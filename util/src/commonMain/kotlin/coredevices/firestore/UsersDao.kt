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

interface UsersDao {
    val user: Flow<PebbleUser?>
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

    private var hadNonAnonymousAccount: Boolean
        get() = settings.getBoolean(KEY_HAD_NON_ANONYMOUS_ACCOUNT, false)
        set(value) { settings[KEY_HAD_NON_ANONYMOUS_ACCOUNT] = value }

    // True only during initial startup, before we've seen the first non-null user.
    // Prevents the long delay from applying on explicit sign-out.
    private var isInitialStartup = true

    override fun init() {
        GlobalScope.launch {
            Firebase.auth.authStateChanged
                .onStart { emit(Firebase.auth.currentUser) }
                .distinctUntilChanged { old, new -> old?.uid == new?.uid }
                .flatMapLatest { firebaseUser ->
                    logger.v { "User changed: $firebaseUser" }
                    if (firebaseUser == null) {
                        if (isInitialStartup) {
                            val delayDuration = if (hadNonAnonymousAccount) {
                                10.seconds // Process restart, had real account
                            } else {
                                2.seconds // Process restart, fresh install
                            }
                            logger.i { "User is null, hadNonAnonymousAccount=$hadNonAnonymousAccount, isInitialStartup=$isInitialStartup, delay=$delayDuration before anonymous sign-in" }
                            // Firebase may emit null briefly on process start before it finishes
                            // loading persisted auth state from disk. Delay here so flatMapLatest
                            // can cancel us if the real user arrives, avoiding a spurious signInAnonymously()
                            // that would clobber a real account with a fresh anonymous one.
                            // Use a longer delay if user previously had a real account, as Firebase
                            // auth restoration can be slow after process kills.
                            delay(delayDuration)
                            logger.w { "Delay expired without real user arriving (hadNonAnonymousAccount=$hadNonAnonymousAccount), falling back to anonymous sign-in" }
                        }
                        hadNonAnonymousAccount = false
                        _user.emit(null)
                        logger.i { "Logging into firebase anonymously" }
                        try {
                            // Signing in will trigger a new emission of this flow - don't let it
                            // cancel the sign-in...
                            withContext(NonCancellable) {
                                Firebase.auth.signInAnonymously()
                            }
                        } catch (e: Exception) {
                            logger.e(e) { "Failed to sign in anonymously" }
                        }
                        flowOf(null)
                    } else {
                        isInitialStartup = false
                        if (!firebaseUser.isAnonymous) {
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
}

private const val KEY_HAD_NON_ANONYMOUS_ACCOUNT = "had_non_anonymous_account"

fun generateRandomUserToken(): String {
    val charPool = "0123456789abcdef"
    return (1..24)
        .map { kotlin.random.Random.nextInt(0, charPool.length) }
        .map(charPool::get)
        .joinToString("")
}
