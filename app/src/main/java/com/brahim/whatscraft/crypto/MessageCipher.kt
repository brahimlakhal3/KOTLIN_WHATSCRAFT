package com.brahim.whatscraft.crypto

/**
 * The WhatsCraft message format (protocol v1).
 *
 * Wire format - identical on the relay's reference implementation
 * (server/scripts/client-crypto.ts):
 *
 *   body      = { alg:"A256GCM", iv, ct, sig }
 *   envelopes = [ { deviceId, wrappedKey:{ iv, ct } } ]
 *
 * Sealing:
 *   1. MK  = 32 random bytes, IV = 12 random bytes
 *      ct  = AES-256-GCM(MK, IV, plaintext, aad = "whatscraft/v1/msg|conv|msg")
 *      sig = Ed25519(sign, "whatscraft/v1/msg|conv|msg|iv|ct|signingPub")
 *   2. for each recipient device:
 *        shared = X25519(myPriv, deviceIdentityPub)
 *        salt   = SHA-256( senderIdentityPubBase64 | deviceIdentityPubBase64 )
 *        KEK    = HKDF-SHA256(shared, salt, "whatscraft/v1/kek", 32)
 *        wrap   = AES-256-GCM(KEK, wiv, MK,
 *                     aad = "whatscraft/v1/wrap|conv|msg|deviceId")
 *
 * The salt and every AAD are built from *base64 strings* (not raw bytes) so the
 * relay reference implementation and the app agree byte for byte.
 *
 * Opening reverses it, verifying the Ed25519 signature before the plaintext is
 * trusted. Any tampering makes the GCM tag or the signature check fail.
 */
object MessageCipher {

    const val ALGORITHM = "A256GCM"

    private const val MSG_AAD_PREFIX = "whatscraft/v1/msg"
    private const val WRAP_AAD_PREFIX = "whatscraft/v1/wrap"
    private const val KEK_INFO = "whatscraft/v1/kek"

    data class Body(val alg: String, val iv: String, val ct: String, val sig: String)

    data class WrappedKey(val iv: String, val ct: String)

    data class Envelope(val deviceId: String, val wrappedKey: WrappedKey)

    data class Sealed(val body: Body, val envelopes: List<Envelope>)

    data class RecipientDevice(val deviceId: String, val identityPublic: String)

    class DecryptionFailed(message: String) : Exception(message)

    // ------------------------------------------------------------------ seal --

    fun seal(
        identity: Crypto.IdentityPair,
        signing: Crypto.SigningPair,
        conversationId: String,
        messageId: String,
        plaintext: String,
        devices: List<RecipientDevice>,
    ): Sealed {
        require(devices.isNotEmpty()) { "At least one recipient device is required" }

        // 1. Content encryption with a one-off message key.
        val messageKey = Crypto.randomKey()
        val iv = Crypto.randomIv()
        val ciphertext = Crypto.aesGcmEncrypt(
            key = messageKey,
            iv = iv,
            plaintext = plaintext.toByteArray(Charsets.UTF_8),
            aad = messageAad(conversationId, messageId),
        )

        // 2. Signature over the ciphertext and the claimed signing key.
        val ivB64 = Crypto.b64(iv)
        val ctB64 = Crypto.b64(ciphertext)
        val signingPublicB64 = Crypto.b64(signing.publicKey)
        val signature = Crypto.sign(
            signing.privateKey,
            "$MSG_AAD_PREFIX|$conversationId|$messageId|$ivB64|$ctB64|$signingPublicB64"
                .toByteArray(Charsets.UTF_8),
        )

        // 3. Wrap the message key once per recipient device.
        val identityPublicB64 = Crypto.b64(identity.publicKey)
        val envelopes = devices.map { device ->
            val kek = deriveKek(
                myIdentityPrivate = identity.privateKey,
                peerIdentityPublicB64 = device.identityPublic,
                senderIdentityPublicB64 = identityPublicB64,
                recipientIdentityPublicB64 = device.identityPublic,
            )
            val wrapIv = Crypto.randomIv()
            val wrapped = Crypto.aesGcmEncrypt(
                key = kek,
                iv = wrapIv,
                plaintext = messageKey,
                aad = wrapAad(conversationId, messageId, device.deviceId),
            )
            Crypto.wipe(kek)
            Envelope(device.deviceId, WrappedKey(Crypto.b64(wrapIv), Crypto.b64(wrapped)))
        }
        Crypto.wipe(messageKey)

        return Sealed(
            body = Body(ALGORITHM, ivB64, ctB64, Crypto.b64(signature)),
            envelopes = envelopes,
        )
    }

    // ------------------------------------------------------------------ open --

    /**
     * Verifies and decrypts a message addressed to this device.
     *
     * @param senderIdentityPublicB64 the sender's X25519 identity key, as
     *   published by the relay (trusted on first use - see [safetyNumber]).
     * @param senderSigningPublicB64 the sender's Ed25519 signing key.
     */
    fun open(
        identity: Crypto.IdentityPair,
        myDeviceId: String,
        conversationId: String,
        messageId: String,
        senderIdentityPublicB64: String,
        senderSigningPublicB64: String,
        body: Body,
        envelope: WrappedKey,
    ): String {
        // 1. Unwrap the one-off message key.
        val kek = deriveKek(
            myIdentityPrivate = identity.privateKey,
            peerIdentityPublicB64 = senderIdentityPublicB64,
            senderIdentityPublicB64 = senderIdentityPublicB64,
            recipientIdentityPublicB64 = Crypto.b64(identity.publicKey),
        )
        val messageKey = try {
            Crypto.aesGcmDecrypt(
                key = kek,
                iv = Crypto.unb64(envelope.iv),
                ciphertext = Crypto.unb64(envelope.ct),
                aad = wrapAad(conversationId, messageId, myDeviceId),
            )
        } catch (error: Exception) {
            Crypto.wipe(kek)
            throw DecryptionFailed("This message was not encrypted for this device.")
        }
        Crypto.wipe(kek)

        // 2. Decrypt the content.
        val plaintext = try {
            Crypto.aesGcmDecrypt(
                key = messageKey,
                iv = Crypto.unb64(body.iv),
                ciphertext = Crypto.unb64(body.ct),
                aad = messageAad(conversationId, messageId),
            )
        } catch (error: Exception) {
            Crypto.wipe(messageKey)
            throw DecryptionFailed("The message content failed its integrity check.")
        }
        Crypto.wipe(messageKey)

        // 3. Authenticate the sender.
        val valid = Crypto.verify(
            publicKey = Crypto.unb64(senderSigningPublicB64),
            message = "$MSG_AAD_PREFIX|$conversationId|$messageId|${body.iv}|${body.ct}|$senderSigningPublicB64"
                .toByteArray(Charsets.UTF_8),
            signature = Crypto.unb64(body.sig),
        )
        if (!valid) {
            throw DecryptionFailed("The sender's signature is invalid.")
        }

        return String(plaintext, Charsets.UTF_8)
    }

    // ------------------------------------------------------------- internals --

    private fun deriveKek(
        myIdentityPrivate: ByteArray,
        peerIdentityPublicB64: String,
        senderIdentityPublicB64: String,
        recipientIdentityPublicB64: String,
    ): ByteArray {
        val shared = Crypto.sharedSecret(myIdentityPrivate, Crypto.unb64(peerIdentityPublicB64))
        // The salt is always built in (sender, recipient) order so both sides
        // derive the same key.
        val salt = Crypto.sha256(
            "$senderIdentityPublicB64|$recipientIdentityPublicB64".toByteArray(Charsets.UTF_8),
        )
        return Crypto.hkdf(shared, salt, KEK_INFO.toByteArray(Charsets.UTF_8))
    }

    private fun messageAad(conversationId: String, messageId: String): ByteArray =
        "$MSG_AAD_PREFIX|$conversationId|$messageId".toByteArray(Charsets.UTF_8)

    private fun wrapAad(conversationId: String, messageId: String, deviceId: String): ByteArray =
        "$WRAP_AAD_PREFIX|$conversationId|$messageId|$deviceId".toByteArray(Charsets.UTF_8)

    // ---------------------------------------------------------------- safety --

    /**
     * 60-digit fingerprint of two identities (12 groups of 5). Both people see
     * the same number; comparing it out loud rules out a man in the middle.
     */
    fun safetyNumber(identityPublicA: String, identityPublicB: String): String {
        val ordered = listOf(identityPublicA, identityPublicB).sorted()
        val digest = Crypto.sha512("${ordered[0]}|${ordered[1]}".toByteArray(Charsets.UTF_8))
        val digits = buildString {
            for (index in 0 until 60) {
                append((digest[index].toInt() and 0xFF) % 10)
            }
        }
        return digits.chunked(5).joinToString(" ")
    }
}
