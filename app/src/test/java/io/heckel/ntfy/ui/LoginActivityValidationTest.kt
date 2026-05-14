package io.heckel.ntfy.ui

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Repository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins the AD11 mitigation in `LoginActivity.onLoginClick`: empty / malformed URL
 * submissions must NOT call `changeDefaultServer` — otherwise an empty submit would
 * pass `""` to `Repository.setDefaultBaseUrl`, which is the "reset to flavor default"
 * path and would wipe the prior server's `User` row as a side effect.
 *
 * Asserts via `SharedPreferences` state (no pref write) and the error TextView.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoginActivityValidationTest {

    private lateinit var context: Context
    private val seededUrl = "https://known.example.com"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("MainPreferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putString("DefaultBaseURL", seededUrl)
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
    fun `empty URL submit shows error and does not touch preferences`() {
        assertErrorAndPrefsUnchanged(enteredUrl = "", expectedErrorRes = R.string.login_error_invalid_url)
    }

    @Test
    fun `malformed URL submit shows error and does not touch preferences`() {
        assertErrorAndPrefsUnchanged(
            enteredUrl = "https://example.com/with/path",
            expectedErrorRes = R.string.login_error_invalid_url
        )
    }

    private fun assertErrorAndPrefsUnchanged(enteredUrl: String, expectedErrorRes: Int) {
        val activity = Robolectric.buildActivity(LoginActivity::class.java).create().get()
        val serverInput = activity.findViewById<TextInputEditText>(R.id.login_server_url)
        val usernameInput = activity.findViewById<TextInputEditText>(R.id.login_username)
        val passwordInput = activity.findViewById<TextInputEditText>(R.id.login_password)
        val loginButton = activity.findViewById<MaterialButton>(R.id.login_button)
        val errorText = activity.findViewById<TextView>(R.id.login_error_text)
        serverInput.setText(enteredUrl)
        usernameInput.setText("alice")
        passwordInput.setText("secret")

        loginButton.performClick()

        assertEquals(View.VISIBLE.toLong(), errorText.visibility.toLong())
        assertEquals(activity.getString(expectedErrorRes), errorText.text.toString())
        // AD11 mitigation: pref must NOT have been overwritten with `""` (reset) or the entered URL.
        val prefs = context.getSharedPreferences("MainPreferences", Context.MODE_PRIVATE)
        assertEquals(seededUrl, prefs.getString("DefaultBaseURL", null))
        // No outgoing activity launch (would only happen on successful login).
        assertNull(shadowOf(activity).peekNextStartedActivity())
    }
}
