package coredevices.util

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

actual fun sha256(bytes: ByteArray): ByteArray {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    digest.usePinned { pinned ->
        CC_SHA256(bytes.refTo(0), bytes.size.toUInt(), pinned.addressOf(0))
    }
    return digest.asByteArray()
}
