package coredevices.coreapp.auth

import PlatformUiContext
import co.touchlab.kermit.Logger
import cocoapods.FirebaseAuth.FIROAuthProvider
import coredevices.util.auth.AppleAuthUtil
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.OAuthCredential
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import okio.ByteString.Companion.toByteString
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationAppleIDRequest
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSError
import platform.darwin.NSObject

actual class RealAppleAuthUtil : AppleAuthUtil {
    companion object {
        private val logger = Logger.withTag("RealAppleAuthUtil")
    }

    private fun performAuthRequest(request: ASAuthorizationAppleIDRequest, nonce: String) =
        callbackFlow {
            val delegate = object : NSObject(), ASAuthorizationControllerDelegateProtocol {
                override fun authorizationController(
                    controller: ASAuthorizationController,
                    didCompleteWithAuthorization: ASAuthorization
                ) {
                    val appleIDCredential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
                    trySend(appleIDCredential)
                }

                override fun authorizationController(
                    controller: ASAuthorizationController,
                    didCompleteWithError: NSError
                ) {
                    close(Exception(didCompleteWithError.localizedDescription))
                }
            }
            val authorizationController = ASAuthorizationController(listOf(request))
            authorizationController.delegate = delegate
            authorizationController.performRequests()
            awaitClose { authorizationController.delegate = null }
        }

    actual override suspend fun signInApple(context: PlatformUiContext): AuthCredential? {
        val nonce = generateNonce()
        val inputData = nonce.toByteArray(Charsets.UTF_8)
        val hashedData = UByteArray(CC_SHA256_DIGEST_LENGTH)
        hashedData.usePinned {
            CC_SHA256(inputData.refTo(0), inputData.size.toUInt(), it.addressOf(0))
        }
        val hashString = hashedData.toHexString(HexFormat.Default)

        val appleIDProvider = ASAuthorizationAppleIDProvider()
        val request = appleIDProvider.createRequest().apply {
            requestedScopes = listOf(ASAuthorizationScopeEmail, ASAuthorizationScopeFullName)
            setNonce(hashString)
        }
        return try {
            val nativeCred = performAuthRequest(request, nonce).first()
            val idToken = nativeCred?.identityToken ?: throw IllegalStateException("Cancelled or failed")
            val name = nativeCred?.fullName
            OAuthCredential(FIROAuthProvider.appleCredentialWithIDToken(idToken.toByteString().utf8(), nonce, name))
        } catch (e: Exception) {
            logger.e(e) { "Apple sign-in failed: ${e.message}" }
            null
        }
    }
}