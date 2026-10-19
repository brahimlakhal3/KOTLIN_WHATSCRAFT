package com.brahim.whatscraft.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.util.encoders.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Low-level cryptography for WhatsCraft.
 *
 *   X25519      key agreement (identity keys)
 *   Ed25519     message authenticity (signing keys)
 *   HKDF-SHA256 key derivation
 *   AES-256-GCM authenticated encryption
 *
 * This is a pure-Java implementation (BouncyCastle's lightweight API for the
 * curve maths, the platform's AES-GCM engine for speed), so the exact same code
 * runs on the device and in JVM unit tests.
 */
object Crypto {

    const val KEY_LENGTH = 32
    const val IV_LENGTH = 12
    const val TAG_LENGTH = 16
    const val GCM_TAG_BITS = 128

    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private val random = SecureRandom()

    // ------------------------------------------------------------- encoding --

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    fun randomKey(): ByteArray = randomBytes(KEY_LENGTH)

    fun randomIv(): ByteArray = randomBytes(IV_LENGTH)

    /** Standard base64 with padding and no line breaks - identical to the relay. */
    fun b64(bytes: ByteArray): String = String(Base64.encode(bytes), Charsets.US_ASCII)

    fun unb64(text: String): ByteArray = Base64.decode(text)

    // ------------------------------------------------------------- hashing ---

    fun sha256(vararg chunks: ByteArray): ByteArray {
        val digest = SHA256Digest()
        chunks.forEach { chunk -> digest.update(chunk, 0, chunk.size) }
        return ByteArray(digest.digestSize).also { digest.doFinal(it, 0) }
    }

    fun sha512(vararg chunks: ByteArray): ByteArray {
        val digest = SHA512Digest()
        chunks.forEach { chunk -> digest.update(chunk, 0, chunk.size) }
        return ByteArray(digest.digestSize).also { digest.doFinal(it, 0) }
    }

    // -------------------------------------------------------------- X25519 ---

    class IdentityPair(val privateKey: ByteArray, val publicKey: ByteArray)

    /** Generates an X25519 identity key pair from a fresh 32-byte seed. */
    fun generateIdentity(): IdentityPair = identityFromSeed(randomBytes(KEY_LENGTH))

    /** Deterministic identity from a seed - used by tests and restores. */
    fun identityFromSeed(seed: ByteArray): IdentityPair {
        val privateKey = X25519PrivateKeyParameters(seed, 0)
        val publicKey = privateKey.generatePublicKey()
        return IdentityPair(seed.copyOf(), publicKey.encoded)
    }

    /** Raw X25519 shared secret (32 bytes). */
    fun sharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(privateKey, 0))
        val secret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), secret, 0)
        return secret
    }

    // ------------------------------------------------------------- Ed25519 ---

    class SigningPair(val privateKey: ByteArray, val publicKey: ByteArray)

    fun generateSigning(): SigningPair = signingFromSeed(randomBytes(KEY_LENGTH))

    fun signingFromSeed(seed: ByteArray): SigningPair {
        val privateKey = Ed25519PrivateKeyParameters(seed, 0)
        return SigningPair(seed.copyOf(), privateKey.generatePublicKey().encoded)
    }

    fun sign(privateKey: ByteArray, message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        val signer = Ed25519Signer()
        return try {
            signer.init(false, Ed25519PublicKeyParameters(publicKey, 0))
            signer.update(message, 0, message.size)
            signer.verifySignature(signature)
        } catch (_: Exception) {
            false
        }
    }

    // ----------------------------------------------------------------- HKDF ---

    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = KEY_LENGTH): ByteArray {
        val generator = HKDFBytesGenerator(SHA256Digest())
        generator.init(HKDFParameters(ikm, salt, info))
        return ByteArray(length).also { generator.generateBytes(it, 0, length) }
    }

    // ----------------------------------------------------------- AES-256-GCM --

    fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    /** Wipes sensitive material as soon as it is no longer needed. */
    fun wipe(vararg arrays: ByteArray?) {
        arrays.forEach { array -> array?.fill(0) }
    }
}
