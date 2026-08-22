package coredevices.ring.external.indexwebhook

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal actual fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray =
    Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(message)
    }
