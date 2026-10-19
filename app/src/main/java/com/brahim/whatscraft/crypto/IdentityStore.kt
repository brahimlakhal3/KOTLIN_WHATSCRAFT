package com.brahim.whatscraft.crypto

import android.os.Build

/**
 * This install's cryptographic identity.
 *
 * Every device generates its own X25519 identity key and Ed25519 signing key on
 * first use. Private keys never leave the phone - not even to the user's other
 * devices - and are sealed by [SecretVault] at rest.
 */
class IdentityStore(private val vault: SecretVault) {

    data class DeviceIdentity(
        val deviceId: String,
        val deviceName: String,
        val identity: Crypto.IdentityPair,
        val signing: Crypto.SigningPair,
    ) {
        val identityPublic: String get() = Crypto.b64(identity.publicKey)
        val signingPublic: String get() = Crypto.b64(signing.publicKey)
    }

    @Volatile
    private var cached: DeviceIdentity? = null

    /** Loads the identity, generating it on first launch. */
    fun load(): DeviceIdentity {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val identity = Crypto.identityFromSeed(
                vault.get(KEY_IDENTITY_SEED)?.let(::decodeSeed) ?: freshSeed(KEY_IDENTITY_SEED),
            )
            val signing = Crypto.signingFromSeed(
                vault.get(KEY_SIGNING_SEED)?.let(::decodeSeed) ?: freshSeed(KEY_SIGNING_SEED),
            )
            val deviceId = vault.get(KEY_DEVICE_ID) ?: java.util.UUID.randomUUID().toString().also {
                vault.put(KEY_DEVICE_ID, it)
            }
            val identity2 = DeviceIdentity(
                deviceId = deviceId,
                deviceName = vault.get(KEY_DEVICE_NAME) ?: defaultDeviceName(),
                identity = identity,
                signing = signing,
            )
            cached = identity2
            return identity2
        }
    }

    /** Replaces this device's keys - used when the account is re-created. */
    fun regenerate(): DeviceIdentity {
        synchronized(this) {
            secureWipe()
            return load()
        }
    }

    fun renameDevice(name: String) {
        vault.put(KEY_DEVICE_NAME, name)
        cached = cached?.copy(deviceName = name)
    }

    /** Removes every trace of the identity (sign out / delete account). */
    fun secureWipe() {
        synchronized(this) {
            vault.put(KEY_IDENTITY_SEED, null)
            vault.put(KEY_SIGNING_SEED, null)
            vault.put(KEY_DEVICE_NAME, null)
            cached = null
        }
    }

    // --------------------------------------------------------------- private --

    private fun freshSeed(key: String): ByteArray {
        val seed = Crypto.randomBytes(Crypto.KEY_LENGTH)
        vault.put(key, Crypto.b64(seed))
        return seed
    }

    private fun decodeSeed(encoded: String): ByteArray = Crypto.unb64(encoded).also {
        require(it.size == Crypto.KEY_LENGTH) { "Stored key has the wrong length" }
    }

    private fun defaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: ""
        val model = Build.MODEL ?: "Android"
        return when {
            model.startsWith(manufacturer, ignoreCase = true) -> model
            manufacturer.isBlank() -> model
            else -> "$manufacturer $model"
        }
    }

    private companion object {
        const val KEY_IDENTITY_SEED = "identity.seed"
        const val KEY_SIGNING_SEED = "signing.seed"
        const val KEY_DEVICE_ID = "device.id"
        const val KEY_DEVICE_NAME = "device.name"
    }
}
