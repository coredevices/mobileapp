package coredevices.util

import kotlinx.coroutines.channels.Channel

interface Platform {
    val name: String

    suspend fun openUrl(url: String)

    /**
     * On platforms with a BG task assertion api, holds an assertion for the duration of the task
     * and cancels the block if the assertion expires.
     *
     * Runs on default dispatcher.
     */
    suspend fun runWithBgTask(name: String, task: suspend () -> Unit)
    companion object {
        /**
         * Channel for URI intents
         */
        val uriChannel = Channel<String>(1)
    }
}

inline val Platform.isIOS get() = name.contains("iOS", ignoreCase = true)
inline val Platform.isAndroid get() = name.contains("Android", ignoreCase = true)