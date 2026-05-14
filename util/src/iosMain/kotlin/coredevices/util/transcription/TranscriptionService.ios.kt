package coredevices.util.transcription

import platform.Foundation.ISOLanguageCodes
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleLanguageCode
import platform.Foundation.localeWithLocaleIdentifier

actual val SpokenLanguageOptions: List<Pair<String, String>> = NSLocale.ISOLanguageCodes.map {
    it as String
    it to (NSLocale.localeWithLocaleIdentifier(it).displayNameForKey(NSLocaleLanguageCode, it) ?: it)
}.sortedBy {
    it.second
}