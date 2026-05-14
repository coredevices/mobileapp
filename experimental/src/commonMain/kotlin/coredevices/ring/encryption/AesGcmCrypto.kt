package coredevices.ring.encryption

class TamperedException(message: String) : Exception(message)

/**
 * AES-256 authenticated encryption.
 *
 * Uses AES-256-CBC + HMAC-SHA256 (Encrypt-then-MAC) for cross-platform compatibility.
 * Wire format: IV(16) || HMAC(32) || AES-CBC-ciphertext (PKCS7 padded).
 * HMAC covers IV + ciphertext.
 */
expect object AesGcmCrypto {
    /** Encrypt plaintext. Returns IV(16) || HMAC(32) || ciphertext. */
    fun encrypt(plaintext: ByteArray, keyBase64: String): ByteArray

    /** Decrypt data produced by [encrypt]. Throws [TamperedException] on auth failure. */
    fun decrypt(ivAndCiphertext: ByteArray, keyBase64: String): ByteArray

    /** SHA-256 hex fingerprint of the raw key bytes. */
    fun keyFingerprint(keyBase64: String): String
}
