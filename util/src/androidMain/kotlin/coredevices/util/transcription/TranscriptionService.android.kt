package coredevices.util.transcription

import androidx.core.os.LocaleListCompat
import java.util.Locale

actual val SpokenLanguageOptions: List<Pair<String, String>> by lazy {
    val userList = buildList {
        val list = LocaleListCompat.getAdjustedDefault()
        for (i in 0 until list.size()) {
            add(list[i]!!.language)
        }
    }.distinct()
    val locales = Locale.getISOLanguages().mapNotNull {
        Locale.forLanguageTag(it).let {
            if (it.displayLanguage.isNullOrBlank()) {
                null
            } else {
                it
            }
        }
    }
    return@lazy locales.sortedBy {
        if (it.language in userList) {
            userList.indexOf(it.language).toString().padStart(2, '0')
        } else {
            it.displayLanguage
        }
    }.map {
        it.language to it.displayLanguage
    }
}