package io.heckel.ntfy.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SensitiveDataAtRestTest {
    @Test
    fun sensitiveFields_areEncryptedAtRest_andDecryptedOnRead() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = Repository.getInstance(context)
        val database = Database.getInstance(context)

        val baseUrl = "https://scr-m2-test.local"
        val username = "alice"
        val password = "super-secret-password"
        val pem = "-----BEGIN CERTIFICATE-----\\nTEST\\n-----END CERTIFICATE-----"
        val p12Base64 = "MIIKTESTBASE64=="
        val p12Password = "p12-secret"

        // Clean slate for deterministic assertions.
        repository.deleteUser(baseUrl)
        repository.removeTrustedCertificate(baseUrl)
        repository.removeClientCertificate(baseUrl)

        repository.addUser(User(baseUrl, username, password))
        repository.addTrustedCertificate(baseUrl, pem)
        repository.addClientCertificate(baseUrl, p12Base64, p12Password)

        val rawDb = database.openHelper.readableDatabase
        assertEncryptedValue(rawDb, "SELECT password FROM User WHERE baseUrl = ?", arrayOf(baseUrl), password)
        assertEncryptedValue(rawDb, "SELECT pem FROM TrustedCertificate WHERE baseUrl = ?", arrayOf(baseUrl), pem)
        assertEncryptedValue(rawDb, "SELECT p12Base64 FROM ClientCertificate WHERE baseUrl = ?", arrayOf(baseUrl), p12Base64)
        assertEncryptedValue(rawDb, "SELECT password FROM ClientCertificate WHERE baseUrl = ?", arrayOf(baseUrl), p12Password)

        // Verify repository still returns plaintext in memory.
        assertEquals(password, repository.getUser(baseUrl)?.password)
        assertEquals(pem, repository.getTrustedCertificate(baseUrl)?.pem)
        assertEquals(p12Base64, repository.getClientCertificate(baseUrl)?.p12Base64)
        assertEquals(p12Password, repository.getClientCertificate(baseUrl)?.password)

        // Cleanup
        repository.deleteUser(baseUrl)
        repository.removeTrustedCertificate(baseUrl)
        repository.removeClientCertificate(baseUrl)
    }

    private fun assertEncryptedValue(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String,
        args: Array<String>,
        plaintext: String
    ) {
        db.query(sql, args).use { cursor ->
            assertTrue("Expected query to return a row for $sql", cursor.moveToFirst())
            val stored = cursor.getString(0)
            assertNotEquals("Sensitive value must not be stored as plaintext", plaintext, stored)
            assertTrue("Sensitive value should be stored with encryption prefix", stored.startsWith("enc_v1:"))
        }
    }
}
