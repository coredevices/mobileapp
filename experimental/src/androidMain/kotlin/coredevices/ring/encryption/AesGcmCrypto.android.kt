package coredevices.ring.encryption

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-CBC + HMAC-SHA256 (Encrypt-then-MAC).
 * Wire format: IV(16) || HMAC(32) || ciphertext.
 *
 * The 32-byte AES key is split: first 16 bytes for AES-256 encryption key,
 * but we use the full 32 bytes as AES key and derive a separate HMAC key via SHA-256.
 *
 * Actually: use the full 32 bytes as AES-256 key, derive HMAC key as SHA-256("hmac" || key).
 */
actual object AesGcmCrypto {
    private const val IV_LENGTH = 16 // AES block size
    private const val HMAC_LENGTH = 32

    actual fun encrypt(plaintext: ByteArray, keyBase64: String): ByteArray {
        val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)
        require(keyBytes.size == 32) { "AES-256 key must be 32 bytes, got ${keyBytes.size}" }

        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(plaintext)

        // HMAC-SHA256 over IV + ciphertext
        val hmacKey = deriveHmacKey(keyBytes)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        mac.update(iv)
        mac.update(ciphertext)
        val hmac = mac.doFinal()

        // IV(16) || HMAC(32) || ciphertext
        return iv + hmac + ciphertext
    }

    actual fun decrypt(ivAndCiphertext: ByteArray, keyBase64: String): ByteArray {
        require(ivAndCiphertext.size > IV_LENGTH + HMAC_LENGTH) {
            "Input too short to contain IV + HMAC + ciphertext"
        }
        val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)
        require(keyBytes.size == 32) { "AES-256 key must be 32 bytes, got ${keyBytes.size}" }

        val iv = ivAndCiphertext.copyOfRange(0, IV_LENGTH)
        val storedHmac = ivAndCiphertext.copyOfRange(IV_LENGTH, IV_LENGTH + HMAC_LENGTH)
        val ciphertext = ivAndCiphertext.copyOfRange(IV_LENGTH + HMAC_LENGTH, ivAndCiphertext.size)

        // Verify HMAC first (Encrypt-then-MAC: verify before decrypt)
        val hmacKey = deriveHmacKey(keyBytes)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        mac.update(iv)
        mac.update(ciphertext)
        val computedHmac = mac.doFinal()

        if (!MessageDigest.isEqual(storedHmac, computedHmac)) {
            throw TamperedException("Data integrity check failed — this recording may have been tampered with")
        }

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(ciphertext)
    }

    actual fun keyFingerprint(keyBase64: String): String {
        val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)
        val digest = MessageDigest.getInstance("SHA-256").digest(keyBytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun deriveHmacKey(aesKey: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update("hmac".toByteArray())
        md.update(aesKey)
        return md.digest()
    }
}
