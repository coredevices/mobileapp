package coredevices.firestore

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
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
    suspend fun updateNotionToken(
        notionToken: String?
    )
    suspend fun updateMcpRunToken(
        mcpRunToken: String?
    )
    suspend fun updateTodoBlockId(
        todoBlockId: String
    )
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

class UsersDaoImpl(dbProvider: () -> FirebaseFirestore, private val settings: Settings): CollectionDao("users", dbProvider), UsersDao {
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

    // True only during initial startup, before we've seen the first non-null user.
    // Prevents the long delay from applying on explicit sign-out.
    private var isInitialStartup = true

    override fun init() {
        GlobalScope.launch {
            Firebase.auth.idTokenChanged
                .onStart { emit(Firebase.auth.currentUser) }
                .distinctUntilChanged { old, new ->
                    old?.uid == new?.uid && old?.isAnonymous == new?.isAnonymous
                }
                .flatMapLatest { firebaseUser ->
                    logger.v { "User changed: $firebaseUser" }
                    if (firebaseUser == null) {
                        if (isInitialStartup) {
                            if (hadNonAnonymousAccount || hadAnonymousAccount) {
                                // Previously had an account (anon or real) — don't create a new
                                // anonymous user, that would orphan the previous UID's Firestore
                                // data. Wait for Firebase to restore auth state.
                                logger.i { "User is null, prior account exists (anon=$hadAnonymousAccount, nonAnon=$hadNonAnonymousAccount), waiting for restoration" }
                                _user.emit(null)
                                while (true) {
                                    delay(1.minutes)
                                    logger.w { "Still waiting for auth restoration (anon=$hadAnonymousAccount, nonAnon=$hadNonAnonymousAccount)" }
                                }
                            }
                            logger.i { "User is null, no prior account, delay=2s before anonymous sign-in" }
                            delay(2.seconds)
                            logger.w { "Delay expired without user arriving, falling back to anonymous sign-in" }
                        } else {
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

    override suspend fun updateNotionToken(
        notionToken: String?
    ) {
        userDoc?.update(mapOf("notion_token" to notionToken))
    }

    override suspend fun updateMcpRunToken(
        mcpRunToken: String?
    ) {
        userDoc?.update(mapOf("mcp_run_token" to mcpRunToken))
    }

    override suspend fun updateTodoBlockId(
        todoBlockId: String
    ) {
        userDoc?.update(mapOf("todo_block_id" to todoBlockId))
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

fun generateRandomUserToken(): String {
    val charPool = "0123456789abcdef"
    return (1..24)
        .map { kotlin.random.Random.nextInt(0, charPool.length) }
        .map(charPool::get)
        .joinToString("")
}
