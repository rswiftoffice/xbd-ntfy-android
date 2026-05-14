package io.heckel.ntfy.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Repository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EffectiveBaseUrlTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear shared preferences between tests so seeded keys don't leak.
        context.getSharedPreferences("MainPreferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        // Repository.getInstance is a JVM singleton; reset it so each test gets a fresh
        // instance bound to *this* sandbox's SharedPreferences (else Robolectric sandbox
        // isolation leaks state across tests in the same JVM).
        Repository::class.java.getDeclaredField("instance").apply {
            isAccessible = true
            set(null, null)
        }
    }

    @Test
    fun `returns SharedPreferences default-base-url when set`() {
        // Arrange: seed the default-server preference directly (bypass allowlist; we're
        // testing the helper, not setDefaultBaseUrl).
        val expected = "https://override.example.com"
        context.getSharedPreferences("MainPreferences", Context.MODE_PRIVATE)
            .edit()
            .putString("DefaultBaseURL", expected)
            .commit()
        val repository = Repository.getInstance(context)

        // Act
        val actual = effectiveBaseUrl(context, repository)

        // Assert
        assertEquals(expected, actual)
    }

    @Test
    fun `falls back to R_string_app_base_url when no default preference is set`() {
        // Arrange: no DefaultBaseURL key in SharedPreferences (setUp() cleared it).
        val repository = Repository.getInstance(context)

        // Act
        val actual = effectiveBaseUrl(context, repository)

        // Assert
        assertEquals(context.getString(R.string.app_base_url), actual)
    }
}
