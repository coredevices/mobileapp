package io.rebble.libpebblecommon.metadata.pbw.appinfo

import kotlinx.serialization.Serializable

@Serializable
data class CompanionApp(val android: AndroidCompanionAppRoot? = null)

fun PbwAppInfo.pebbleKit2Packages(): List<String> = companionApp?.android?.apps.orEmpty().mapNotNull { it.pkg }

fun PbwAppInfo.hasPebbleKit2Companion(): Boolean = pebbleKit2Packages().isNotEmpty()
