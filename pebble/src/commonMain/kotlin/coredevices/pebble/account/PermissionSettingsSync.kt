package coredevices.pebble.account

import co.touchlab.kermit.Logger
import coredevices.firestore.User
import coredevices.firestore.UsersDao
import io.rebble.libpebblecommon.WatchConfig
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.permissions.PermissionPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** When apps have to ask, kept the same for the whole account: the web store asks what the phone would. */
class PermissionSettingsSync(
    private val usersDao: UsersDao,
    private val libPebble: LibPebble,
) {
    private val logger = Logger.withTag("PermissionSettingsSync")

    fun init() {
        GlobalScope.launch(Dispatchers.Default) {
            var synced: PermissionSettings? = null
            val local = libPebble.config.map { PermissionSettings.of(it.watchConfig) }.distinctUntilChanged()
            combine(usersDao.user, local) { user, settings -> user to settings }.collect { (user, settings) ->
                if (user == null) return@collect
                when (val step = syncStep(PermissionSettings.of(user.user), settings, synced)) {
                    is SyncStep.Adopt -> {
                        synced = step.settings
                        if (step.settings != settings) {
                            logger.d { "Adopting ${step.settings}" }
                            libPebble.updateConfig(libPebble.config.value.let { config ->
                                config.copy(watchConfig = step.settings.applyTo(config.watchConfig))
                            })
                        }
                    }
                    is SyncStep.Upload -> try {
                        usersDao.updatePermissionSettings(step.settings.policy.name, step.settings.askLegacyApps)
                        synced = step.settings
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Left unsynced, so the next change to either side tries again.
                        logger.w(e) { "Couldn't save permission settings" }
                    }
                    SyncStep.Clear -> try {
                        usersDao.clearPermissionSettings()
                        synced = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.w(e) { "Couldn't clear permission settings" }
                    }
                    null -> {}
                }
            }
        }
    }
}

internal sealed interface SyncStep {
    data class Adopt(val settings: PermissionSettings) : SyncStep
    data class Upload(val settings: PermissionSettings) : SyncStep
    /** Plugins are off here, so the web mustn't ask on this phone's behalf. */
    data object Clear : SyncStep
}

/** A remote value that moved since the last sync wins (another device, or a new sign-in); otherwise a local change goes up. */
internal fun syncStep(remote: PermissionSettings?, local: PermissionSettings?, synced: PermissionSettings?): SyncStep? = when {
    local == null -> if (remote != null) SyncStep.Clear else null
    remote != null && remote != synced -> SyncStep.Adopt(remote)
    local != synced -> SyncStep.Upload(local)
    else -> null
}

internal data class PermissionSettings(val policy: PermissionPolicy, val askLegacyApps: Boolean) {
    fun applyTo(config: WatchConfig) = config.copy(permissionPolicy = policy, askLegacyAppsForPermissions = askLegacyApps)

    companion object {
        /** Null while plugins are off: the settings don't apply. */
        fun of(config: WatchConfig) =
            if (config.enablePlugins) PermissionSettings(config.permissionPolicy, config.askLegacyAppsForPermissions) else null

        fun of(user: User): PermissionSettings? {
            val policy = PermissionPolicy.entries.firstOrNull { it.name == user.permissionPolicy } ?: return null
            return PermissionSettings(policy, user.askLegacyAppsForPermissions ?: false)
        }
    }
}
