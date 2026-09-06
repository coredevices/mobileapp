package coredevices.ring.external.indexwebhook

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA256

@OptIn(ExperimentalForeignApi::class)
internal actual fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
    val result = ByteArray(CC_SHA256_DIGEST_LENGTH)
    key.usePinned { keyPinned ->
        message.usePinned { messagePinned ->
            result.usePinned { resultPinned ->
                CCHmac(
                    kCCHmacAlgSHA256,
                    keyPinned.addressOf(0),
                    key.size.toULong(),
                    messagePinned.addressOf(0),
                    message.size.toULong(),
                    resultPinned.addressOf(0),
                )
            }
        }
    }
    return result
}
