package coredevices.ring.external.indexwebhook

import coredevices.ring.service.button.RingGesture
import coredevices.util.integrations.IntegrationTokenStorage

internal const val WEBHOOK_SIGNATURE_HEADER = "X-Index-Signature"
internal const val WEBHOOK_TIMESTAMP_HEADER = "X-Index-Timestamp"
internal const val WEBHOOK_DELIVERY_HEADER = "X-Index-Delivery"

internal val RESERVED_WEBHOOK_HEADERS = setOf(
    WEBHOOK_AUDIO_SIZE_HEADER,
    WEBHOOK_TRIGGER_HEADER,
    WEBHOOK_TEST_HEADER,
    WEBHOOK_SIGNATURE_HEADER,
    WEBHOOK_TIMESTAMP_HEADER,
    WEBHOOK_DELIVERY_HEADER,
    WEBHOOK_VERSION_HEADER,
)

internal fun Map<String, String>.withoutReservedWebhookHeaders(): Map<String, String> =
    filterKeys { name -> RESERVED_WEBHOOK_HEADERS.none { it.equals(name, ignoreCase = true) } }

internal fun buildWebhookSignatureInput(
    timestamp: Long,
    deliveryId: String,
    triggerValue: String,
    isTest: Boolean,
    body: ByteArray,
): ByteArray = buildString {
    append('v')
    append(WEBHOOK_VERSION)
    append('\n')
    append(timestamp)
    append('\n')
    append(deliveryId)
    append('\n')
    append(triggerValue)
    append('\n')
    append(if (isTest) '1' else '0')
    append('\n')
}.encodeToByteArray() + body

internal fun createWebhookSignature(
    secret: String,
    timestamp: Long,
    deliveryId: String,
    triggerValue: String,
    isTest: Boolean,
    body: ByteArray,
): String {
    val signature = hmacSha256(
        key = secret.encodeToByteArray(),
        message = buildWebhookSignatureInput(timestamp, deliveryId, triggerValue, isTest, body),
    )
    return signature.joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}

internal expect fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray

class IndexWebhookSigningSecretStorage(
    private val tokenStorage: IntegrationTokenStorage,
) {
    suspend fun get(gesture: RingGesture): String? = tokenStorage.getToken(key(gesture))

    suspend fun save(gesture: RingGesture, secret: String) {
        tokenStorage.saveToken(key(gesture), secret)
    }

    suspend fun delete(gesture: RingGesture) {
        tokenStorage.deleteToken(key(gesture))
    }

    private fun key(gesture: RingGesture) = "index_webhook_signing_secret_${gesture.name}"
}
