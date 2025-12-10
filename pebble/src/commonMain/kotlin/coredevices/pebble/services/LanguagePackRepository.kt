package coredevices.pebble.services

import androidx.compose.ui.text.intl.Locale
import co.touchlab.kermit.Logger
import coredevices.pebble.firmware.isCoreDevice
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class LanguagePackRepository(
    private val json: Json,
) {
    private val logger = Logger.withTag("LanguagePackRepository")

    private val languagePacks by lazy {
        try {
            json.decodeFromString<LanguagePackFile>(LanguagePacksJson).languages
                // For Rebble packs, only include the higest FW version for a given hardware/iso locale
                .groupBy { "${it.isoLocal}/${it.hardware}/${it.file.urlForComparingVersions()}" }
                .values
                .map { it.maxBy { lp -> lp.firmwareVersion } }
        } catch (e: SerializationException) {
            logger.e(e) { "Error decoding language packs!" }
            emptyList()
        }
    }

    suspend fun languagePacksForWatch(watch: ConnectedPebbleDevice): List<LanguagePack> = withContext(Dispatchers.IO) {
        val locale = Locale.current.platformLocale.toString()
        val hardwarePlatform = watch.watchInfo.platform
        languagePacks
            .filter { it.hardware == null || it.hardware == hardwarePlatform.languagePackPlatform().revision }

            .sortedByDescending { it.isoLocal.take(2) == locale.take(2) }
            .sortedByDescending { it.isoLocal == locale }
    }
}

private fun WatchHardwarePlatform.languagePackPlatform(): WatchHardwarePlatform = when {
    isCoreDevice() -> WatchHardwarePlatform.PEBBLE_SILK
    else -> this
}

private fun String.urlForComparingVersions() = when {
    startsWith("https://binaries.rebble.io") -> "https://binaries.rebble.io"
    else -> this
}

@Serializable
data class LanguagePack(
    @SerialName("ISOLocal")
    val isoLocal: String,
    val file: String,
    @SerialName("firmware")
    val firmwareVersion: String,
    val hardware: String?,
    val localName: String,
    val name: String,
    val version: Int,
    val id: String,
)

fun LanguagePack.displayName() = "${localName} (${name}) v$version"

@Serializable
data class LanguagePackFile(
    val languages: List<LanguagePack>
)

private val LanguagePacksJson = """
{
  "languages": [
    {
      "ISOLocal": "en_US",
      "file": "https://binaries.rebble.io/lp/ZJxzFrz-Dn1N058-en_US.pbl",
      "firmware": "3.4.0",
      "hardware": "snowy_s3",
      "id": "55ef3ec040b14b1400f2b2cf",
      "localName": "English",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English",
      "version": 1
    },
    {
      "ISOLocal": "it_IT",
      "file": "https://binaries.rebble.io/lp/OHRO2oz-it_IT.pbl",
      "firmware": "3.8.0",
      "hardware": "snowy_s3",
      "id": "5670a6e04d40a31b00cb2a4f",
      "localName": "Italiano",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Italian",
      "version": 15
    },
    {
      "ISOLocal": "pt_PT",
      "file": "https://binaries.rebble.io/lp/bYqKdPB-pt_PT.pbl",
      "firmware": "3.8.0",
      "hardware": "ev2_4",
      "id": "5670a794b9abe4160010c228",
      "localName": "Português",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Portuguese",
      "version": 13
    },
    {
      "ISOLocal": "pt_PT",
      "file": "https://binaries.rebble.io/lp/iC7D2Pn-pt_PT.pbl",
      "firmware": "3.8.0",
      "hardware": "snowy_s3",
      "id": "5670a82ecedf381b009f3728",
      "localName": "Português",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Portuguese",
      "version": 13
    },
    {
      "ISOLocal": "en_TW",
      "file": "https://binaries.rebble.io/lp/HFz2CaF-en_TW.pbl",
      "firmware": "3.10.0",
      "hardware": "snowy_s3",
      "id": "56df1ea1afea831600b3a5bb",
      "localName": "English + 繁體通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+TraditionalChinese",
      "version": 1
    },
    {
      "ISOLocal": "de_DE",
      "file": "https://binaries.rebble.io/lp/zH1pXTe-de_DE.pbl",
      "firmware": "3.8.0",
      "hardware": "v1_5",
      "id": "5670a4dcb9abe4160010c223",
      "localName": "Deutsch",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "German",
      "version": 28
    },
    {
      "ISOLocal": "pt_PT",
      "file": "https://binaries.rebble.io/lp/qG9UxIF-pt_PT.pbl",
      "firmware": "3.8.0",
      "hardware": "snowy_dvt",
      "id": "5670a816347e8d1600601fc4",
      "localName": "Português",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Portuguese",
      "version": 13
    },
    {
      "ISOLocal": "en_TW",
      "file": "https://binaries.rebble.io/lp/FoSGt2K-en_TW.pbl",
      "firmware": "3.10.0",
      "hardware": "v1_5",
      "id": "56df1ee96aecc31600bcbab5",
      "localName": "English + 繁體通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+TraditionalChinese",
      "version": 1
    },
    {
      "ISOLocal": "it_IT",
      "file": "https://binaries.rebble.io/lp/WE0PE72-it_IT.pbl",
      "firmware": "3.8.0",
      "hardware": "v2_0",
      "id": "5670a673b9abe4160010c227",
      "localName": "Italiano",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Italian",
      "version": 15
    },
    {
      "ISOLocal": "en_CN",
      "file": "https://binaries.rebble.io/lp/O8KxhxC-en_CN.pbl",
      "firmware": "3.10.0",
      "hardware": "ev2_4",
      "id": "56df1d799de9101b0028020c",
      "localName": "English + 简体通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+SimplifiedChinese",
      "version": 2
    },
    {
      "ISOLocal": "en_TW",
      "file": "https://binaries.rebble.io/lp/Zmvb8eZ-en_TW.pbl",
      "firmware": "3.10.0",
      "hardware": "ev2_4",
      "id": "56df1e62279cc21b005eb785",
      "localName": "English + 繁體通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+TraditionalChinese",
      "version": 1
    },
    {
      "ISOLocal": "en_US",
      "file": "https://binaries.rebble.io/lp/p6Ist5P-en_US.pbl",
      "firmware": "3.8.0",
      "hardware": "spalding",
      "id": "5670a8834d40a31b00cb2a52",
      "localName": "English",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English",
      "version": 1
    },
    {
      "ISOLocal": "fr_FR",
      "file": "https://binaries.rebble.io/lp/3O3sv1w-fr_FR.pbl",
      "firmware": "3.8.0",
      "hardware": "spalding",
      "id": "5670a8a1b9abe4160010c229",
      "localName": "Français",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "French",
      "version": 32
    },
    {
      "ISOLocal": "en_CN",
      "file": "https://binaries.rebble.io/lp/9tHqvSx-en_CN.pbl",
      "firmware": "3.10.0",
      "hardware": "v1_5",
      "id": "56df1e1303f9d616009889dc",
      "localName": "English + 简体通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+SimplifiedChinese",
      "version": 2
    },
    {
      "ISOLocal": "en_US",
      "file": "https://binaries.rebble.io/lp/yLtzOoj-en_US.pbl",
      "firmware": "3.8.0",
      "hardware": "v2_0",
      "id": "5670a3d8347e8d1600601fc1",
      "localName": "English",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English",
      "version": 1
    },
    {
      "ISOLocal": "fr_FR",
      "file": "https://binaries.rebble.io/lp/TrFihXD-fr_FR.pbl",
      "firmware": "3.8.0",
      "hardware": "ev2_4",
      "id": "5670a413cedf381b009f3725",
      "localName": "Français",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "French",
      "version": 32
    },
    {
      "ISOLocal": "fr_FR",
      "file": "https://binaries.rebble.io/lp/2LEeOi3-fr_FR.pbl",
      "firmware": "3.4.0",
      "hardware": "snowy_dvt",
      "id": "55ef3e4d40b14b1400f2b2ce",
      "localName": "Français",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "French",
      "version": 32
    },
    {
      "ISOLocal": "pt_PT",
      "file": "https://binaries.rebble.io/lp/8qbff4h-pt_PT.pbl",
      "firmware": "3.8.0",
      "hardware": "v1_5",
      "id": "5670a7f34d40a31b00cb2a50",
      "localName": "Português",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Portuguese",
      "version": 13
    },
    {
      "ISOLocal": "pt_PT",
      "file": "https://binaries.rebble.io/lp/DBy3zn8-pt_PT.pbl",
      "firmware": "3.8.0",
      "hardware": "v2_0",
      "id": "5670a8034d40a31b00cb2a51",
      "localName": "Português",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Portuguese",
      "version": 13
    },
    {
      "ISOLocal": "fr_FR",
      "file": "https://binaries.rebble.io/lp/PghkVPb-fr_FR.pbl",
      "firmware": "3.8.0",
      "hardware": "v2_0",
      "id": "5670a447b9abe4160010c222",
      "localName": "Français",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "French",
      "version": 32
    },
    {
      "ISOLocal": "en_US",
      "file": "https://binaries.rebble.io/lp/X9Xx37z-Dn1N058-en_US.pbl",
      "firmware": "3.4.0",
      "hardware": "snowy_dvt",
      "id": "55ef3ea0d47f451400aba9d1",
      "localName": "English",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English",
      "version": 1
    },
    {
      "ISOLocal": "it_IT",
      "file": "https://binaries.rebble.io/lp/ETKbeFr-it_IT.pbl",
      "firmware": "3.8.0",
      "hardware": "ev2_4",
      "id": "5670a63db9abe4160010c225",
      "localName": "Italiano",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Italian",
      "version": 15
    },
    {
      "ISOLocal": "en_CN",
      "file": "https://binaries.rebble.io/lp/kTOouEQ-en_CN.pbl",
      "firmware": "3.10.0",
      "hardware": "snowy_s3",
      "id": "56df1dc903f9d616009889db",
      "localName": "English + 简体通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+SimplifiedChinese",
      "version": 2
    },
    {
      "ISOLocal": "en_US",
      "file": "https://binaries.rebble.io/lp/WXnHVGy-en_US.pbl",
      "firmware": "3.8.0",
      "hardware": "ev2_4",
      "id": "5670a38acedf381b009f3724",
      "localName": "English",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English",
      "version": 1
    },
    {
      "ISOLocal": "es_ES",
      "file": "https://binaries.rebble.io/lp/u7f2giK-es_ES.pbl",
      "firmware": "3.8.0",
      "hardware": "ev2_4",
      "id": "5670a5c2b9abe4160010c224",
      "localName": "Español",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Spanish",
      "version": 28
    },
    {
      "ISOLocal": "en_US",
      "file": "https://binaries.rebble.io/lp/bFuQbKO-en_US.pbl",
      "firmware": "3.8.0",
      "hardware": "v1_5",
      "id": "5670a3ad4d40a31b00cb2a4c",
      "localName": "English",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English",
      "version": 1
    },
    {
      "ISOLocal": "de_DE",
      "file": "https://binaries.rebble.io/lp/EkydbXY-de_DE.pbl",
      "firmware": "2.8.0",
      "hardware": "ev2_4",
      "id": "547f35eedf4579140047174d",
      "localName": "Deutsch",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "German",
      "version": 1
    },
    {
      "ISOLocal": "de_DE",
      "file": "https://binaries.rebble.io/lp/hlOFDp1-de_DE.pbl",
      "firmware": "3.8.0",
      "hardware": "v2_0",
      "id": "5670a5034d40a31b00cb2a4d",
      "localName": "Deutsch",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "German",
      "version": 28
    },
    {
      "ISOLocal": "ru_RU",
      "file": "https://binaries.rebble.io/lp/sNN1m33-ru_RU.pbl",
      "firmware": "3.8.0",
      "hardware": "bb2",
      "id": "56b28b47fbf86216008a0c3e",
      "localName": "English + Кириллица (для уведомлений)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Russian",
      "version": 3
    },
    {
      "ISOLocal": "ru_RU",
      "file": "https://binaries.rebble.io/lp/gaxruNO-ru_RU.pbl",
      "firmware": "3.8.0",
      "hardware": "ev2_4",
      "id": "56b28b6ce21e461b00734c16",
      "localName": "English + Кириллица (для уведомлений)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Russian",
      "version": 3
    },
    {
      "ISOLocal": "en_TW",
      "file": "https://binaries.rebble.io/lp/z8vO3Hk-en_TW.pbl",
      "firmware": "3.10.0",
      "hardware": "spalding",
      "id": "56df1ec4279cc21b005eb786",
      "localName": "English + 繁體通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+TraditionalChinese",
      "version": 1
    },
    {
      "ISOLocal": "fr_FR",
      "file": "https://binaries.rebble.io/lp/qcBr3f1-fr_FR.pbl",
      "firmware": "2.8.0",
      "hardware": "v1_5",
      "id": "547f3ab5b511f5130099cbe9",
      "localName": "Français",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "French",
      "version": 1
    },
    {
      "ISOLocal": "en_US",
      "file": "https://binaries.rebble.io/lp/Dn1N058-en_US.pbl",
      "firmware": "2.8.0",
      "hardware": "v1_5",
      "id": "547f65f8d4a5be130004884e",
      "localName": "English",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English",
      "version": 1
    },
    {
      "ISOLocal": "ru_RU",
      "file": "https://binaries.rebble.io/lp/HDBLrGp-ru_RU.pbl",
      "firmware": "3.8.0",
      "hardware": "snowy_dvt",
      "id": "56b28b9bd3921f1b00ab6834",
      "localName": "English + Кириллица (для уведомлений)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Russian",
      "version": 3
    },
    {
      "ISOLocal": "ru_RU",
      "file": "https://binaries.rebble.io/lp/jgAbIBK-ru_RU.pbl",
      "firmware": "3.8.0",
      "hardware": "snowy_s3",
      "id": "56b28bbdd3921f1b00ab6835",
      "localName": "English + Кириллица (для уведомлений)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Russian",
      "version": 3
    },
    {
      "ISOLocal": "de_DE",
      "file": "https://binaries.rebble.io/lp/M1xu8Fi-de_DE.pbl",
      "firmware": "2.8.0",
      "hardware": "v2_0",
      "id": "547f362ab511f5130099cbe8",
      "localName": "Deutsch",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "German",
      "version": 1
    },
    {
      "ISOLocal": "en_US",
      "file": "https://binaries.rebble.io/lp/yBE7uWg-en_US.pbl",
      "firmware": "2.8.0",
      "hardware": "bb2",
      "id": "547f6911df45791400471755",
      "localName": "English",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English",
      "version": 1
    },
    {
      "ISOLocal": "es_ES",
      "file": "https://binaries.rebble.io/lp/pokRBw8-es_ES.pbl",
      "firmware": "3.4.0",
      "hardware": "snowy_s3",
      "id": "55ef3e35abdc101500894f13",
      "localName": "Español",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Spanish",
      "version": 28
    },
    {
      "ISOLocal": "es_ES",
      "file": "https://binaries.rebble.io/lp/M5nEntT-es_ES.pbl",
      "firmware": "3.8.0",
      "hardware": "v2_0",
      "id": "5670a5eb347e8d1600601fc3",
      "localName": "Español",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Spanish",
      "version": 28
    },
    {
      "ISOLocal": "en_TW",
      "file": "https://binaries.rebble.io/lp/wu1QMwE-en_TW.pbl",
      "firmware": "3.10.0",
      "hardware": "v2_0",
      "id": "56df1f0603f9d616009889de",
      "localName": "English + 繁體通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+TraditionalChinese",
      "version": 1
    },
    {
      "ISOLocal": "de_DE",
      "file": "https://binaries.rebble.io/lp/HreGCZz-de_DE.pbl",
      "firmware": "3.4.0",
      "hardware": "snowy_dvt",
      "id": "55ef3dccd47f451400aba9d0",
      "localName": "Deutsch",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "German",
      "version": 28
    },
    {
      "ISOLocal": "de_DE",
      "file": "https://binaries.rebble.io/lp/SGwqQtC-de_DE.pbl",
      "firmware": "3.4.0",
      "hardware": "snowy_s3",
      "id": "55ef3de49992251500bfb0e1",
      "localName": "Deutsch",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "German",
      "version": 28
    },
    {
      "ISOLocal": "fr_FR",
      "file": "https://binaries.rebble.io/lp/RaIc0OX-fr_FR.pbl",
      "firmware": "3.4.0",
      "hardware": "snowy_s3",
      "id": "55ef3e6aabdc101500894f14",
      "localName": "Français",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "French",
      "version": 32
    },
    {
      "ISOLocal": "ru_RU",
      "file": "https://binaries.rebble.io/lp/9WJii2R-ru_RU.pbl",
      "firmware": "3.8.0",
      "hardware": "v1_5",
      "id": "56b28c11e21e461b00734c17",
      "localName": "English + Кириллица (для уведомлений)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Russian",
      "version": 3
    },
    {
      "ISOLocal": "en_US",
      "file": "https://binaries.rebble.io/lp/Dn1N058-en_US.pbl",
      "firmware": "2.8.0",
      "hardware": "ev2_4",
      "id": "547f65e0a511f2140099c8ba",
      "localName": "English",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English",
      "version": 1
    },
    {
      "ISOLocal": "it_IT",
      "file": "https://binaries.rebble.io/lp/zkPZL1B-it_IT.pbl",
      "firmware": "3.8.0",
      "hardware": "snowy_dvt",
      "id": "5670a6b84d40a31b00cb2a4e",
      "localName": "Italiano",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Italian",
      "version": 15
    },
    {
      "ISOLocal": "en_CN",
      "file": "https://binaries.rebble.io/lp/QoG7BEZ-en_CN.pbl",
      "firmware": "3.10.0",
      "hardware": "snowy_dvt",
      "id": "56df1da59de9101b0028020d",
      "localName": "English + 简体通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+SimplifiedChinese",
      "version": 2
    },
    {
      "ISOLocal": "zh_CN",
      "file": "https://binaries.rebble.io/lp/lMG5CiT-zh_CN.pbl",
      "firmware": "2.9.0",
      "hardware": "v2_0",
      "id": "550874f3a795921400f4d87a",
      "localName": "中文 (简体)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Simplified Chinese",
      "version": 1
    },
    {
      "ISOLocal": "en_US",
      "file": "https://binaries.rebble.io/lp/GGpQ16m-en_US.pbl",
      "firmware": "2.8.0",
      "hardware": "v2_0",
      "id": "547f6671df45791400471754",
      "localName": "English",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English",
      "version": 1
    },
    {
      "ISOLocal": "de_DE",
      "file": "https://binaries.rebble.io/lp/Od4Wl29-de_DE.pbl",
      "firmware": "3.8.0",
      "hardware": "ev2_4",
      "id": "5670a4bb347e8d1600601fc2",
      "localName": "Deutsch",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "German",
      "version": 28
    },
    {
      "ISOLocal": "it_IT",
      "file": "https://binaries.rebble.io/lp/l5VhgOO-it_IT.pbl",
      "firmware": "3.8.0",
      "hardware": "v1_5",
      "id": "5670a650b9abe4160010c226",
      "localName": "Italiano",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Italian",
      "version": 15
    },
    {
      "ISOLocal": "de_DE",
      "file": "https://binaries.rebble.io/lp/2VF84e5-de_DE.pbl",
      "firmware": "3.8.0",
      "hardware": "spalding",
      "id": "5670a8becedf381b009f3729",
      "localName": "Deutsch",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "German",
      "version": 28
    },
    {
      "ISOLocal": "pt_PT",
      "file": "https://binaries.rebble.io/lp/Wdc0pxt-pt_PT.pbl",
      "firmware": "3.8.0",
      "hardware": "spalding",
      "id": "5670a8f3347e8d1600601fc5",
      "localName": "Português",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Portuguese",
      "version": 13
    },
    {
      "ISOLocal": "en_CN",
      "file": "https://binaries.rebble.io/lp/bbdf45F-en_CN.pbl",
      "firmware": "3.10.0",
      "hardware": "spalding",
      "id": "56df1df2279cc21b005eb784",
      "localName": "English + 简体通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+SimplifiedChinese",
      "version": 2
    },
    {
      "ISOLocal": "en_CN",
      "file": "https://binaries.rebble.io/lp/IbRxJKf-en_CN.pbl",
      "firmware": "3.10.0",
      "hardware": "v2_0",
      "id": "56df1e336aecc31600bcbab4",
      "localName": "English + 简体通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+SimplifiedChinese",
      "version": 2
    },
    {
      "ISOLocal": "fr_FR",
      "file": "https://binaries.rebble.io/lp/qcBr3f1-fr_FR.pbl",
      "firmware": "2.8.0",
      "hardware": "ev2_4",
      "id": "547f3aa21b79ba13003a300e",
      "localName": "Français",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "French",
      "version": 1
    },
    {
      "ISOLocal": "zh_CN",
      "file": "https://binaries.rebble.io/lp/IdlEZEQ-zh_CN.pbl",
      "firmware": "2.9.0",
      "hardware": "v1_5",
      "id": "550874df17bfdb1500771f8f",
      "localName": "中文 (简体)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Simplified Chinese",
      "version": 1
    },
    {
      "ISOLocal": "es_ES",
      "file": "https://binaries.rebble.io/lp/bnRoRvE-es_ES.pbl",
      "firmware": "3.4.0",
      "hardware": "snowy_dvt",
      "id": "55ef3e1a9992251500bfb0e2",
      "localName": "Español",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Spanish",
      "version": 28
    },
    {
      "ISOLocal": "it_IT",
      "file": "https://binaries.rebble.io/lp/rDedW7P-it_IT.pbl",
      "firmware": "3.8.0",
      "hardware": "spalding",
      "id": "5670a8dab9abe4160010c22a",
      "localName": "Italiano",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Italian",
      "version": 15
    },
    {
      "ISOLocal": "es_ES",
      "file": "https://binaries.rebble.io/lp/jCjmLvU-es_ES.pbl",
      "firmware": "2.8.0",
      "hardware": "ev2_4",
      "id": "547f391edf45791400471750",
      "localName": "Español",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Spanish",
      "version": 1
    },
    {
      "ISOLocal": "zh_TW",
      "file": "https://binaries.rebble.io/lp/nwsTb46-zh_TW.pbl",
      "firmware": "2.9.0",
      "hardware": "v2_0",
      "id": "5508753bb175e91400099330",
      "localName": "中文 (繁體)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Traditional Chinese",
      "version": 1
    },
    {
      "ISOLocal": "de_DE",
      "file": "https://binaries.rebble.io/lp/M1xu8Fi-de_DE.pbl",
      "firmware": "2.8.0",
      "hardware": "v1_5",
      "id": "547f360fdf4579140047174e",
      "localName": "Deutsch",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "German",
      "version": 1
    },
    {
      "ISOLocal": "es_ES",
      "file": "https://binaries.rebble.io/lp/jCjmLvU-es_ES.pbl",
      "firmware": "2.8.0",
      "hardware": "v1_5",
      "id": "547f3939df45791400471751",
      "localName": "Español",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Spanish",
      "version": 1
    },
    {
      "ISOLocal": "es_ES",
      "file": "https://binaries.rebble.io/lp/PmaMD2c-es_ES.pbl",
      "firmware": "3.8.0",
      "hardware": "spalding",
      "id": "5670a91eb9abe4160010c22b",
      "localName": "Español",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Spanish",
      "version": 28
    },
    {
      "ISOLocal": "ru_RU",
      "file": "https://binaries.rebble.io/lp/lqmxEzg-ru_RU.pbl",
      "firmware": "3.8.0",
      "hardware": "v2_0",
      "id": "56b28c36d3921f1b00ab6836",
      "localName": "English + Кириллица (для уведомлений)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Russian",
      "version": 3
    },
    {
      "ISOLocal": "fr_FR",
      "file": "https://binaries.rebble.io/lp/Pvk2ZR4-fr_FR.pbl",
      "firmware": "3.8.0",
      "hardware": "v1_5",
      "id": "5670a428cedf381b009f3726",
      "localName": "Français",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "French",
      "version": 32
    },
    {
      "ISOLocal": "es_ES",
      "file": "https://binaries.rebble.io/lp/jCjmLvU-es_ES.pbl",
      "firmware": "2.8.0",
      "hardware": "v2_0",
      "id": "547f3950df45791400471752",
      "localName": "Español",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Spanish",
      "version": 1
    },
    {
      "ISOLocal": "fr_FR",
      "file": "https://binaries.rebble.io/lp/qcBr3f1-fr_FR.pbl",
      "firmware": "2.8.0",
      "hardware": "v2_0",
      "id": "547f3acdb511f5130099cbea",
      "localName": "Français",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "French",
      "version": 1
    },
    {
      "ISOLocal": "ru_RU",
      "file": "https://binaries.rebble.io/lp/2JuBJ56-ru_RU.pbl",
      "firmware": "3.8.0",
      "hardware": "spalding",
      "id": "56b28be26325961600cbe236",
      "localName": "English + Кириллица (для уведомлений)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Russian",
      "version": 3
    },
    {
      "ISOLocal": "en_TW",
      "file": "https://binaries.rebble.io/lp/ndHGwZm-en_TW.pbl",
      "firmware": "3.10.0",
      "hardware": "snowy_dvt",
      "id": "56df1e8203f9d616009889dd",
      "localName": "English + 繁體通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+TraditionalChinese",
      "version": 1
    },
    {
      "ISOLocal": "de_DE",
      "file": "https://binaries.rebble.io/lp/QxfUUVU-de_DE.pbl",
      "firmware": "2.8.0",
      "hardware": "bb2",
      "id": "547f38fedf4579140047174f",
      "localName": "Deutsch",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "German",
      "version": 1
    },
    {
      "ISOLocal": "fr_FR",
      "file": "https://binaries.rebble.io/lp/Q5NOtdC-fr_FR.pbl",
      "firmware": "2.8.0",
      "hardware": "bb2",
      "id": "547f40e6d1083c140027263b",
      "localName": "Français",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "French",
      "version": 1
    },
    {
      "ISOLocal": "es_ES",
      "file": "https://binaries.rebble.io/lp/kL8SksK-es_ES.pbl",
      "firmware": "2.8.0",
      "hardware": "bb2",
      "id": "547f3a8ad1083c140027263a",
      "localName": "Español",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Spanish",
      "version": 1
    },
    {
      "ISOLocal": "zh_TW",
      "file": "https://binaries.rebble.io/lp/SutQecS-zh_TW.pbl",
      "firmware": "2.9.0",
      "hardware": "v1_5",
      "id": "5508750ea795921400f4d87b",
      "localName": "中文 (繁體)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Traditional Chinese",
      "version": 1
    },
    {
      "ISOLocal": "es_ES",
      "file": "https://binaries.rebble.io/lp/M4TmHfQ-es_ES.pbl",
      "firmware": "3.8.0",
      "hardware": "v1_5",
      "id": "5670a5d6cedf381b009f3727",
      "localName": "Español",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Spanish",
      "version": 28
    },
    {
      "ISOLocal": "es_ES",
      "file": "https://binaries.rebble.io/lp/VDFK6zp-es_ES.pbl",
      "firmware": "4.0.0",
      "hardware": "silk_evt",
      "id": "57be3ec85fe5b7001ab59161",
      "localName": "Español",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Spanish",
      "version": 34
    },
    {
      "ISOLocal": "es_ES",
      "file": "https://binaries.rebble.io/lp/7T8wvUU-es_ES.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_dvt",
      "id": "57be3f3c287571001a4c5824",
      "localName": "Español",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Spanish",
      "version": 34
    },
    {
      "ISOLocal": "es_ES",
      "file": "https://binaries.rebble.io/lp/gluTJKY-es_ES.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_s3",
      "id": "57be3f3f287571001a4c5825",
      "localName": "Español",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Spanish",
      "version": 34
    },
    {
      "ISOLocal": "es_ES",
      "file": "https://binaries.rebble.io/lp/dCt5LZg-es_ES.pbl",
      "firmware": "4.0.0",
      "hardware": "spalding",
      "id": "57be3f41287571001a4c5826",
      "localName": "Español",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Spanish",
      "version": 34
    },
    {
      "ISOLocal": "es_ES",
      "file": "https://binaries.rebble.io/lp/QKYD6CQ-es_ES.pbl",
      "firmware": "4.0.0",
      "hardware": "silk",
      "id": "57be3f45287571001a4c5827",
      "localName": "Español",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Spanish",
      "version": 34
    },
    {
      "ISOLocal": "en_US",
      "file": "https://binaries.rebble.io/lp/CJEDY8M-en_US.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_dvt",
      "id": "57be40ab5fe5b7001ab59162",
      "localName": "English",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English",
      "version": 1
    },
    {
      "ISOLocal": "en_US",
      "file": "https://binaries.rebble.io/lp/X8BWOSY-en_US.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_s3",
      "id": "57be40ae5fe5b7001ab59163",
      "localName": "English",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English",
      "version": 1
    },
    {
      "ISOLocal": "en_US",
      "file": "https://binaries.rebble.io/lp/gXO1usQ-en_US.pbl",
      "firmware": "4.0.0",
      "hardware": "spalding",
      "id": "57be40b2287571001a4c5828",
      "localName": "English",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English",
      "version": 1
    },
    {
      "ISOLocal": "en_US",
      "file": "https://binaries.rebble.io/lp/960sGtg-en_US.pbl",
      "firmware": "4.0.0",
      "hardware": "silk",
      "id": "57be40b55fe5b7001ab59164",
      "localName": "English",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English",
      "version": 1
    },
    {
      "ISOLocal": "en_US",
      "file": "https://binaries.rebble.io/lp/16fYHW4-en_US.pbl",
      "firmware": "4.0.0",
      "hardware": "silk_evt",
      "id": "57be40b7287571001a4c5829",
      "localName": "English",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English",
      "version": 1
    },
    {
      "ISOLocal": "fr_FR",
      "file": "https://binaries.rebble.io/lp/PEFAq6m-fr_FR.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_dvt",
      "id": "57be412f5fe5b7001ab59165",
      "localName": "Français",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "French",
      "version": 38
    },
    {
      "ISOLocal": "fr_FR",
      "file": "https://binaries.rebble.io/lp/cQdYUfg-fr_FR.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_s3",
      "id": "57be41325f8363001b3055fd",
      "localName": "Français",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "French",
      "version": 38
    },
    {
      "ISOLocal": "fr_FR",
      "file": "https://binaries.rebble.io/lp/l8B0BJp-fr_FR.pbl",
      "firmware": "4.0.0",
      "hardware": "spalding",
      "id": "57be41355fe5b7001ab59166",
      "localName": "Français",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "French",
      "version": 38
    },
    {
      "ISOLocal": "fr_FR",
      "file": "https://binaries.rebble.io/lp/gIfNRlM-fr_FR.pbl",
      "firmware": "4.0.0",
      "hardware": "silk",
      "id": "57be4139287571001a4c582a",
      "localName": "Français",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "French",
      "version": 38
    },
    {
      "ISOLocal": "fr_FR",
      "file": "https://binaries.rebble.io/lp/QLLQ1v4-fr_FR.pbl",
      "firmware": "4.0.0",
      "hardware": "silk_evt",
      "id": "57be413c287571001a4c582b",
      "localName": "Français",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "French",
      "version": 38
    },
    {
      "ISOLocal": "de_DE",
      "file": "https://binaries.rebble.io/lp/4lMwAEP-de_DE.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_dvt",
      "id": "57be41932c68e6001b192811",
      "localName": "Deutsch",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "German",
      "version": 34
    },
    {
      "ISOLocal": "de_DE",
      "file": "https://binaries.rebble.io/lp/JAUZg9r-de_DE.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_s3",
      "id": "57be41965f8363001b3055fe",
      "localName": "Deutsch",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "German",
      "version": 34
    },
    {
      "ISOLocal": "de_DE",
      "file": "https://binaries.rebble.io/lp/BAD8P5o-de_DE.pbl",
      "firmware": "4.0.0",
      "hardware": "spalding",
      "id": "57be41995f8363001b3055ff",
      "localName": "Deutsch",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "German",
      "version": 34
    },
    {
      "ISOLocal": "de_DE",
      "file": "https://binaries.rebble.io/lp/Vzq58DE-de_DE.pbl",
      "firmware": "4.0.0",
      "hardware": "silk",
      "id": "57be419c5fe5b7001ab59167",
      "localName": "Deutsch",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "German",
      "version": 34
    },
    {
      "ISOLocal": "de_DE",
      "file": "https://binaries.rebble.io/lp/BHTmRUc-de_DE.pbl",
      "firmware": "4.0.0",
      "hardware": "silk_evt",
      "id": "57be419f5fe5b7001ab59168",
      "localName": "Deutsch",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "German",
      "version": 34
    },
    {
      "ISOLocal": "it_IT",
      "file": "https://binaries.rebble.io/lp/IXdfvsf-it_IT.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_dvt",
      "id": "57be41b35f8363001b305600",
      "localName": "Italiano",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Italian",
      "version": 21
    },
    {
      "ISOLocal": "it_IT",
      "file": "https://binaries.rebble.io/lp/MBY8HUk-it_IT.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_s3",
      "id": "57be41b62c68e6001b192812",
      "localName": "Italiano",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Italian",
      "version": 21
    },
    {
      "ISOLocal": "it_IT",
      "file": "https://binaries.rebble.io/lp/3ZKMFAJ-it_IT.pbl",
      "firmware": "4.0.0",
      "hardware": "spalding",
      "id": "57be41b95f8363001b305601",
      "localName": "Italiano",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Italian",
      "version": 21
    },
    {
      "ISOLocal": "it_IT",
      "file": "https://binaries.rebble.io/lp/HPECY6i-it_IT.pbl",
      "firmware": "4.0.0",
      "hardware": "silk",
      "id": "57be41bc5f8363001b305602",
      "localName": "Italiano",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Italian",
      "version": 21
    },
    {
      "ISOLocal": "it_IT",
      "file": "https://binaries.rebble.io/lp/Ugxjvbi-it_IT.pbl",
      "firmware": "4.0.0",
      "hardware": "silk_evt",
      "id": "57be41bf2c68e6001b192813",
      "localName": "Italiano",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Italian",
      "version": 21
    },
    {
      "ISOLocal": "pt_PT",
      "file": "https://binaries.rebble.io/lp/KRlZrUS-pt_PT.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_dvt",
      "id": "57be41d05f8363001b305603",
      "localName": "Português",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Portuguese",
      "version": 19
    },
    {
      "ISOLocal": "pt_PT",
      "file": "https://binaries.rebble.io/lp/C5J2kKA-pt_PT.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_s3",
      "id": "57be41d35f8363001b305604",
      "localName": "Português",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Portuguese",
      "version": 19
    },
    {
      "ISOLocal": "pt_PT",
      "file": "https://binaries.rebble.io/lp/wc3KjQY-pt_PT.pbl",
      "firmware": "4.0.0",
      "hardware": "spalding",
      "id": "57be41d6287571001a4c582c",
      "localName": "Português",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Portuguese",
      "version": 19
    },
    {
      "ISOLocal": "pt_PT",
      "file": "https://binaries.rebble.io/lp/vCoHVfL-pt_PT.pbl",
      "firmware": "4.0.0",
      "hardware": "silk",
      "id": "57be41da5f8363001b305605",
      "localName": "Português",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Portuguese",
      "version": 19
    },
    {
      "ISOLocal": "pt_PT",
      "file": "https://binaries.rebble.io/lp/DEAxmda-pt_PT.pbl",
      "firmware": "4.0.0",
      "hardware": "silk_evt",
      "id": "57be41dd287571001a4c582d",
      "localName": "Português",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "Portuguese",
      "version": 19
    },
    {
      "ISOLocal": "ru_RU",
      "file": "https://binaries.rebble.io/lp/ATH6w2K-ru_RU.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_dvt",
      "id": "57c4b92e852fe4001b3b5b20",
      "localName": "English + Кириллица",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Russian",
      "version": 3
    },
    {
      "ISOLocal": "ru_RU",
      "file": "https://binaries.rebble.io/lp/d0oGecv-ru_RU.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_s3",
      "id": "57c4b930852fe4001b3b5b21",
      "localName": "English + Кириллица",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Russian",
      "version": 3
    },
    {
      "ISOLocal": "ru_RU",
      "file": "https://binaries.rebble.io/lp/kaQriKK-ru_RU.pbl",
      "firmware": "4.0.0",
      "hardware": "spalding",
      "id": "57c4b9327071d3001aaa19d9",
      "localName": "English + Кириллица",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Russian",
      "version": 3
    },
    {
      "ISOLocal": "ru_RU",
      "file": "https://binaries.rebble.io/lp/ywAw1NK-ru_RU.pbl",
      "firmware": "4.0.0",
      "hardware": "silk",
      "id": "57c4b9347071d3001aaa19da",
      "localName": "English + Кириллица",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Russian",
      "version": 3
    },
    {
      "ISOLocal": "ru_RU",
      "file": "https://binaries.rebble.io/lp/J65CZEn-ru_RU.pbl",
      "firmware": "4.0.0",
      "hardware": "silk_evt",
      "id": "57c4b9367071d3001aaa19db",
      "localName": "English + Кириллица",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Russian",
      "version": 3
    },
    {
      "ISOLocal": "en_TW",
      "file": "https://binaries.rebble.io/lp/XnZkJE5-en_TW.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_dvt",
      "id": "57c4b943774b7d001b4717b4",
      "localName": "English + 繁體通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+TraditionalChinese",
      "version": 1
    },
    {
      "ISOLocal": "en_TW",
      "file": "https://binaries.rebble.io/lp/X30WFoq-en_TW.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_s3",
      "id": "57c4b948774b7d001b4717b5",
      "localName": "English + 繁體通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+TraditionalChinese",
      "version": 1
    },
    {
      "ISOLocal": "en_TW",
      "file": "https://binaries.rebble.io/lp/lnfKJjI-en_TW.pbl",
      "firmware": "4.0.0",
      "hardware": "spalding",
      "id": "57c4b94c14f887001cb3e8c3",
      "localName": "English + 繁體通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+TraditionalChinese",
      "version": 1
    },
    {
      "ISOLocal": "en_TW",
      "file": "https://binaries.rebble.io/lp/dGKVKJA-en_TW.pbl",
      "firmware": "4.0.0",
      "hardware": "silk",
      "id": "57c4b95414f887001cb3e8c4",
      "localName": "English + 繁體通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+TraditionalChinese",
      "version": 1
    },
    {
      "ISOLocal": "en_TW",
      "file": "https://binaries.rebble.io/lp/Qb20rbp-en_TW.pbl",
      "firmware": "4.0.0",
      "hardware": "silk_evt",
      "id": "57c4b9587071d3001aaa19dc",
      "localName": "English + 繁體通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+TraditionalChinese",
      "version": 1
    },
    {
      "ISOLocal": "en_CN",
      "file": "https://binaries.rebble.io/lp/3TZWD3X-en_CN.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_dvt",
      "id": "57c4b96c852fe4001b3b5b22",
      "localName": "English + 简体通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+SimplifiedChinese",
      "version": 2
    },
    {
      "ISOLocal": "en_CN",
      "file": "https://binaries.rebble.io/lp/YqkXICr-en_CN.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_s3",
      "id": "57c4b9707071d3001aaa19dd",
      "localName": "English + 简体通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+SimplifiedChinese",
      "version": 2
    },
    {
      "ISOLocal": "en_CN",
      "file": "https://binaries.rebble.io/lp/YQ2IKVE-en_CN.pbl",
      "firmware": "4.0.0",
      "hardware": "spalding",
      "id": "57c4b974852fe4001b3b5b23",
      "localName": "English + 简体通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+SimplifiedChinese",
      "version": 2
    },
    {
      "ISOLocal": "en_CN",
      "file": "https://binaries.rebble.io/lp/EDJQTJm-en_CN.pbl",
      "firmware": "4.0.0",
      "hardware": "silk",
      "id": "57c4b9797071d3001aaa19de",
      "localName": "English + 简体通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+SimplifiedChinese",
      "version": 2
    },
    {
      "ISOLocal": "en_CN",
      "file": "https://binaries.rebble.io/lp/qik58a8-en_CN.pbl",
      "firmware": "4.0.0",
      "hardware": "silk_evt",
      "id": "57c4b97d852fe4001b3b5b24",
      "localName": "English + 简体通知",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+SimplifiedChinese",
      "version": 2
    },
    {
      "ISOLocal": "en_MY",
      "file": "https://binaries.rebble.io/lp/myanmar.pbl",
      "firmware": "4.0.0",
      "hardware": "silk_evt",
      "id": "57c4b97d852ff00000000000",
      "localName": "English + မြန်မာစာ (contrib by LCP)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Burmese(contrib)",
      "version": 1
    },
    {
      "ISOLocal": "en_MY",
      "file": "https://binaries.rebble.io/lp/myanmar.pbl",
      "firmware": "4.0.0",
      "hardware": "silk",
      "id": "57c4b97d852ff00000000001",
      "localName": "English + မြန်မာစာ (contrib by LCP)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Burmese(contrib)",
      "version": 1
    },
    {
      "ISOLocal": "en_MY",
      "file": "https://binaries.rebble.io/lp/myanmar.pbl",
      "firmware": "4.0.0",
      "hardware": "spalding",
      "id": "57c4b97d852ff00000000002",
      "localName": "English + မြန်မာစာ (contrib by LCP)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Burmese(contrib)",
      "version": 1
    },
    {
      "ISOLocal": "en_MY",
      "file": "https://binaries.rebble.io/lp/myanmar.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_dvt",
      "id": "57c4b97d852ff00000000003",
      "localName": "English + မြန်မာစာ (contrib by LCP)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Burmese(contrib)",
      "version": 1
    },
    {
      "ISOLocal": "en_MY",
      "file": "https://binaries.rebble.io/lp/myanmar.pbl",
      "firmware": "4.0.0",
      "hardware": "snowy_s3",
      "id": "57c4b97d852ff00000000004",
      "localName": "English + မြန်မာစာ (contrib by LCP)",
      "mobile": {
        "name": "ios",
        "version": "2.6.0"
      },
      "name": "English+Burmese(contrib)",
      "version": 1
    },
    {
      "ISOLocal": "bg",
      "file": "https://github.com/MarSoft/pebble-firmware-utils/raw/builds/langs/Bulgarian-v1.pbl",
      "firmware": "4.0.0",
      "hardware": null,
      "id": "marsoft1",
      "localName": "български",
      "name": "Bulgarian - by MarSoft",
      "version": 1
    },  
    {
      "ISOLocal": "ca",
      "file": "https://github.com/MarSoft/pebble-firmware-utils/raw/builds/langs/Catalan-v1.pbl",
      "firmware": "4.0.0",
      "hardware": null,
      "id": "marsoft2",
      "localName": "català",
      "name": "Catalan - by MarSoft",
      "version": 1
    }
  ]
}
""".trimIndent()
