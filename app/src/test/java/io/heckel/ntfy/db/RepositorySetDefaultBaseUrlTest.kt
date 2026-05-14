package io.heckel.ntfy.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Slice 4 — pins `Repository.setDefaultBaseUrl`'s shape-only acceptance after the
 * `INTERNAL_BASE_URL_HOST_ALLOWLIST` is dropped (AD11). Arbitrary hosts and `http://`
 * URLs persist; malformed URLs (with a path) are still rejected by the retained
 * `validBaseUrl` shape gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositorySetDefaultBaseUrlTest {

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
    fun `accepts arbitrary https url outside prior allowlist`() {
        // S4.1 — AD11: allowlist dropped, any well-formed https URL persists.
        val url = "https://ntfy.other.example.com"

        repository.setDefaultBaseUrl(url)

        assertEquals(url, repository.getDefaultBaseUrl())
    }

    @Test
    fun `accepts http url`() {
        // S4.2 — AD11: scheme restriction dropped, http persists.
        val url = "http://localhost:8080"

        repository.setDefaultBaseUrl(url)

        assertEquals(url, repository.getDefaultBaseUrl())
    }

    @Test
    fun `rejects malformed url with a path`() {
        // S4.3 — AD11: shape gate retained. URL with a path is still rejected;
        // the prior pref must remain untouched.
        val prior = "https://prior.example.com"
        repository.setDefaultBaseUrl(prior)
        assertEquals(prior, repository.getDefaultBaseUrl())

        repository.setDefaultBaseUrl("https://example.com/subpath")

        assertEquals(prior, repository.getDefaultBaseUrl())
    }
}
