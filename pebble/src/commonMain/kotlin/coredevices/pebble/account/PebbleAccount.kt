package coredevices.pebble.account

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.pebble.services.RealPebbleWebServices
import io.rebble.libpebblecommon.connection.TokenProvider
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

interface PebbleAccount {
    val loggedIn: StateFlow<String?>
    val devToken: StateFlow<String?>

    suspend fun setToken(token: String, bootUrl: String)
    suspend fun setDevPortalId()
}

class RealPebbleAccount(
    private val settings: Settings,
    private val pebbleWebServices: RealPebbleWebServices,
    private val bootConfigProvider: BootConfigProvider,
) : PebbleAccount {
    private val logger = Logger.withTag("PebbleAccount")
    private val _loggedIn = MutableStateFlow(getToken())
    override val loggedIn = _loggedIn.asStateFlow()
    private val _devToken = MutableStateFlow(getDevPortalId())
    override val devToken = _devToken.asStateFlow()

    override suspend fun setToken(token: String, bootUrl: String) {
        logger.d("setToken")
        settings.putString(TOKEN_KEY, token)
        _loggedIn.value = token

        bootConfigProvider.setUrl(bootUrl)
        setDevPortalId()
    }

    override suspend fun setDevPortalId() {
        val devPortalId = pebbleWebServices.fetchUsersMe()?.users?.firstOrNull()?.id
        if (devPortalId == null) {
            logger.e { "couldn't fetch dev portal id" }
            return
        }
        settings.putString(DEV_KEY, devPortalId)
        _devToken.value = devPortalId
    }

    private fun getToken(): String? = settings.getStringOrNull(TOKEN_KEY)
    private fun getDevPortalId(): String? = settings.getStringOrNull(DEV_KEY)

    companion object {
        private val TOKEN_KEY = "account_token_key"
        private val DEV_KEY = "dev_token_key"
    }
}

class PebbleTokenProvider(
    private val pebbleAccount: PebbleAccount,
) : TokenProvider {
    override suspend fun getDevToken(): String? {
        return pebbleAccount.devToken.value
    }
}

@Serializable
data class UsersMeResponse(
    val users: List<UsersMeUser>
)

@Serializable
data class UsersMeUser(
    val id: String,
)