@file:Suppress("CAST_NEVER_SUCCEEDS")

package coredevices.ring.database

import coredevices.util.integrations.IntegrationTokenStorage
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFAutorelease
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
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

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class IntegrationTokenStorageImpl : IntegrationTokenStorage {

    actual override suspend fun saveToken(key: String, token: String) {
        val tokenData = NSString.create(token)!!.dataUsingEncoding(NSUTF8StringEncoding) ?: return

        // Try updating first; if item doesn't exist, add it
        val query = baseQuery(key)
        val updateAttrs = CFDictionaryCreateMutable(null, 1, null, null).also { dict ->
            CFDictionarySetValue(dict, kSecValueData, CFBridgingRetain(tokenData))
            CFAutorelease(dict)
        }

        val updateStatus = SecItemUpdate(query, updateAttrs)
        if (updateStatus == errSecItemNotFound) {
            CFDictionarySetValue(query, kSecValueData, CFBridgingRetain(tokenData))
            CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            val addStatus = SecItemAdd(query, null)
            check(addStatus == errSecSuccess) { "Keychain add failed: $addStatus" }
        } else {
            check(updateStatus == errSecSuccess) { "Keychain update failed: $updateStatus" }
        }
    }

    actual override suspend fun getToken(key: String): String? = memScoped {
        val query = baseQuery(key)
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)

        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)

        if (status == errSecItemNotFound) return null
        check(status == errSecSuccess) { "Keychain read failed: $status" }

        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        return NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
    }

    actual override suspend fun deleteToken(key: String) {
        val query = baseQuery(key)
        val status = SecItemDelete(query)
        if (status != errSecSuccess && status != errSecItemNotFound) {
            error("Keychain delete failed: $status")
        }
    }

    private fun baseQuery(key: String) =
        CFDictionaryCreateMutable(null, 4, null, null).also { dict ->
            CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(dict, kSecAttrService, CFBridgingRetain(SERVICE_NAME))
            CFDictionarySetValue(dict, kSecAttrAccount, CFBridgingRetain(key))
            CFAutorelease(dict)
        }

    companion object {
        private const val SERVICE_NAME = "coredevices.integration_tokens"
    }
}