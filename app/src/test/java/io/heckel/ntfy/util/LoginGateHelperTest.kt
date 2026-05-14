package io.heckel.ntfy.util

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.db.User
import io.heckel.ntfy.ui.LoginActivity
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

/**
 * Tests for the DRY login-gate helper extracted in slice 3. Both `MainActivity.onCreate`
 * and `MainActivity.onResume` call this helper — the cold-launch behaviour is already
 * covered by `MainActivityGateTest`, this suite pins the helper itself so it can be
 * trusted from both lifecycle entry points without re-creating MainActivity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoginGateHelperTest {

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
    fun `redirects and finishes when no user row exists for effective base url`() {
        // Arrange: empty repository, fresh prefs (effectiveBaseUrl falls back to R.string.app_base_url).
        val repository = Repository.getInstance(context)
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().get()

        // Act
        val redirected = activity.applyLoginGateOrRedirect(repository)

        // Assert
        assertTrue("expected helper to signal redirect when no user row exists", redirected)
        val nextIntent = shadowOf(activity).peekNextStartedActivity()
        assertNotNull("expected LoginActivity intent to be launched", nextIntent)
        assertEquals(LoginActivity::class.java.name, nextIntent.component?.className)
        assertTrue("expected activity to be finishing after redirect", activity.isFinishing)
    }

    /**
     * S3.2 — gate-passes branch. Same JVM-keystore blocker as MainActivityGateTest's
     * S2.4: `Repository.addUser` calls `SensitiveDataCipher` which requires the
     * Android-only "AndroidKeyStore" provider. Covered structurally at the on-device
     * self-check (returning from Settings without logout → MainActivity stays visible).
     */
    @Ignore("Pending JVM keystore shim; covered by on-device self-check")
    @Test
    fun `returns false without redirect when user row exists for effective base url`() {
        val repository = Repository.getInstance(context)
        val baseUrl = context.getString(io.heckel.ntfy.R.string.app_base_url)
        runBlocking {
            repository.addUser(User(baseUrl = baseUrl, username = "alice", password = "secret"))
        }
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().get()

        val redirected = activity.applyLoginGateOrRedirect(repository)

        assertFalse("expected helper to NOT redirect when user row exists", redirected)
        assertNull(shadowOf(activity).peekNextStartedActivity())
        assertFalse(activity.isFinishing)
    }
}
