package io.heckel.ntfy.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import io.heckel.ntfy.R
import io.heckel.ntfy.db.Repository
import io.heckel.ntfy.service.SubscriberServiceManager
import io.heckel.ntfy.ui.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Effective base URL for the login gate — SSOT (AD7).
 *
 * Returns the user's chosen default-server preference if set, else falls back to the
 * build-flavor-baked [R.string.app_base_url].
 */
fun effectiveBaseUrl(context: Context, repository: Repository): String {
    return repository.getDefaultBaseUrl() ?: context.getString(R.string.app_base_url)
}

/**
 * Suppress the activity-open transition animation. Uses the SDK 34+ API where available,
 * falls back to the deprecated [Activity.overridePendingTransition] on older devices
 * (minSdk 26).
 */
fun Activity.suppressActivityTransition() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

/**
 * Login gate (AD1/AD3) — DRY helper shared by `MainActivity.onCreate` and `MainActivity.onResume`.
 *
 * If no [io.heckel.ntfy.db.User] row exists for [effectiveBaseUrl], launches [LoginActivity],
 * suppresses the transition, calls [Activity.finish], and returns `true`. Otherwise returns
 * `false` and leaves the caller intact.
 *
 * Callers MUST early-`return` when this returns `true` to honour NFR1 — no observers,
 * workers, or Firebase subscribes leak before the gate decides.
 */
fun AppCompatActivity.applyLoginGateOrRedirect(repository: Repository): Boolean {
    val user = runBlocking(Dispatchers.IO) {
        repository.getUser(effectiveBaseUrl(this@applyLoginGateOrRedirect, repository))
    }
    if (user != null) return false
    startActivity(Intent(this, LoginActivity::class.java))
    suppressActivityTransition()
    finish()
    return true
}

/**
 * Logout — SSOT for "delete the User row at the effective base URL" (AD7/AD9).
 *
 * Bypasses the keystore decryption path entirely (DAO delete by primary key), so it
 * succeeds even when a key rotation orphaned the row. Refreshes the subscriber service
 * so any in-flight WS/HTTP connections holding the prior Basic-auth credentials are
 * torn down — without this, cached credentials keep flowing on existing connections
 * until process restart.
 */
suspend fun performLogout(context: Context, repository: Repository) {
    repository.deleteUser(effectiveBaseUrl(context, repository))
    SubscriberServiceManager.refresh(context)
}

/**
 * Switch the default server — single-account-at-a-time policy (slice 3 amendment).
 *
 * When the URL actually changes, wipe the prior server's `User` row so the gate
 * re-prompts on the new server. Same-URL writes are a no-op (preserves the row).
 * Co-located with [performLogout] / [applyLoginGateOrRedirect] so all auth-state
 * mutations live in one place.
 */
suspend fun changeDefaultServer(context: Context, repository: Repository, newUrl: String) {
    val oldEffective = effectiveBaseUrl(context, repository)
    if (newUrl != oldEffective) {
        repository.deleteUser(oldEffective)
    }
    repository.setDefaultBaseUrl(newUrl)
}
