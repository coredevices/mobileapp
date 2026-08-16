package coredevices.firestore

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import coredevices.analytics.AnalyticsBackend
import coredevices.util.AppResumed
import coredevices.util.auth.NoOpSilentSignIn
import coredevices.util.auth.SilentSignIn
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

interface UsersDao {
    val user: Flow<PebbleUser?>
    val loginEvents: Flow<PebbleUser>
    suspend fun updateTodoBlockId(
        todoBlockId: String
    )
    suspend fun updateNotionPageId(pageId: String) {}
    suspend fun initUserDevToken(rebbleUserToken: String?)
    suspend fun updateLastConnectedWatch(serial: String)
    suspend fun updateRingLifetimeCollectionCount(serial: String, count: Int)
    suspend fun updateEncryptionInfo(info: EncryptionInfo) {}
    fun init()
}

data class PebbleUser(
    val isAnonymousUser: Boolean,
    val user: User,
)

class UsersDaoImpl(
    dbProvider: () -> FirebaseFirestore,
    private val settings: Settings,
    private val silentSignIn: SilentSignIn = NoOpSilentSignIn,
    private val appResumed: AppResumed? = null,
    private val analytics: AnalyticsBackend? = null,
): CollectionDao("users", dbProvider), UsersDao {
    private val userDoc get() = authenticatedId?.let { db.document(it) }
    private val logger = Logger.withTag("UsersDaoImpl")

    private val _user = MutableSharedFlow<PebbleUser?>(replay = 1)
    override val user: Flow<PebbleUser?> = _user.asSharedFlow()

    // replay=1 so a subscriber that subscribes after the login event fires (e.g. one gated
    // behind libPebble.init() / appstore source init) still receives it.
    private val _loginEvents = MutableSharedFlow<PebbleUser>(replay = 1)
    override val loginEvents: Flow<PebbleUser> = _loginEvents.asSharedFlow()

    // Set when we observe a non-anonymous user with hadNonAnonymousAccount=false
    // (i.e. an active manual login, not a Firebase auth-state restore on startup).
    // Consumed once the corresponding PebbleUser has been emitted to _user.
    private var pendingLoginEmission = false

    private var hadNonAnonymousAccount: Boolean
        get() = settings.getBoolean(KEY_HAD_NON_ANONYMOUS_ACCOUNT, false)
        set(value) { settings[KEY_HAD_NON_ANONYMOUS_ACCOUNT] = value }

    private var hadAnonymousAccount: Boolean
        get() = settings.getBoolean(KEY_HAD_ANONYMOUS_ACCOUNT, false)
        set(value) { settings[KEY_HAD_ANONYMOUS_ACCOUNT] = value }

    // Comma-joined provider ids (e.g. "google.com,password") of the last signed-in user, so
    // the restoration path knows whether silent Google re-auth is applicable.
    private var lastSignInProviders: String
        get() = settings.getString(KEY_LAST_SIGN_IN_PROVIDERS, "")
        set(value) { settings[KEY_LAST_SIGN_IN_PROVIDERS] = value }

    // True only during initial startup, before we've seen the first non-null user.
    // Prevents the long delay from applying on explicit sign-out.
    private var isInitialStartup = true

    override fun init() {
        GlobalScope.launch {
            Firebase.auth.idTokenChanged
                .onStart { emit(Firebase.auth.currentUser) }
                // Emissions can wrap the same mutated native user; snapshot the key per emission.
                .distinctUntilChangedBy { it?.uid to it?.isAnonymous }
                .flatMapLatest { firebaseUser ->
                    val userInfo = firebaseUser?.let { "uid=${it.uid.take(8)} isAnonymous=${it.isAnonymous}" } ?: "null"
                    logger.v { "User changed: $userInfo" }
                    if (firebaseUser == null) {
                        if (isInitialStartup) {
                            if (hadNonAnonymousAccount || hadAnonymousAccount) {
                                // Previously had an account (anon or real) — don't create a new
                                // anonymous user, that would orphan the previous UID's Firestore
                                // data. Wait for Firebase to restore auth state.
                                logger.i { "User is null, prior account exists (anon=$hadAnonymousAccount, nonAnon=$hadNonAnonymousAccount), waiting for restoration" }
                                analytics?.logEvent(
                                    "auth_loss_detected",
                                    mapOf("anon" to hadAnonymousAccount, "non_anon" to hadNonAnonymousAccount)
                                )
                                _user.emit(null)
                                coroutineScope {
                                    val silentReauthMutex = Mutex()
                                    var silentAttempts = 0
                                    suspend fun trySilentReauth(trigger: String) {
                                        if (!silentReauthMutex.tryLock()) return
                                        try {
                                            logger.i { "Attempting silent re-auth (trigger=$trigger)" }
                                            val success = try {
                                                silentSignIn.attempt()
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                logger.w(e) { "Silent re-auth threw" }
                                                false
                                            }
                                            analytics?.logEvent(
                                                "auth_silent_reauth",
                                                mapOf("success" to success, "trigger" to trigger)
                                            )
                                            // On success idTokenChanged fires upstream and cancels this wait.
                                            logger.i { "Silent re-auth ${if (success) "succeeded" else "failed"} (trigger=$trigger)" }
                                        } finally {
                                            silentReauthMutex.unlock()
                                        }
                                    }
                                    val resumeJob = appResumed?.let { resumed ->
                                        launch {
                                            resumed.appResumed.collect {
                                                if (canSilentReauth(lastSignInProviders)) trySilentReauth("app_resumed")
                                            }
                                        }
                                    }
                                    var attempt = 0
                                    var retryDelay = AUTH_RESTORE_INITIAL_RETRY_INTERVAL
                                    while (true) {
                                        delay(retryDelay)
                                        retryDelay = (retryDelay * 2).coerceAtMost(AUTH_RESTORE_MAX_RETRY_INTERVAL)
                                        attempt++
                                        // We rely on idTokenChanged (upstream of this flatMapLatest) firing
                                        // with the restored user to break out of this wait. Occasionally the
                                        // SDK repopulates currentUser without emitting a token event (seen after
                                        // aggressive OS process kills), which would leave us waiting forever.
                                        // If we can see a currentUser here, actively force a token refresh so
                                        // idTokenChanged fires and flatMapLatest cancels this wait and processes
                                        // the real user.
                                        val restored = Firebase.auth.currentUser
                                        if (restored != null) {
                                            logger.i { "currentUser present during auth wait (uid=${restored.uid.take(8)}), forcing token refresh to resume" }
                                            try {
                                                withContext(NonCancellable) { restored.getIdToken(true) }
                                            } catch (e: Exception) {
                                                logger.w(e) { "Forced token refresh failed during auth restoration wait" }
                                            }
                                        } else {
                                            logger.w { "Still waiting for auth restoration, attempt=$attempt (anon=$hadAnonymousAccount, nonAnon=$hadNonAnonymousAccount)" }
                                            if (shouldAttemptSilentReauth(attempt, silentAttempts, lastSignInProviders)) {
                                                silentAttempts++
                                                trySilentReauth("startup_wait")
                                            }
                                            if (shouldGiveUpWaitingForAuth(attempt, silentAttempts, lastSignInProviders)) {
                                                resumeJob?.cancel()
                                                break
                                            }
                                        }
                                    }
                                }
                                // Firebase doesn't repopulate currentUser later in the process, so a
                                // session that hasn't arrived by now never will (e.g. auth prefs
                                // restored from another device, whose keystore keys didn't come with
                                // them). Concede the prior account so we don't wait again next launch.
                                logger.w { "Gave up waiting for auth restoration, signing in anonymously" }
                                analytics?.logEvent(
                                    "auth_restore_gave_up",
                                    mapOf("anon" to hadAnonymousAccount, "non_anon" to hadNonAnonymousAccount)
                                )
                                hadAnonymousAccount = false
                                hadNonAnonymousAccount = false
                            } else {
                                logger.i { "User is null, no prior account, delay=2s before anonymous sign-in" }
                                delay(2.seconds)
                                logger.w { "Delay expired without user arriving, falling back to anonymous sign-in" }
                            }
                        } else {
                            if (hadNonAnonymousAccount) {
                                logger.i { "User became null post-startup, hadNonAnonymousAccount: true→false" }
                            }
                            hadNonAnonymousAccount = false
                        }
                        _user.emit(null)
                        logger.i { "Logging into firebase anonymously" }
                        try {
                            withContext(NonCancellable) {
                                Firebase.auth.signInAnonymously()
                            }
                        } catch (e: Exception) {
                            logger.e(e) { "Failed to sign in anonymously" }
                        }
                        flowOf(null)
                    } else {
                        isInitialStartup = false
                        if (firebaseUser.isAnonymous) {
                            if (!hadAnonymousAccount) {
                                logger.i { "Anonymous user observed, setting hadAnonymousAccount=true" }
                                hadAnonymousAccount = true
                            }
                        } else {
                            if (!hadNonAnonymousAccount) {
                                logger.i { "Active login detected (hadNonAnonymousAccount false→true)" }
                                pendingLoginEmission = true
                            }
                            logger.i { "Non-anonymous user restored/signed in, setting hadNonAnonymousAccount=true" }
                            hadNonAnonymousAccount = true
                            lastSignInProviders = firebaseUser.providerData.joinToString(",") { it.providerId }
                        }
                        val docRef = db.document("users/${firebaseUser.uid}")
                        docRef.snapshots
                            .onEach { snapshot ->
                                try {
                                    if (!snapshot.exists) {
                                        docRef.set(User(pebbleUserToken = generateRandomUserToken()))
                                    } else if (snapshot.data<User?>()?.pebbleUserToken == null) {
                                        docRef.update(mapOf("pebble_user_token" to generateRandomUserToken()))
                                    }
                                } catch (e: Exception) {
                                    logger.w(e) { "Error initializing user document" }
                                }
                            }
                            .filter { it.exists }
                            .map { snapshot ->
                                // COMBINE BOTH SOURCES HERE:
                                // firebaseUser provides 'isAnonymous', snapshot provides the Firestore data
                                val userData = snapshot.data<User>()
                                PebbleUser(
                                    isAnonymousUser = firebaseUser.isAnonymous,
                                    user = userData
                                )
                            }
                            .catch { e -> logger.w(e) { "Error observing user doc" } }
                    }
                }
                .collect { user ->
                    logger.d { "User changed.." }
                    _user.emit(user)
                    if (pendingLoginEmission && user != null && !user.isAnonymousUser) {
                        pendingLoginEmission = false
                        logger.i { "Emitting loginEvents for active login" }
                        _loginEvents.emit(user)
                    }
                }
        }
    }

    override suspend fun updateTodoBlockId(
        todoBlockId: String
    ) {
        userDoc?.update(mapOf("todo_block_id" to todoBlockId))
    }

    override suspend fun updateNotionPageId(pageId: String) {
        // Reset the cached Todo block so a new one is created in the chosen page.
        userDoc?.update(mapOf("notion_page_id" to pageId, "todo_block_id" to null))
    }

    override suspend fun initUserDevToken(rebbleUserToken: String?) {
        if (rebbleUserToken == null) return
        val user = user.first()
        if (user == null) {
            logger.w { "initUserDevToken: user is null" }
            return
        }
        if (user.user.rebbleUserToken != rebbleUserToken) {
            userDoc?.update(mapOf("rebble_user_token" to rebbleUserToken))
        }
    }

    override suspend fun updateLastConnectedWatch(serial: String) {
        val user = user.first()
        if (user == null) {
            logger.w { "updateLastConnectedWatch: user is null" }
            return
        }
        if (user.user.lastConnectedWatch != serial) {
            userDoc?.update(mapOf("last_connected_watch" to serial))
        }
    }

    override suspend fun updateRingLifetimeCollectionCount(serial: String, count: Int) {
        val user = user.first()
        if (user == null) {
            logger.w { "updateRingLifetimeCollectionCount: user is null" }
            return
        }
        val existing = user.user.ringLifetimeCollectionCounts.orEmpty()
        if ((existing[serial] ?: -1) >= count) return
        val merged = existing + (serial to count)
        userDoc?.update(mapOf("ring_lifetime_collection_counts" to merged))
    }

    override suspend fun updateEncryptionInfo(info: EncryptionInfo) {
        val doc = userDoc ?: throw IllegalStateException("Not signed in — cannot store encryption info")
        doc.update("encryption" to info)
    }
}

private const val KEY_HAD_NON_ANONYMOUS_ACCOUNT = "had_non_anonymous_account"
private const val KEY_HAD_ANONYMOUS_ACCOUNT = "had_anonymous_account"
internal const val KEY_LAST_SIGN_IN_PROVIDERS = "last_sign_in_providers"

// Poll quickly at first so a silently-restored session is picked up within seconds
// (the user is looking at a sign-in screen while we wait), backing off to a steady
// 1-minute cadence to avoid needless wakeups/token refreshes during a long stall.
private val AUTH_RESTORE_INITIAL_RETRY_INTERVAL = 2.seconds
private val AUTH_RESTORE_MAX_RETRY_INTERVAL = 1.minutes

internal const val GOOGLE_PROVIDER_ID = "google.com"
// Give Firebase's own restoration ~6s (attempts 1-2) before trying silent re-auth.
internal const val SILENT_REAUTH_MIN_WAIT_ATTEMPT = 3
internal const val SILENT_REAUTH_MAX_ATTEMPTS = 5
// ~6 minutes of waiting given the 2s-doubling-to-1m backoff.
internal const val AUTH_RESTORE_MAX_ATTEMPTS = 10

// Silent re-auth only works for Google accounts (Credential Manager can return a stored
// Google credential without UI; Apple/GitHub have no equivalent).
internal fun canSilentReauth(providers: String) = providers.contains(GOOGLE_PROVIDER_ID)

internal fun shouldAttemptSilentReauth(attempt: Int, silentAttempts: Int, providers: String): Boolean =
    attempt >= SILENT_REAUTH_MIN_WAIT_ATTEMPT &&
        silentAttempts < SILENT_REAUTH_MAX_ATTEMPTS &&
        canSilentReauth(providers)

internal fun shouldGiveUpWaitingForAuth(attempt: Int, silentAttempts: Int, providers: String): Boolean =
    attempt >= AUTH_RESTORE_MAX_ATTEMPTS &&
        !shouldAttemptSilentReauth(attempt, silentAttempts, providers)

fun generateRandomUserToken(): String {
    val charPool = "0123456789abcdef"
    return (1..24)
        .map { kotlin.random.Random.nextInt(0, charPool.length) }
        .map(charPool::get)
        .joinToString("")
}
