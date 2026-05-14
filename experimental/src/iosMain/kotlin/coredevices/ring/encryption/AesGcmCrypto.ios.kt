@file:OptIn(ExperimentalForeignApi::class)

package coredevices.ring.encryption

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCAlgorithmAES128
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.posix.size_tVar

/**
 * AES-256-CBC + HMAC-SHA256 (Encrypt-then-MAC).
 * Wire format: IV(16) || HMAC(32) || ciphertext.
 */
actual object AesGcmCrypto {
    private const val IV_LENGTH = 16
    private const val HMAC_LENGTH = 32

    actual fun encrypt(plaintext: ByteArray, keyBase64: String): ByteArray {
        val keyBytes = kotlin.io.encoding.Base64.decode(keyBase64)
        require(keyBytes.size == 32) { "AES-256 key must be 32 bytes" }

        // Generate random IV
        val iv = ByteArray(IV_LENGTH)
        iv.usePinned { pinned ->
            val status = SecRandomCopyBytes(kSecRandomDefault, IV_LENGTH.toULong(), pinned.addressOf(0))
            check(status == 0) { "SecRandomCopyBytes failed: $status" }
        }

        // AES-256-CBC encrypt with PKCS7 padding
        // Output buffer: plaintext size + one block for padding
        val maxOutputSize = plaintext.size + 16
        val ciphertext = ByteArray(maxOutputSize)
        val actualSize = memScoped {
            val dataOutMoved = alloc<size_tVar>()
            val status = keyBytes.usePinned { keyPinned ->
                iv.usePinned { ivPinned ->
                    plaintext.usePinned { ptPinned ->
                        ciphertext.usePinned { ctPinned ->
                            CCCrypt(
                                kCCEncrypt,
                                kCCAlgorithmAES128, // kCCAlgorithmAES128 handles all AES key sizes (128/192/256)
                                kCCOptionPKCS7Padding,
                                keyPinned.addressOf(0), keyBytes.size.toULong(),
                                ivPinned.addressOf(0),
                                ptPinned.addressOf(0), plaintext.size.toULong(),
                                ctPinned.addressOf(0), maxOutputSize.toULong(),
                                dataOutMoved.ptr
                            )
                        }
                    }
                }
            }
            check(status == kCCSuccess) { "CCCrypt encrypt failed: $status" }
            dataOutMoved.value.toInt()
        }
        val trimmedCiphertext = ciphertext.copyOfRange(0, actualSize)

        // HMAC-SHA256 over IV + ciphertext
        val hmacKey = deriveHmacKey(keyBytes)
        val hmacInput = iv + trimmedCiphertext
        val hmac = computeHmac(hmacKey, hmacInput)

        // IV(16) || HMAC(32) || ciphertext
        return iv + hmac + trimmedCiphertext
    }

    actual fun decrypt(ivAndCiphertext: ByteArray, keyBase64: String): ByteArray {
        require(ivAndCiphertext.size > IV_LENGTH + HMAC_LENGTH) {
            "Input too short to contain IV + HMAC + ciphertext"
        }
        val keyBytes = kotlin.io.encoding.Base64.decode(keyBase64)
        require(keyBytes.size == 32) { "AES-256 key must be 32 bytes" }

        val iv = ivAndCiphertext.copyOfRange(0, IV_LENGTH)
        val storedHmac = ivAndCiphertext.copyOfRange(IV_LENGTH, IV_LENGTH + HMAC_LENGTH)
        val ciphertext = ivAndCiphertext.copyOfRange(IV_LENGTH + HMAC_LENGTH, ivAndCiphertext.size)

        // Verify HMAC first
        val hmacKey = deriveHmacKey(keyBytes)
        val hmacInput = iv + ciphertext
        val computedHmac = computeHmac(hmacKey, hmacInput)

        if (!constantTimeEquals(storedHmac, computedHmac)) {
            throw TamperedException("Data integrity check failed — this recording may have been tampered with")
        }

        // AES-256-CBC decrypt
        val plaintext = ByteArray(ciphertext.size)
        val actualSize = memScoped {
            val dataOutMoved = alloc<size_tVar>()
            val status = keyBytes.usePinned { keyPinned ->
                iv.usePinned { ivPinned ->
                    ciphertext.usePinned { ctPinned ->
                        plaintext.usePinned { ptPinned ->
                            CCCrypt(
                                kCCDecrypt,
                                kCCAlgorithmAES128,
                                kCCOptionPKCS7Padding,
                                keyPinned.addressOf(0), keyBytes.size.toULong(),
                                ivPinned.addressOf(0),
                                ctPinned.addressOf(0), ciphertext.size.toULong(),
                                ptPinned.addressOf(0), plaintext.size.toULong(),
                                dataOutMoved.ptr
                            )
                        }
                    }
                }
            }
            check(status == kCCSuccess) { "CCCrypt decrypt failed: $status" }
            dataOutMoved.value.toInt()
        }
        return plaintext.copyOfRange(0, actualSize)
    }

    actual fun keyFingerprint(keyBase64: String): String {
        val keyBytes = kotlin.io.encoding.Base64.decode(keyBase64)
        return sha256Hex(keyBytes)
    }

    private fun sha256(input: ByteArray): ByteArray {
        val uInput = input.toUByteArray()
        val digest = UByteArray(32)
        uInput.usePinned { inputPinned ->
            digest.usePinned { outputPinned ->
                CC_SHA256(inputPinned.addressOf(0), uInput.size.toUInt(), outputPinned.addressOf(0))
            }
        }
        return digest.toByteArray()
    }

    private fun sha256Hex(input: ByteArray): String {
        return sha256(input).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private fun deriveHmacKey(aesKey: ByteArray): ByteArray {
        val prefix = "hmac".encodeToByteArray()
        return sha256(prefix + aesKey)
    }

    private fun computeHmac(key: ByteArray, data: ByteArray): ByteArray {
        val hmac = ByteArray(HMAC_LENGTH)
        key.usePinned { keyPinned ->
            data.usePinned { dataPinned ->
                hmac.usePinned { hmacPinned ->
                    CCHmac(
                        kCCHmacAlgSHA256,
                        keyPinned.addressOf(0), key.size.toULong(),
                        dataPinned.addressOf(0), data.size.toULong(),
                        hmacPinned.addressOf(0)
                    )
                }
            }
        }
        return hmac
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}
