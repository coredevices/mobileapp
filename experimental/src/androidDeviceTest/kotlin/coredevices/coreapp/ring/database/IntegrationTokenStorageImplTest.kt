package coredevices.coreapp.ring.database

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import coredevices.ring.database.IntegrationTokenStorageImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class IntegrationTokenStorageImplTest {

    private lateinit var context: Context
    private lateinit var storage: IntegrationTokenStorageImpl

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Clear any leftover prefs from previous test runs
        context.applicationContext
            .getSharedPreferences("integration_tokens", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        storage = IntegrationTokenStorageImpl(context)
        // Ensure the encryption key is cleared before each test to avoid cross-test contamination
        storage.clearKey()
    }

    @After
    fun tearDown() {
        context.applicationContext
            .getSharedPreferences("integration_tokens", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun saveAndRetrieveToken() = runBlocking {
        val token = "oauth2_test_token_abc123"
        storage.saveToken("notion", token)

        val retrieved = storage.getToken("notion")
        assertEquals(token, retrieved)
    }

    @Test
    fun getTokenReturnsNullWhenMissing() = runBlocking {
        val result = storage.getToken("nonexistent_key")
        assertNull(result)
    }

    @Test
    fun deleteTokenRemovesEntry() = runBlocking {
        storage.saveToken("notion", "some_token")
        storage.deleteToken("notion")

        val result = storage.getToken("notion")
        assertNull(result)
    }

    @Test
    fun overwriteTokenWithNewValue() = runBlocking {
        storage.saveToken("notion", "old_token")
        storage.saveToken("notion", "new_token")

        assertEquals("new_token", storage.getToken("notion"))
    }

    @Test
    fun multipleKeysAreIndependent() = runBlocking {
        storage.saveToken("notion", "notion_token")
        storage.saveToken("google", "google_token")

        assertEquals("notion_token", storage.getToken("notion"))
        assertEquals("google_token", storage.getToken("google"))

        storage.deleteToken("notion")
        assertNull(storage.getToken("notion"))
        assertEquals("google_token", storage.getToken("google"))
    }

    @Test
    fun storedValueIsNotPlaintext() = runBlocking {
        val token = "super_secret_oauth_token_12345"
        storage.saveToken("notion", token)

        // Read the raw SharedPreferences value
        val prefs = context.applicationContext
            .getSharedPreferences("integration_tokens", Context.MODE_PRIVATE)
        val rawValue = prefs.getString("notion", null)

        // The raw stored value must not be the plaintext token
        assertNotEquals(
            "Token was stored in plaintext!",
            token,
            rawValue
        )
        // The raw value must not contain the plaintext token as a substring
        assertNotEquals(
            "Raw stored value contains the plaintext token!",
            true,
            rawValue?.contains(token)
        )
    }

    @Test
    fun storedValueDoesNotContainPlaintextSubstring() = runBlocking {
        // Use a recognizable token with a long unique substring
        val token = "xoxb-UNIQUE_PLAINTEXT_MARKER_999"
        storage.saveToken("slack", token)

        val prefs = context.applicationContext
            .getSharedPreferences("integration_tokens", Context.MODE_PRIVATE)
        val rawValue = prefs.getString("slack", null)!!

        // Ensure no recognizable substring of the token leaks into storage
        assertNotEquals(
            "Plaintext marker found in stored value!",
            true,
            rawValue.contains("UNIQUE_PLAINTEXT_MARKER")
        )
    }

    @Test
    fun differentEncryptionsOfSameTokenProduceDifferentCiphertext() = runBlocking {
        // GCM uses a random IV each time, so encrypting the same value twice
        // should produce different ciphertext (no deterministic encryption)
        val token = "same_token_value"
        storage.saveToken("key1", token)
        storage.saveToken("key2", token)

        val prefs = context.applicationContext
            .getSharedPreferences("integration_tokens", Context.MODE_PRIVATE)
        val raw1 = prefs.getString("key1", null)
        val raw2 = prefs.getString("key2", null)

        assertNotEquals(
            "Same token encrypted to identical ciphertext — IV may not be random",
            raw1,
            raw2
        )

        // Both should still decrypt to the same value
        assertEquals(token, storage.getToken("key1"))
        assertEquals(token, storage.getToken("key2"))
    }

    @Test
    fun corruptedCiphertextReturnsNullAndCleansUp() = runBlocking {
        // Write garbage directly to SharedPreferences
        context.applicationContext
            .getSharedPreferences("integration_tokens", Context.MODE_PRIVATE)
            .edit()
            .putString("corrupted", "not_valid_base64_ciphertext!!")
            .commit()

        // Should return null instead of crashing
        val result = storage.getToken("corrupted")
        assertNull(result)

        // The corrupted entry should be cleaned up
        val prefs = context.applicationContext
            .getSharedPreferences("integration_tokens", Context.MODE_PRIVATE)
        assertNull(prefs.getString("corrupted", null))
    }
}