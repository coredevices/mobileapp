package coredevices

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import CoreNav
import CoreRoute
import DocumentAttachment
import coredevices.pebble.ui.TopBarParams
import coredevices.util.Permission
import kotlinx.io.files.Path

class ExperimentalDevices {
    fun init() {
    }

    fun requiredRuntimePermissions(): Set<Permission> {
        return emptySet()
    }

    fun addExperimentalRoutes(builder: NavGraphBuilder, coreNav: CoreNav) {
    }

    fun home(): CoreRoute? = null

    suspend fun exportOutput(id: String): DocumentAttachment? {
        return null
    }

    fun debugSummary(): String? {
        return null
    }

    @Composable
    fun IndexScreen(coreNav: CoreNav, topBarParams: TopBarParams) {

    }

    fun badCollectionsDir(): Path? = null
}