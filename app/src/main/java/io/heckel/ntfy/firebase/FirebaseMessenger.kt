package io.heckel.ntfy.firebase

/**
 * No-op messenger for non-Firebase builds.
 */
class FirebaseMessenger {
    fun subscribe(topic: String) {
        // Intentionally empty: Firebase is disabled in this build.
    }

    fun unsubscribe(topic: String) {
        // Intentionally empty: Firebase is disabled in this build.
    }
}
