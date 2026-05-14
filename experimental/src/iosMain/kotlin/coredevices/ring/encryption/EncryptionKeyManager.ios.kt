@file:Suppress("CAST_NEVER_SUCCEEDS")

package coredevices.ring.encryption

import PlatformUiContext
import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.serialization.json.Json
import platform.CoreCrypto.CC_SHA256
import platform.CoreFoundation.CFAutorelease
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemUpdate
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class EncryptionKeyManager {
    private val logger = Logger.withTag("EncryptionKeyManager")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    actual fun generateKey(): KeyResult {
        val keyBytes = ByteArray(32)
        keyBytes.usePinned { pinned ->
            val status = SecRandomCopyBytes(kSecRandomDefault, 32u, pinned.addressOf(0))
            check(status == 0) { "SecRandomCopyBytes failed: $status" }
        }
        val keyBase64 = kotlin.io.encoding.Base64.encode(keyBytes)
        val fingerprint = sha256Hex(keyBytes)
        return KeyResult(
            keyBase64 = keyBase64,
            fingerprint = fingerprint,
        )
    }

    actual suspend fun saveKeyLocally(key: String, email: String) {
        val fingerprint = AesGcmCrypto.keyFingerprint(key)
        val entry = StoredKeyEntry(email = email, keyBase64 = key, fingerprint = fingerprint)

        // Load existing entries, replace or add for this email
        val entries = loadEntries(LOCAL_SERVICE, LOCAL_ACCOUNT, synchronizable = false).toMutableList()
        entries.removeAll { it.email == email }
        entries.add(entry)
        saveEntries(LOCAL_SERVICE, LOCAL_ACCOUNT, entries, synchronizable = false)

        logger.i { "Key saved locally for $email (${entries.size} total entries)" }
    }

    actual suspend fun getLocalKey(email: String?): String? {
        val entries = loadEntries(LOCAL_SERVICE, LOCAL_ACCOUNT, synchronizable = false)
        if (entries.isEmpty()) return null
        return if (email != null) {
            entries.find { it.email == email }?.keyBase64
        } else {
            entries.firstOrNull()?.keyBase64
        }
    }

    actual suspend fun getStoredKeyEntries(): List<StoredKeyEntry> {
        return loadEntries(LOCAL_SERVICE, LOCAL_ACCOUNT, synchronizable = false)
    }

    actual suspend fun saveToCloudKeychain(uiContext: PlatformUiContext, key: String) {
        try {
            saveToKeychain(CLOUD_SERVICE, CLOUD_ACCOUNT, key, synchronizable = true)
            logger.i { "Key saved to iCloud Keychain" }
        } catch (e: Exception) {
            throw Exception("Could not save to iCloud Keychain. Make sure iCloud Keychain is enabled in Settings > Apple ID > iCloud > Passwords and Keychain.")
        }
    }

    actual suspend fun readFromCloudKeychain(uiContext: PlatformUiContext): String? {
        val key = readFromKeychain(CLOUD_SERVICE, CLOUD_ACCOUNT, synchronizable = true)
        if (key == null) {
            throw Exception("No encryption key found in iCloud Keychain. Make sure iCloud Keychain is enabled and synced, or generate a new key.")
        }
        return key
    }

    // --- Entries storage (JSON array in Keychain) ---

    private fun loadEntries(service: String, account: String, synchronizable: Boolean): List<StoredKeyEntry> {
        val raw = readFromKeychain(service, account, synchronizable) ?: return emptyList()
        return try {
            json.decodeFromString<List<StoredKeyEntry>>(raw)
        } catch (e: Exception) {
            logger.w(e) { "Failed to parse key entries, treating as empty" }
            emptyList()
        }
    }

    private fun saveEntries(service: String, account: String, entries: List<StoredKeyEntry>, synchronizable: Boolean) {
        val jsonStr = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(StoredKeyEntry.serializer()),
            entries
        )
        saveToKeychain(service, account, jsonStr, synchronizable)
    }

    // --- Keychain helpers ---

    private fun saveToKeychain(service: String, account: String, value: String, synchronizable: Boolean) {
        val tokenData = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: throw Exception("Failed to encode key data")
        val query = baseQuery(service, account, synchronizable)
        val updateAttrs = CFDictionaryCreateMutable(null, 1, null, null).also { dict ->
            CFDictionarySetValue(dict, kSecValueData, CFBridgingRetain(tokenData))
            CFAutorelease(dict)
        }
        val updateStatus = SecItemUpdate(query, updateAttrs)
        if (updateStatus == errSecItemNotFound) {
            CFDictionarySetValue(query, kSecValueData, CFBridgingRetain(tokenData))
            val accessible = if (synchronizable) kSecAttrAccessibleAfterFirstUnlock
                else kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            CFDictionarySetValue(query, kSecAttrAccessible, accessible)
            val addStatus = SecItemAdd(query, null)
            check(addStatus == errSecSuccess) { "Keychain add failed: $addStatus" }
        } else {
            check(updateStatus == errSecSuccess) { "Keychain update failed: $updateStatus" }
        }
    }

    private fun readFromKeychain(service: String, account: String, synchronizable: Boolean): String? = memScoped {
        val query = baseQuery(service, account, synchronizable)
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        if (status == errSecItemNotFound) return null
        check(status == errSecSuccess) { "Keychain read failed: $status" }
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        return NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
    }

    private fun baseQuery(service: String, account: String, synchronizable: Boolean) =
        CFDictionaryCreateMutable(null, 5, null, null).also { dict ->
            CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(dict, kSecAttrService, CFBridgingRetain(service))
            CFDictionarySetValue(dict, kSecAttrAccount, CFBridgingRetain(account))
            CFDictionarySetValue(dict, kSecAttrSynchronizable, if (synchronizable) kCFBooleanTrue else kCFBooleanFalse)
            CFAutorelease(dict)
        }

    private fun sha256Hex(bytes: ByteArray): String {
        val input = bytes.toUByteArray()
        val digest = UByteArray(32)
        input.usePinned { inputPinned ->
            digest.usePinned { outputPinned ->
                CC_SHA256(inputPinned.addressOf(0), input.size.toUInt(), outputPinned.addressOf(0))
            }
        }
        return digest.joinToString("") { it.toInt().toString(16).padStart(2, '0') }
    }

    companion object {
        private const val LOCAL_SERVICE = "coredevices.encryption_keys"
        private const val LOCAL_ACCOUNT = "key_entries"
        private const val CLOUD_SERVICE = "coredevices.encryption_keys.cloud"
        private const val CLOUD_ACCOUNT = "Pebble Index Private Key"
    }
}
