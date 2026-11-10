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
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationErrorCanceled
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.AuthenticationServices.ASPresentationAnchor
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSError
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

private class AppleSignInException(val code: Long, description: String) :
    Exception("$description (Apple error $code)")

actual class RealAppleAuthUtil : AppleAuthUtil {
    companion object {
        private val logger = Logger.withTag("RealAppleAuthUtil")
    }

    private fun performAuthRequest(request: ASAuthorizationAppleIDRequest, nonce: String) =
        callbackFlow {
            val delegate = object :
                NSObject(),
                ASAuthorizationControllerDelegateProtocol,
                ASAuthorizationControllerPresentationContextProvidingProtocol {
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
                    close(AppleSignInException(didCompleteWithError.code, didCompleteWithError.localizedDescription))
                }

                override fun presentationAnchorForAuthorizationController(
                    controller: ASAuthorizationController
                ): ASPresentationAnchor =
                    UIApplication.sharedApplication.keyWindow
                        ?: UIApplication.sharedApplication.windows.firstOrNull() as? UIWindow
                        ?: UIWindow()
            }
            val authorizationController = ASAuthorizationController(listOf(request))
            // ASAuthorizationController references its delegate and presentation context
            // provider weakly, so this is the only strong reference keeping them alive
            // until the callback fires.
            var strongDelegate: NSObject? = delegate
            authorizationController.delegate = delegate
            authorizationController.presentationContextProvider = delegate
            authorizationController.performRequests()
            awaitClose {
                authorizationController.delegate = null
                authorizationController.presentationContextProvider = null
                strongDelegate = null
            }
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
        val nativeCred = try {
            performAuthRequest(request, nonce).first()
        } catch (e: AppleSignInException) {
            if (e.code == ASAuthorizationErrorCanceled) {
                logger.i { "Apple sign-in cancelled" }
                return null
            }
            throw e
        }
        val idToken = nativeCred?.identityToken ?: throw IllegalStateException("Apple didn't return a sign-in token")
        return OAuthCredential(FIROAuthProvider.appleCredentialWithIDToken(idToken.toByteString().utf8(), nonce, nativeCred.fullName))
    }
}