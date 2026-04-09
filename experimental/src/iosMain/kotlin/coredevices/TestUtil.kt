package coredevices

import cocoapods.FirebaseCore.FIRConfiguration
import cocoapods.FirebaseCore.FIRLoggerLevelDebug
import coredevices.firestore.PebbleUser
import coredevices.firestore.User
import coredevices.firestore.UsersDao
import coredevices.mcp.client.McpSession
import coredevices.ring.agent.BuiltinServletRepository
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.data.NoteShortcutType
import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.Preferences
import coredevices.ring.database.SecondaryMode
import coredevices.ring.firestoreModule
import coredevices.ring.mcpModule
import coredevices.util.Platform
import coredevices.util.models.CactusSTTMode
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.initialize
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import platform.posix.getenv

object TestUtil {
    private val loginDefer = CompletableDeferred<Unit>()
    private suspend fun initAuth() {
        FIRConfiguration.sharedInstance.setLoggerLevel(FIRLoggerLevelDebug)
        val token = getenv("TESTS_FB_CUSTOM_TOKEN")?.toKString() ?: error("TESTS_FB_CUSTOM_TOKEN env var not set")
        try {
            Firebase.auth.signInWithCustomToken(token).user ?: error("Couldn't sign in")
            loginDefer.complete(Unit)
        } catch (e: Exception) {
            loginDefer.completeExceptionally(e)
        }
    }

    @Throws(IllegalStateException::class, FirebaseAuthException::class)
    suspend fun waitForAuth() {
        loginDefer.await()
    }


    suspend fun initTestApp() {
        val koin = startKoin {
            allowOverride(true)
            modules(
                module {
                    factory {
                        HttpClient().engine
                    } bind HttpClientEngine::class
                },
                firestoreModule,
                experimentalModule,
                mcpModule,
                module {
                    single { PreferencesTestImpl } bind Preferences::class
                    single {
                        object : Platform {
                            override val name: String
                                get() = "iOS"

                            override suspend fun openUrl(url: String) {

                            }
                        }
                    } bind Platform::class
                    single { UsersDaoTestImpl } bind UsersDao::class
                }
            )
        }
        Firebase.initialize(null)
        initAuth()
    }

    fun getTestableMcpSession(): McpSession {
        val koin = KoinPlatform.getKoin()
        val builtinMcpRepository: BuiltinServletRepository = koin.get()
        val scope = CoroutineScope(Dispatchers.Default)
        val mcpIntegrations = builtinMcpRepository.getAllServlets().map {
            builtinMcpRepository.resolveName(it.name)!!
        }
        return McpSession(
            integrations = mcpIntegrations,
            scope = scope
        )
    }
}

private object PreferencesTestImpl: Preferences {
    override val useCactusAgent: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    override val useCactusTranscription: StateFlow<Boolean>
        get() = TODO("Not yet implemented")
    override val cactusMode: CactusSTTMode
        get() = TODO("Not yet implemented")
    override val ringPaired: StateFlow<String?>
        get() = TODO("Not yet implemented")
    override val ringPairedOld: StateFlow<Boolean>
        get() = TODO("Not yet implemented")
    override val musicControlMode: StateFlow<MusicControlMode>
        get() = TODO("Not yet implemented")
    override val lastSyncIndex: StateFlow<Int?>
        get() = TODO("Not yet implemented")
    override val debugDetailsEnabled: StateFlow<Boolean>
        get() = TODO("Not yet implemented")
    override val approvedBeeperContacts: StateFlow<List<String>>
        get() = TODO("Not yet implemented")
    override val secondaryMode: StateFlow<SecondaryMode>
        get() = TODO("Not yet implemented")
    override val reminderProvider: StateFlow<ReminderProvider>
        get() = TODO("Not yet implemented")
    override val noteProvider: StateFlow<NoteProvider>
        get() = TODO("Not yet implemented")
    override val noteShortcut: StateFlow<NoteShortcutType>
        get() = TODO("Not yet implemented")

    override suspend fun setUseCactusAgent(useCactus: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun setUseCactusTranscription(useCactus: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setCactusMode(mode: CactusSTTMode) {
        TODO("Not yet implemented")
    }

    override fun setRingPaired(id: String?) {
        TODO("Not yet implemented")
    }

    override fun setMusicControlMode(mode: MusicControlMode) {
        TODO("Not yet implemented")
    }

    override suspend fun setLastSyncIndex(index: Int?) {
        TODO("Not yet implemented")
    }

    override fun setDebugDetailsEnabled(enabled: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun setApprovedBeeperContacts(contacts: List<String>?) {
        TODO("Not yet implemented")
    }

    override fun setSecondaryMode(mode: SecondaryMode) {
        TODO("Not yet implemented")
    }

    override fun setReminderProvider(provider: ReminderProvider) {
        TODO("Not yet implemented")
    }

    override fun setNoteProvider(provider: NoteProvider) {
        TODO("Not yet implemented")
    }

    override fun setNoteShortcut(shortcut: NoteShortcutType) {
        TODO("Not yet implemented")
    }

}

private object UsersDaoTestImpl: UsersDao {
    override val user: Flow<PebbleUser?> = MutableStateFlow(PebbleUser(false, User()))

    override suspend fun updateNotionToken(notionToken: String?) {
    }

    override suspend fun updateMcpRunToken(mcpRunToken: String?) {
    }

    override suspend fun updateTodoBlockId(todoBlockId: String) {
    }

    override suspend fun initUserDevToken(rebbleUserToken: String?) {
    }

    override suspend fun updateLastConnectedWatch(serial: String) {
    }

    override fun init() {
        TODO("Not yet implemented")
    }
}