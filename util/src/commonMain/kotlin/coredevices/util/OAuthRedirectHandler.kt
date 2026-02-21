package coredevices.util

import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class OAuthRedirectHandler {
    companion object {
        private val logger = Logger.withTag("OAuthRedirectHandler")
    }
    private val _oauthRedirects = MutableSharedFlow<Uri>(extraBufferCapacity = 1, replay = 1)
    val oauthRedirects = _oauthRedirects.asSharedFlow()

    fun handleOAuthRedirect(uri: Uri?): Boolean {
        if (uri != null && uri.host == "oauth") {
            logger.i { "Handling oauth redirect" }
            _oauthRedirects.tryEmit(uri)
            return true
        } else {
            return false
        }
    }
}