package coredevices.ring.external.indexwebhook

import coredevices.ring.service.button.RingGesture
import coredevices.util.integrations.IntegrationTokenStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IndexWebhookSigningTest {

    @Test
    fun hmacSha256MatchesRfc4231TestVector() {
        val digest = hmacSha256(
            key = ByteArray(20) { 0x0b },
            message = "Hi There".encodeToByteArray(),
        )

        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            digest.toHex(),
        )
    }

    @Test
    fun signatureInputContainsVersionedMetadataThenExactBodyBytes() {
        val body = byteArrayOf(0, 1, 2, -1)

        val input = buildWebhookSignatureInput(
            timestamp = 1_700_000_000,
            deliveryId = "delivery-123",
            triggerValue = "single-click-hold",
            isTest = false,
            body = body,
        )

        assertContentEquals(
            "v1\n1700000000\ndelivery-123\nsingle-click-hold\n0\n".encodeToByteArray() + body,
            input,
        )
    }

    @Test
    fun signatureChangesWhenBodyOrMetadataChanges() {
        fun signature(body: ByteArray, trigger: String = "single-click-hold") =
            createWebhookSignature(
                secret = "0123456789abcdef0123456789abcdef",
                timestamp = 1_700_000_000,
                deliveryId = "delivery-123",
                triggerValue = trigger,
                isTest = false,
                body = body,
            )

        val original = signature(byteArrayOf(1, 2, 3))

        assertNotEquals(original, signature(byteArrayOf(1, 2, 4)))
        assertNotEquals(original, signature(byteArrayOf(1, 2, 3), "double-click-hold"))
    }

    @Test
    fun signatureHeaderValueIsRawLowercaseHex() {
        val signature = createWebhookSignature(
            secret = "0123456789abcdef0123456789abcdef",
            timestamp = 1_700_000_000,
            deliveryId = "delivery-123",
            triggerValue = "single-click-hold",
            isTest = false,
            body = byteArrayOf(1, 2, 3),
        )

        assertEquals(64, signature.length)
        assertTrue(signature.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun reservedHeadersCannotBeOverriddenCaseInsensitively() {
        val headers = mapOf(
            "Authorization" to "Bearer abc",
            "x-index-signature" to "forged",
            "X-INDEX-TIMESTAMP" to "0",
            "X-Index-Delivery" to "forged",
            "x-index-webhook-version" to "999",
            "x-index-trigger" to "forged",
            "X-Index-Test" to "false",
            "x-audio-size" to "999",
        )

        val filtered = headers.withoutReservedWebhookHeaders()

        assertEquals(mapOf("Authorization" to "Bearer abc"), filtered)
        RESERVED_WEBHOOK_HEADERS.forEach { reserved ->
            assertFalse(filtered.keys.any { it.equals(reserved, ignoreCase = true) })
        }
    }

    @Test
    fun signingSecretsAreStoredSeparatelyForEachGesture() = runTest {
        val tokens = MemoryTokenStorage()
        val storage = IndexWebhookSigningSecretStorage(tokens)

        storage.save(RingGesture.Hold, "hold-secret")
        storage.save(RingGesture.ClickHold, "click-hold-secret")

        assertEquals("hold-secret", storage.get(RingGesture.Hold))
        assertEquals("click-hold-secret", storage.get(RingGesture.ClickHold))

        storage.delete(RingGesture.Hold)

        assertEquals(null, storage.get(RingGesture.Hold))
        assertEquals("click-hold-secret", storage.get(RingGesture.ClickHold))
    }

    private fun ByteArray.toHex(): String = joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private class MemoryTokenStorage : IntegrationTokenStorage {
        private val values = mutableMapOf<String, String>()

        override suspend fun saveToken(key: String, token: String) {
            values[key] = token
        }

        override suspend fun getToken(key: String): String? = values[key]

        override suspend fun deleteToken(key: String) {
            values.remove(key)
        }
    }
}
