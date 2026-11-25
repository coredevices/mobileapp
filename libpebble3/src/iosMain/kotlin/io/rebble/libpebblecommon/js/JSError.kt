package io.rebble.libpebblecommon.js

internal data class JSError(
    val cause: String?,
    val message: String?,
    val stack: String?,
) {
    companion object {
        fun fromDictionary(dict: Map<Any?, *>): JSError {
            val cause = dict["cause"]?.toString()
            val message = dict["message"]?.toString()
            val stack = dict["stack"]?.toString()
            return JSError(cause, message, stack)
        }
    }
}
