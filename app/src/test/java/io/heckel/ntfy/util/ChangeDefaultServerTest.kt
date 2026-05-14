package io.heckel.ntfy.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.heckel.ntfy.db.Database
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.db.User
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for `changeDefaultServer` — slice 3 in-flight amendment.
 *
 * Single-account-at-a-time policy: switching the default server wipes the prior
 * server's `User` row so the gate re-prompts on the new server. Same-URL writes
 * are a no-op so a no-op preference save doesn't accidentally log the user out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChangeDefaultServerTest {

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
    fun `wipes prior server user row when default url changes`() {
        // Arrange: User row at the current effective base URL ("A"); default-url pref set to A.
        val a = "https://unifiedpush.airforceraid.swiftoffice.org"
        val b = "https://unifiedpush.airforceraid-stag.swiftoffice.org"
        context.getSharedPreferences("MainPreferences", Context.MODE_PRIVATE)
            .edit()
            .putString("DefaultBaseURL", a)
            .commit()
        runBlocking {
            database.userDao().insert(User(baseUrl = a, username = "alice", password = "x"))
        }

        // Act: switch default to B.
        runBlocking { changeDefaultServer(context, repository, b) }

        // Assert: A's row gone; default-url pref now B.
        runBlocking { assertNull("expected prior server row deleted", database.userDao().get(a)) }
        assertEquals(b, repository.getDefaultBaseUrl())
    }

    @Test
    fun `same-url no-op preserves existing user row`() {
        // Arrange: User row at the current effective base URL; default-url pref set to it.
        val a = "https://unifiedpush.airforceraid.swiftoffice.org"
        context.getSharedPreferences("MainPreferences", Context.MODE_PRIVATE)
            .edit()
            .putString("DefaultBaseURL", a)
            .commit()
        runBlocking {
            database.userDao().insert(User(baseUrl = a, username = "alice", password = "x"))
        }

        // Act: "change" to the same URL (no-op save).
        runBlocking { changeDefaultServer(context, repository, a) }

        // Assert: row preserved.
        runBlocking {
            assertNotNull("expected row preserved on same-url no-op", database.userDao().get(a))
        }
    }
}
