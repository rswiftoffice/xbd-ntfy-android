package io.heckel.ntfy.db

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts sensitive values stored in local persistence.
 *
 * Values are encoded as "enc_v1:<base64(iv + ciphertext)>". Any value without the
 * prefix is treated as legacy plaintext for backwards compatibility.
 */
object SensitiveDataCipher {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "io.heckel.ntfy.db.sensitive.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_SIZE_BITS = 128
    private const val PREFIX = "enc_v1:"

    fun encrypt(value: String): String {
        if (value.isEmpty() || value.startsWith(PREFIX)) {
            return value
        }
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            val payload = cipher.iv + encrypted
            PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
        }.getOrThrow()
    }

    fun decrypt(value: String): String {
        if (!value.startsWith(PREFIX)) {
            return value
        }
        return try {
            val payload = Base64.decode(value.removePrefix(PREFIX), Base64.DEFAULT)
            if (payload.size <= 12) {
                throw IllegalArgumentException("Encrypted payload too short: ${payload.size} bytes")
            }
            val iv = payload.copyOfRange(0, 12)
            val encrypted = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // Symmetric with encrypt(): surface failures so callers can clean up the orphaned
            // ciphertext row instead of silently passing it through as if it were plaintext
            // (which previously caused enc_v1:... to be sent as the HTTP Basic auth username).
            throw SensitiveDataDecryptionException(e)
        }
    }

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}

class SensitiveDataDecryptionException(cause: Throwable)
    : Exception("Decryption of sensitive data failed", cause)
