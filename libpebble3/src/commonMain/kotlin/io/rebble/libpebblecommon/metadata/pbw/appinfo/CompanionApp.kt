package io.rebble.libpebblecommon.metadata.pbw.appinfo

import kotlinx.serialization.Serializable

@Serializable
public data class CompanionApp(val android: AndroidCompanionAppRoot? = null)

internal fun PbwAppInfo.pebbleKit2Packages(): List<String> = companionApp?.android?.apps.orEmpty().mapNotNull { it.pkg }

internal fun PbwAppInfo.hasPebbleKit2Companion(): Boolean = pebbleKit2Packages().isNotEmpty()
