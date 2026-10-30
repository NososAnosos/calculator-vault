package ai.kbird.calculatorvault.vault

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * PIN-derived encryption, pure JVM (javax.crypto) so it is unit-testable.
 *
 * - Key: PBKDF2-HMAC-SHA256(PIN, per-install 16-byte salt, [DEFAULT_ITERATIONS]) -> 256-bit AES key.
 * - Data: AES-256-GCM, random 12-byte nonce per encryption, 128-bit tag.
 *   Sealed format: nonce || ciphertext+tag.
 * - Unlock check: a fixed constant sealed under the key at setup ("verifier").
 *   A wrong PIN fails GCM authentication; no PIN hash is stored separately.
 */
object VaultCrypto {
    /** OWASP (2023+) recommendation for PBKDF2-HMAC-SHA256. */
    const val DEFAULT_ITERATIONS = 600_000
    const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private val VERIFIER_PLAINTEXT = "kbird-vault-v1".toByteArray()
    private val VERIFIER_AAD = "verifier".toByteArray()

    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun deriveKey(pin: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        val ct = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(nonce.size + ct.size).put(nonce).put(ct).array()
    }

    /** Returns null if the key is wrong or the data/AAD were tampered with. */
    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray? {
        if (sealed.size < NONCE_BYTES + TAG_BITS / 8) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES),
        )
        cipher.updateAAD(aad)
        return try {
            cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
        } catch (_: AEADBadTagException) {
            null
        }
    }

    fun makeVerifier(key: ByteArray): ByteArray = seal(key, VERIFIER_PLAINTEXT, VERIFIER_AAD)

    fun checkVerifier(key: ByteArray, verifier: ByteArray): Boolean {
        val pt = open(key, verifier, VERIFIER_AAD) ?: return false
        return pt.contentEquals(VERIFIER_PLAINTEXT)
    }

    fun wipe(bytes: ByteArray?) {
        if (bytes != null) Arrays.fill(bytes, 0)
    }
}
