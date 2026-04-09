package coredevices.coreapp.auth

import coredevices.util.auth.GoogleAuthUtil
import kotlin.random.Random

@OptIn(ExperimentalUnsignedTypes::class)
internal fun generateNonce(): String {
    val nonce = Random.nextBytes(32).toUByteArray()
    return nonce.joinToString("") { it.toString(16) }
}

expect class RealGoogleAuthUtil : GoogleAuthUtil