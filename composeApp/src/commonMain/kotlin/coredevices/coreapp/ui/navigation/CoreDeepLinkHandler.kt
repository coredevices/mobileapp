package coredevices.coreapp.ui.navigation

import CommonRoutes
import CoreRoute
import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Note - I tried getting native compose navigation deep links to work, but they just wouldn't, so
 * doing it ourselves.
 */
class CoreDeepLinkHandler {
    private val _navigateToDeepLink = MutableSharedFlow<CoreRoute>(extraBufferCapacity = 1, replay = 1)
    val navigateToDeepLink = _navigateToDeepLink.asSharedFlow()

    fun handle(uri: Uri) {
        val route = uri.decodeDeepLink()
        logger.d { "handle: uri = $uri route = $route" }
        if (route == null) return
        _navigateToDeepLink.tryEmit(route)
    }

    companion object {
        private val logger = Logger.withTag("CoreDeepLinkHandler")
        private const val SCHEME = "pebblecore"
        private const val HOST = "deep-link"
        private const val VIEW_BUG_REPORT_PATH = "view-bug-report"
        private const val CONVERSATION_ID_QUERY_PARAM = "conversationId"

        fun CommonRoutes.ViewBugReportRoute.asUri(): Uri = Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendPath(VIEW_BUG_REPORT_PATH)
            .appendQueryParameter(CONVERSATION_ID_QUERY_PARAM, conversationId)
            .build()

        fun Uri.decodeDeepLink(): CoreRoute? {
            if (scheme != SCHEME) return null
            if (host != HOST) return null
            val pathSegment = pathSegments.firstOrNull() ?: return null
            return when (pathSegment) {
                VIEW_BUG_REPORT_PATH -> {
                    val conversationId = getQueryParameter(CONVERSATION_ID_QUERY_PARAM)
                    if (conversationId == null) return null
                    CommonRoutes.ViewBugReportRoute(conversationId)
                }
                else -> null
            }
        }
    }
}