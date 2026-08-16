@file:OptIn(kotlin.time.ExperimentalTime::class)

package coredevices.coreapp.ring.encryption

import androidx.test.platform.app.InstrumentationRegistry
import coredevices.indexai.data.entity.ItemDocument
import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.indexai.data.entity.ListDocument
import coredevices.ring.encryption.DocumentEncryptor
import coredevices.ring.encryption.EncryptionKeyManager
import coredevices.ring.encryption.KeyFingerprintMismatchException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Verifies the item/list encryption added for the RING privacy fix: sensitive
 * content (title/body/dueAt/metadata) is encrypted into an envelope and the
 * structural fields stay cleartext, and decrypt restores the original. Runs as
 * an instrumented test because [coredevices.ring.encryption.AesCbcHmacCrypto]'s
 * Android actual uses `android.util.Base64` (unavailable on the host JVM) — same
 * reason RecordingProcessingQueueTest is instrumented.
 */
class DocumentEncryptorItemsTest {

    private val encryptor: DocumentEncryptor by lazy {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        DocumentEncryptor(EncryptionKeyManager(ctx))
    }

    private fun newKey(): String {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        return EncryptionKeyManager(ctx).generateKey().keyBase64
    }

    @Test
    fun encryptItem_blanksContent_keepsStructure() {
        val key = newKey()
        val item = sampleItem()

        val enc = encryptor.encryptItem(item, key)

        // Sensitive fields blanked, envelope populated.
        assertEquals("", enc.title)
        assertEquals("", enc.body)
        assertNull(enc.dueAt)
        assertEquals(ItemMetadata.Note, enc.metadata)
        assertNotNull(enc.encrypted)
        // Structural fields untouched (sync/grouping/dedup rely on them).
        assertEquals(item.updatedAt, enc.updatedAt)
        assertEquals(item.createdAt, enc.createdAt)
        assertEquals(item.done, enc.done)
        assertEquals(item.parentListIds, enc.parentListIds)
        assertEquals(item.sourceRecordingId, enc.sourceRecordingId)
        assertEquals(item.sourceToolCallId, enc.sourceToolCallId)
        assertEquals(item.deleted, enc.deleted)
    }

    @Test
    fun item_roundTrip_restoresAllSensitiveFields() {
        val key = newKey()
        val item = sampleItem()

        val restored = encryptor.decryptItem(encryptor.encryptItem(item, key), key)

        assertNull(restored.encrypted)
        assertEquals(item.title, restored.title)
        assertEquals(item.body, restored.body)
        assertEquals(item.dueAt, restored.dueAt)
        assertEquals(item.metadata, restored.metadata) // polymorphic Message survives
    }

    @Test
    fun list_roundTrip_restoresTitle() {
        val key = newKey()
        val list = ListDocument(title = "Therapy notes", icon = "🧠", seed = null)

        val enc = encryptor.encryptList(list, key)
        assertEquals("", enc.title)
        assertNotNull(enc.encrypted)
        assertEquals(list.icon, enc.icon) // icon stays cleartext

        val restored = encryptor.decryptList(enc, key)
        assertNull(restored.encrypted)
        assertEquals("Therapy notes", restored.title)
    }

    @Test
    fun decryptItem_withWrongKey_throwsFingerprintMismatch() {
        val keyA = newKey()
        val keyB = newKey()
        val enc = encryptor.encryptItem(sampleItem(), keyA)

        assertFailsWith<KeyFingerprintMismatchException> {
            encryptor.decryptItem(enc, keyB)
        }
    }

    @Test
    fun decryptItem_cleartextDoc_isNoOp() {
        val key = newKey()
        val plain = sampleItem() // encrypted == null
        assertEquals(plain, encryptor.decryptItem(plain, key))
    }

    private fun sampleItem(): ItemDocument = ItemDocument(
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
        updatedAt = Instant.fromEpochMilliseconds(1_700_000_500_000),
        title = "Text Bob about the surprise party",
        body = "don't let him see this",
        done = false,
        dueAt = Instant.fromEpochMilliseconds(1_700_100_000_000),
        parentListIds = listOf("list_todos"),
        sourceRecordingId = "tSf9wtb5PDB3PVAD6r99",
        sourceToolCallId = "call_123",
        metadata = ItemMetadata.Message(
            integration = "beeper",
            contact = "@bob:server",
            recipientName = "Bob",
            text = "secret message text",
            sentAt = Instant.fromEpochMilliseconds(1_700_000_400_000),
            status = ItemMetadata.Message.Status.Sent,
        ),
        deleted = false,
    )
}
