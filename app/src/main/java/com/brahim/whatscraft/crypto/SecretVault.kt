package com.brahim.whatscraft.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A vault backed by the Android Keystore.
 *
 * One AES-256-GCM key lives inside the Keystore (hardware-isolated on most
 * modern phones, so it cannot be extracted even from a rooted device's
 * filesystem). Everything sensitive - the X25519/Ed25519 private keys and the
 * local copy of decrypted message text - is sealed with it before being written
 * to disk.
 *
 * Note: Keystore keys are per-install. Removing the app removes the key, which
 * is why message history is fetched back from the relay and re-decrypted.
 */
class SecretVault(context: Context) {

    class VaultUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private val key: SecretKey
        get() = loadOrCreateKey()

    // ---------------------------------------------------------------- public --

    /** Seals [plaintext] and returns base64(iv || ciphertext). */
    fun seal(plaintext: ByteArray, associatedData: String? = null): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        associatedData?.let { cipher.updateAAD(it.toByteArray(Charsets.UTF_8)) }
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    fun sealString(plaintext: String, associatedData: String? = null): String =
        seal(plaintext.toByteArray(Charsets.UTF_8), associatedData)

    fun open(sealed: String, associatedData: String? = null): ByteArray {
        val blob = Base64.decode(sealed, Base64.NO_WRAP)
        if (blob.size <= IV_LENGTH) throw VaultUnavailableException("Sealed value is malformed")
        val iv = blob.copyOfRange(0, IV_LENGTH)
        val ciphertext = blob.copyOfRange(IV_LENGTH, blob.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        associatedData?.let { cipher.updateAAD(it.toByteArray(Charsets.UTF_8)) }
        return try {
            cipher.doFinal(ciphertext)
        } catch (error: Exception) {
            throw VaultUnavailableException("Stored data could not be decrypted on this device", error)
        }
    }

    fun openString(sealed: String, associatedData: String? = null): String =
        String(open(sealed, associatedData), Charsets.UTF_8)

    fun put(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    fun get(key: String): String? = prefs.getString(key, null)

    /** Destroys the Keystore key and every sealed value (used on "delete account"). */
    fun destroy() {
        prefs.edit().clear().apply()
        runCatching { keyStore.deleteEntry(KEY_ALIAS) }
    }

    // --------------------------------------------------------------- private --

    private fun loadOrCreateKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return createKey()
    }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // A fresh random IV is generated for every operation.
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "whatscraft.vault.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFS_NAME = "whatscraft.sealed"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
