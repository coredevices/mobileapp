package coredevices.util

import kotlinx.coroutines.channels.Channel

interface Platform {
    val name: String

    suspend fun openUrl(url: String)
    companion object {
        /**
         * Channel for URI intents
         */
        val uriChannel = Channel<String>(1)
    }
}

inline val Platform.isIOS get() = name.contains("iOS", ignoreCase = true)
inline val Platform.isAndroid get() = name.contains("Android", ignoreCase = true)