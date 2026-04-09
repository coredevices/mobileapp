package coredevices.util.integrations

import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64

internal actual fun generateSecureRandomString(length: Int, charset: List<Char>): String {
    val random = SecureRandom()
    return buildString {
        for (i in 0 until length) {
            val randomIndex = random.nextInt(charset.size)
            append(charset[randomIndex])
        }
    }
}

internal actual fun sha256(input: String): String {
    val bytes = input.toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return Base64.UrlSafe.encode(digest)
}