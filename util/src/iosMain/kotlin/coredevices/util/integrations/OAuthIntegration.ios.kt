package coredevices.util.integrations

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
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
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    digest.usePinned { pinned ->
        CC_SHA256(data.refTo(0), data.size.toUInt(), pinned.addressOf(0))
    }
    return Base64.UrlSafe.encode(digest.asByteArray())
}