package coredevices.util.integrations

import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.posix.arc4random_uniform
import kotlin.io.encoding.Base64

internal actual fun generateSecureRandomString(length: Int, charset: List<Char>): String {
    return buildString {
        for (i in 0 until length) {
            val randomIndex = arc4random_uniform(charset.size.toUInt()).toInt()
            append(charset[randomIndex])
        }
    }
}

internal actual fun sha256(input: String): String {
    val data = input.encodeToByteArray()
    val hash = CC_SHA256(data.refTo(0), data.size.toUInt(), null)
    return hash?.readBytes(CC_SHA256_DIGEST_LENGTH)?.let { Base64.UrlSafe.encode(it) } ?: throw IllegalStateException("SHA-256 hashing failed")
}