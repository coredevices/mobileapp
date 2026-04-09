package coredevices.util.integrations

import PlatformUiContext

interface Integration {
    suspend fun signIn(uiContext: PlatformUiContext): Boolean
    suspend fun unlink()
}

class IntegrationAuthException(message: String) : Exception(message)
