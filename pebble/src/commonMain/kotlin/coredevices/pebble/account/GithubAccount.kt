package coredevices.pebble.account

import com.russhwolf.settings.Settings
import coredevices.pebble.services.Github
import coredevices.pebble.services.OAuthService
import coredevices.pebble.services.OAuthToken
import dev.gitlive.firebase.auth.OAuthCredential
import io.rebble.libpebblecommon.connection.TokenProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

interface GithubAccount {
    val loggedIn: StateFlow<OAuthToken?>

    fun setToken(token: OAuthToken?)
}

class RealGithubAccount (
    private val settings: Settings
) : GithubAccount {
    companion object {
        private const val TOKEN_KEY = "github_token"
        private val json = Json {
            encodeDefaults = false
        }
    }
    private val _loggedIn = MutableStateFlow(getToken())
    override val loggedIn = _loggedIn.asStateFlow()

    override fun setToken(token: OAuthToken?) {
        if (token != null) {
            settings.putString(TOKEN_KEY, json.encodeToString(token))
        } else {
            settings.remove(TOKEN_KEY)
        }
        _loggedIn.value = token
    }

    private fun getToken(): OAuthToken? {
        val tokenString = settings.getStringOrNull(TOKEN_KEY) ?: return null
        return json.decodeFromString<OAuthToken>(tokenString)
    }

}