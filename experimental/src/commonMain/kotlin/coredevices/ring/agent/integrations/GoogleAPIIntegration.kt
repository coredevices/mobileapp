package coredevices.ring.agent.integrations

import PlatformUiContext
import coredevices.util.integrations.Integration
import co.touchlab.kermit.Logger
import coredevices.util.auth.GoogleAuthUtil
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * An abstract class representing a Google API integration, implementing the scoped sign in flow.
 * This class can be extended to implement specific Google API integrations, such as Google Tasks or Google Keep.
 */
abstract class GoogleAPIIntegration(
    private val scopes: List<String>
) : Integration, KoinComponent {
    private val googleAuthUtil: GoogleAuthUtil by inject<GoogleAuthUtil>()
    override suspend fun signIn(uiContext: PlatformUiContext): Boolean {
        return googleAuthUtil.authorizeScopes(uiContext, scopes) != null
    }

    override suspend fun unlink() {
        //TODO: Revoke token? might be in use
    }

    protected suspend fun tokenForScopes(): String? = googleAuthUtil.getAccessToken(scopes)

    suspend fun isAuthorized(): Boolean {
        val r = tokenForScopes() != null
        Logger.d { "GoogleAPIIntegration.isAuthorized: $r" }
        return r
    }
}