package io.heckel.ntfy.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.heckel.ntfy.db.Database
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.db.User
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for `performLogout` — the SSOT for "logout means: delete the User row at the
 * effective base URL". Uses an in-memory Room database wired into a manually-constructed
 * `Repository` so we bypass `Repository.addUser`'s keystore-encrypted insert (which has
 * no JVM provider). The deletion path itself is keystore-free (DAO `DELETE WHERE baseUrl`)
 * so this test exercises real behaviour, not a mock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerformLogoutTest {

    private lateinit var context: Context
    private lateinit var database: Database
    private lateinit var repository: Repository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("MainPreferences", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        database = Room
            .inMemoryDatabaseBuilder(context, Database::class.java)
            .allowMainThreadQueries()
            .build()
        repository = Repository(prefs, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `deletes only the user row for the effective base url`() {
        // Arrange: two raw User rows (bypass Repository.addUser to skip keystore encryption —
        // we're testing the deletion mechanic, not the cipher). Effective base URL is "https://A".
        val target = "https://A.example.com"
        val other = "https://B.example.com"
        runBlocking {
            database.userDao().insert(User(baseUrl = target, username = "alice", password = "x"))
            database.userDao().insert(User(baseUrl = other, username = "bob", password = "y"))
        }
        context.getSharedPreferences("MainPreferences", Context.MODE_PRIVATE)
            .edit()
            .putString("DefaultBaseURL", target)
            .commit()

        // Act
        runBlocking { performLogout(context, repository) }

        // Assert: target row gone, the other server's row is preserved.
        runBlocking {
            assertNull("expected target row deleted", database.userDao().get(target))
            assertNotNull("expected other server's row preserved", database.userDao().get(other))
        }
    }
}
