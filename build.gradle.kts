plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.nativeCocoaPods) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

project.gradle.taskGraph.whenReady {
    allTasks.filter { it::class.simpleName?.contains("EmbedAndSign") == true }.forEach {
        logger.warn("Disabling embedding and signing task in project ${it.project.name}")
        it.enabled = false
    }
}