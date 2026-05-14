package io.heckel.ntfy.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.db.User
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityGateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("MainPreferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        Repository::class.java.getDeclaredField("instance").apply {
            isAccessible = true
            set(null, null)
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build()
        )
    }

    @Test
    fun `cold launch with no user row redirects to LoginActivity and finishes`() {
        // Arrange: fresh state — no User row for the effective base URL.

        // Act
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()

        // Assert: gate fired — a LoginActivity intent was launched and MainActivity is finishing.
        val nextIntent = shadowOf(activity).peekNextStartedActivity()
        assertNotNull("expected LoginActivity intent to be launched", nextIntent)
        assertEquals(LoginActivity::class.java.name, nextIntent.component?.className)
        assertTrue("expected MainActivity to finish itself after redirect", activity.isFinishing)
    }

    /**
     * NFR1: gate decision must complete *before* any observers, workers, or dispatcher
     * setup. Pins the ordering by reading the post-gate fields (`workManager`,
     * `dispatcher`, `appBaseUrl` assigned at MainActivity.kt:154-156) via reflection
     * and asserting they remain null on the redirect path. Regression scenario: a
     * future refactor that moves the gate below one of these assignments would leak
     * a WorkManager/dispatcher instance for an unauthenticated user.
     */
    @Test
    fun `gate redirect leaves post-gate dependencies unset (NFR1)`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()

        assertTrue("precondition: gate must have redirected", activity.isFinishing)
        listOf("workManager", "dispatcher", "appBaseUrl").forEach { fieldName ->
            val field = MainActivity::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            assertNull(
                "NFR1 violated: `$fieldName` initialised before/at the gate redirect",
                field.get(activity)
            )
        }
    }

    /**
     * S2.4 — gate-passes branch. Deferred under JVM/Robolectric because
     * [io.heckel.ntfy.db.SensitiveDataCipher] uses the Android-only "AndroidKeyStore"
     * provider, which has no JVM shim. Seeding via [Repository.addUser] fails with
     * KeyStoreException → NoSuchAlgorithmException; seeding plaintext directly
     * triggers in-line re-encryption in [Repository.getUser]'s migration path,
     * which also hits the keystore. Covered at the on-device self-check
     * (force-quit + relaunch after successful login → no LoginActivity flash).
     */
    @Ignore("Pending JVM keystore shim; covered by on-device self-check")
    @Test
    fun `cold launch with valid user row proceeds without redirect`() {
        // Arrange: seed a User row for the effective base URL so the gate passes.
        val repository = Repository.getInstance(context)
        val baseUrl = context.getString(R.string.app_base_url)
        runBlocking {
            repository.addUser(User(baseUrl = baseUrl, username = "alice", password = "secret"))
        }

        // Act
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()

        // Assert: gate did NOT redirect — no LoginActivity intent, activity still alive.
        val nextIntent = shadowOf(activity).peekNextStartedActivity()
        assertNull("expected no redirect intent when user row exists", nextIntent)
        assertFalse("expected MainActivity to remain alive past the gate", activity.isFinishing)
    }
}
