package coredevices.coreapp

import coredevices.util.GoogleAuthUtil
import kotlin.random.Random

internal fun generateNonce(): String {
    val nonce = Random.nextBytes(32)
    return nonce.joinToString("") { it.toString(16) }
}

expect class RealGoogleAuthUtil : GoogleAuthUtil