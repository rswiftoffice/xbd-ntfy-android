package io.heckel.ntfy.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.db.User
import io.heckel.ntfy.msg.ApiService
import io.heckel.ntfy.util.Log
import io.heckel.ntfy.util.changeDefaultServer
import io.heckel.ntfy.util.effectiveBaseUrl
import io.heckel.ntfy.util.normalizeBaseUrl
import io.heckel.ntfy.util.suppressActivityTransition
import io.heckel.ntfy.util.validBaseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private val repository by lazy { Repository.getInstance(this) }
    private val api by lazy { ApiService(this) }

    private lateinit var serverUrlInput: TextInputEditText
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var progress: ProgressBar
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        serverUrlInput = findViewById(R.id.login_server_url)
        serverUrlInput.setText(effectiveBaseUrl(this, repository))

        usernameInput = findViewById(R.id.login_username)
        passwordInput = findViewById(R.id.login_password)
        loginButton = findViewById(R.id.login_button)
        progress = findViewById(R.id.login_progress)
        errorText = findViewById(R.id.login_error_text)

        loginButton.setOnClickListener { onLoginClick() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
    }

    private fun onLoginClick() {
        val enteredUrl = normalizeBaseUrl(serverUrlInput.text?.toString().orEmpty().trim())
        val username = usernameInput.text?.toString().orEmpty().trim()
        val password = passwordInput.text?.toString().orEmpty()
        if (enteredUrl.isEmpty() || !validBaseUrl(enteredUrl)) {
            showError(getString(R.string.login_error_invalid_url))
            return
        }
        if (username.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.login_error_invalid_credentials))
            return
        }

        setBusy(true)
        val user = User(baseUrl = enteredUrl, username = username, password = password)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                changeDefaultServer(this@LoginActivity, repository, enteredUrl)
                val authorized = api.verifyAccount(enteredUrl, user)
                if (authorized) {
                    repository.addUser(user)
                    withContext(Dispatchers.Main) { onLoginSuccess() }
                } else {
                    withContext(Dispatchers.Main) {
                        showError(getString(R.string.login_error_invalid_credentials))
                        setBusy(false)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Login failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showError(getString(R.string.login_error_server_unreachable))
                    setBusy(false)
                }
            }
        }
    }

    private fun onLoginSuccess() {
        startActivity(Intent(this, MainActivity::class.java))
        suppressActivityTransition()
        finish()
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        loginButton.isEnabled = !busy
        serverUrlInput.isEnabled = !busy
        usernameInput.isEnabled = !busy
        passwordInput.isEnabled = !busy
        if (busy) {
            errorText.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    companion object {
        private const val TAG = "NtfyLoginActivity"
    }
}
