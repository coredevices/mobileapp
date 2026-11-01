package coredevices.util.integrations

import java.security.SecureRandom

internal actual fun generateSecureRandomString(length: Int, charset: List<Char>): String {
    val random = SecureRandom()
    return buildString {
        for (i in 0 until length) {
            val randomIndex = random.nextInt(charset.size)
            append(charset[randomIndex])
        }
    }
}
