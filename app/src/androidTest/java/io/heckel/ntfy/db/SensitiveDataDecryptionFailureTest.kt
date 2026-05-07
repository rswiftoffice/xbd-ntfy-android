package io.heckel.ntfy.db

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SensitiveDataDecryptionFailureTest {

    @Test
    fun decrypt_throws_when_ciphertext_cannot_be_decrypted() {
        try {
            SensitiveDataCipher.decrypt(corruptCiphertext())
            fail("Expected SensitiveDataDecryptionException")
        } catch (e: SensitiveDataDecryptionException) {
            assertNotNull(e.cause)
        }
    }

    @Test
    fun decrypt_throws_when_payload_too_short() {
        // A truncated payload (under the 12-byte IV) should be flagged, not silently passed through.
        val truncated = "enc_v1:" + Base64.encodeToString(ByteArray(8) { 0 }, Base64.NO_WRAP)
        try {
            SensitiveDataCipher.decrypt(truncated)
            fail("Expected SensitiveDataDecryptionException")
        } catch (e: SensitiveDataDecryptionException) {
            assertNotNull(e.cause)
        }
    }

    @Test
    fun decrypt_passesThrough_legacy_plaintext() {
        // Non-prefixed values are treated as legacy plaintext and returned unchanged.
        assertEquals("legacy", SensitiveDataCipher.decrypt("legacy"))
        assertEquals("", SensitiveDataCipher.decrypt(""))
    }

    @Test
    fun getUser_returnsNull_andDeletesOrphanRow_onDecryptFailure() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = Repository.getInstance(context)
        val database = Database.getInstance(context)
        val baseUrl = "https://decrypt-failure-getuser-test.local"

        // Plant an unreadable row that simulates a Keystore key mismatch (e.g. backup/restore).
        repository.deleteUser(baseUrl)
        database.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO User (baseUrl, username, password) VALUES (?, ?, ?)",
            arrayOf(baseUrl, corruptCiphertext(), corruptCiphertext())
        )

        // Caller sees the orphan as "no credentials" rather than getting ciphertext as username.
        assertNull(repository.getUser(baseUrl))

        // The orphan row was cleaned up so the next addUser() can succeed without a UNIQUE collision.
        database.openHelper.readableDatabase
            .query("SELECT 1 FROM User WHERE baseUrl = ?", arrayOf(baseUrl)).use { cursor ->
                assertFalse("Orphan row should have been deleted", cursor.moveToFirst())
            }
        repository.addUser(User(baseUrl, "alice", "hunter2"))
        assertEquals("alice", repository.getUser(baseUrl)?.username)

        repository.deleteUser(baseUrl)
    }

    @Test
    fun getUsers_skipsUnreadableRows_withoutDeleting() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = Repository.getInstance(context)
        val database = Database.getInstance(context)

        val goodBaseUrl = "https://decrypt-failure-list-good.local"
        val badBaseUrl = "https://decrypt-failure-list-bad.local"

        repository.deleteUser(goodBaseUrl)
        repository.deleteUser(badBaseUrl)

        repository.addUser(User(goodBaseUrl, "alice", "hunter2"))
        database.openHelper.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO User (baseUrl, username, password) VALUES (?, ?, ?)",
            arrayOf(badBaseUrl, corruptCiphertext(), corruptCiphertext())
        )

        val users = repository.getUsers()
        assertEquals(1, users.count { it.baseUrl == goodBaseUrl })
        assertEquals(0, users.count { it.baseUrl == badBaseUrl })

        // List read must NOT delete unreadable rows — Backuper.createUserList() uses this method
        // and side-effecting deletes during a backup export would be surprising.
        database.openHelper.readableDatabase
            .query("SELECT 1 FROM User WHERE baseUrl = ?", arrayOf(badBaseUrl)).use { cursor ->
                assertTrue("Unreadable row should remain after list read", cursor.moveToFirst())
            }

        // Cleanup the planted bad row via execSQL since deleteUser would also work but this
        // documents that we control the lifecycle independently.
        database.openHelper.writableDatabase.execSQL("DELETE FROM User WHERE baseUrl = ?", arrayOf(badBaseUrl))
        repository.deleteUser(goodBaseUrl)
    }

    private fun corruptCiphertext(): String {
        // 12 bytes IV + 20 bytes "ciphertext+tag" of zeros. GCM tag verification will fail under
        // any real key, producing AEADBadTagException — the same surface as a Keystore-key mismatch.
        return "enc_v1:" + Base64.encodeToString(ByteArray(32) { 0 }, Base64.NO_WRAP)
    }
}
