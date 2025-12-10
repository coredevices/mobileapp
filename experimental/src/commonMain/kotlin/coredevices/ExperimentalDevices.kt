package coredevices

import androidx.navigation.NavGraphBuilder
import CoreNav
import CoreRoute
import DocumentAttachment
import coredevices.util.Permission

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
}