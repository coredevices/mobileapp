@file:Suppress("CAST_NEVER_SUCCEEDS")

package io.rebble.libpebblecommon.storage

import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFAutorelease
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
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
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import kotlin.uuid.Uuid

/** Generic-password Keychain items under [service], one account per key. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainSecureStorage(private val service: String) : SecureStorage {

    override suspend fun set(key: String, value: String) {
        val data = NSString.create(value)!!.dataUsingEncoding(NSUTF8StringEncoding) ?: return

        val query = query(key)
        val updateAttrs = CFDictionaryCreateMutable(null, 1, null, null).also { dict ->
            CFDictionarySetValue(dict, kSecValueData, CFBridgingRetain(data))
            CFAutorelease(dict)
        }

        val updateStatus = SecItemUpdate(query, updateAttrs)
        if (updateStatus == errSecItemNotFound) {
            CFDictionarySetValue(query, kSecValueData, CFBridgingRetain(data))
            CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            val addStatus = SecItemAdd(query, null)
            check(addStatus == errSecSuccess) { "Keychain add failed: $addStatus" }
        } else {
            check(updateStatus == errSecSuccess) { "Keychain update failed: $updateStatus" }
        }
    }

    override suspend fun get(key: String): String? = memScoped {
        val query = query(key)
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)

        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)

        if (status == errSecItemNotFound) return null
        check(status == errSecSuccess) { "Keychain read failed: $status" }

        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        return NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
    }

    override suspend fun remove(key: String) = delete(query(key))

    override suspend fun destroy() = delete(query(account = null))

    private fun delete(query: CFMutableDictionaryRef?) {
        val status = SecItemDelete(query)
        check(status == errSecSuccess || status == errSecItemNotFound) { "Keychain delete failed: $status" }
    }

    private fun query(account: String?) =
        CFDictionaryCreateMutable(null, 4, null, null).also { dict ->
            CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(dict, kSecAttrService, CFBridgingRetain(service))
            account?.let { CFDictionarySetValue(dict, kSecAttrAccount, CFBridgingRetain(it)) }
            CFAutorelease(dict)
        }
}

internal actual fun pluginSecureStorage(appContext: AppContext, pluginUuid: Uuid): SecureStorage =
    KeychainSecureStorage("io.rebble.libpebble.plugin_secrets.$pluginUuid")
