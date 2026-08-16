package coredevices.coreapp.ui.navigation

import CommonRoutes
import CoreRoute
import androidx.navigation.NavUri
import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import coredevices.ring.ui.navigation.RingRoutes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class CoreDeepLinkHandler {
    private val _navigateToDeepLink = MutableSharedFlow<Any>(extraBufferCapacity = 1, replay = 1)
    val navigateToDeepLink = _navigateToDeepLink.asSharedFlow()

    fun handle(uri: Uri): Boolean {
        logger.d { "handle: uri = $uri" }
        objectRouteFor(uri)?.let { return _navigateToDeepLink.tryEmit(it) }
        recordingRouteFor(uri)?.let { return _navigateToDeepLink.tryEmit(it) }
        return _navigateToDeepLink.tryEmit(NavUri(uri.toString()))
    }

    /** `pebblecore://deep-link/object?id=<firestoreId>` opens the index item
     *  detail. Emitted as a typed route so navigation doesn't depend on a
     *  per-route deep link registration. */
    internal fun objectRouteFor(uri: Uri): RingRoutes.ObjectDetails? {
        if (uri.scheme != SCHEME) return null
        if (uri.host != RingRoutes.OBJECT_DEEP_LINK_HOST) return null
        if (uri.pathSegments.firstOrNull() != RingRoutes.OBJECT_DEEP_LINK_PATH) return null
        val id = uri.getQueryParameter(RingRoutes.OBJECT_DEEP_LINK_ID_PARAM)?.takeIf { it.isNotBlank() }
            ?: return null
        return RingRoutes.ObjectDetails(id)
    }

    /** `pebblecore://deep-link/recording?id=<recordingId>` opens the recording
     *  detail (used by Index notifications). */
    internal fun recordingRouteFor(uri: Uri): RingRoutes.RecordingDetails? {
        if (uri.scheme != SCHEME) return null
        if (uri.host != RingRoutes.OBJECT_DEEP_LINK_HOST) return null
        if (uri.pathSegments.firstOrNull() != RingRoutes.RECORDING_DEEP_LINK_PATH) return null
        val id = uri.getQueryParameter(RingRoutes.OBJECT_DEEP_LINK_ID_PARAM)?.toLongOrNull()
            ?: return null
        return RingRoutes.RecordingDetails(id)
    }

    fun clearPendingDeepLink() {
        _navigateToDeepLink.resetReplayCache()
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
    }
}