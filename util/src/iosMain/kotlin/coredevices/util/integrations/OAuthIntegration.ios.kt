package coredevices.util.integrations

import platform.posix.arc4random_uniform

internal actual fun generateSecureRandomString(length: Int, charset: List<Char>): String {
    return buildString {
        for (i in 0 until length) {
            val randomIndex = arc4random_uniform(charset.size.toUInt()).toInt()
            append(charset[randomIndex])
        }
    }
}
