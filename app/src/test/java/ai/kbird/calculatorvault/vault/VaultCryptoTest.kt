package ai.kbird.calculatorvault.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultCryptoTest {

    // Low iteration count keeps tests fast; production uses DEFAULT_ITERATIONS.
    private val iterations = 1_000
    private val salt = VaultCrypto.newSalt()
    private fun key(pin: String) = VaultCrypto.deriveKey(pin.toCharArray(), salt, iterations)

    @Test fun keyDerivationIsDeterministicPerSaltAndPin() {
        assertArrayEquals(key("2580"), key("2580"))
        assertFalse(key("2580").contentEquals(key("2581")))
        val otherSalt = VaultCrypto.deriveKey("2580".toCharArray(), VaultCrypto.newSalt(), iterations)
        assertFalse(key("2580").contentEquals(otherSalt))
        assertEquals(32, key("2580").size)
    }

    @Test fun knownPbkdf2Vector() {
        // RFC 7914 section 11 PBKDF2-HMAC-SHA256 test vector (P="passwd", S="salt", c=1).
        val k = VaultCrypto.deriveKey("passwd".toCharArray(), "salt".toByteArray(), 1)
        val hex = k.joinToString("") { "%02x".format(it) }
        assertEquals("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc", hex)
    }

    @Test fun sealOpenRoundTrip() {
        val k = key("2580")
        val sealed = VaultCrypto.seal(k, "hello".toByteArray(), "aad".toByteArray())
        assertEquals("hello", String(VaultCrypto.open(k, sealed, "aad".toByteArray())!!))
    }

    @Test fun nonceIsFreshPerSeal() {
        val k = key("2580")
        val a = VaultCrypto.seal(k, "same".toByteArray(), ByteArray(0))
        val b = VaultCrypto.seal(k, "same".toByteArray(), ByteArray(0))
        assertNotEquals(a.toList(), b.toList())
    }

    @Test fun wrongKeyTamperOrAadFailAuthentication() {
        val k = key("2580")
        val sealed = VaultCrypto.seal(k, "secret".toByteArray(), "a".toByteArray())
        assertNull(VaultCrypto.open(key("0000"), sealed, "a".toByteArray()))
        assertNull(VaultCrypto.open(k, sealed, "b".toByteArray()))
        val tampered = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertNull(VaultCrypto.open(k, tampered, "a".toByteArray()))
        assertNull(VaultCrypto.open(k, ByteArray(5), "a".toByteArray()))
    }

    @Test fun verifierAcceptsOnlyTheRightPin() {
        val verifier = VaultCrypto.makeVerifier(key("2580"))
        assertTrue(VaultCrypto.checkVerifier(key("2580"), verifier))
        assertFalse(VaultCrypto.checkVerifier(key("2581"), verifier))
    }

    @Test fun noteRoundTripEncryptsTitleAndBody() {
        val k = key("2580")
        val note = Note("id-1", "Bank codes", "line one\nline two ✓", 1_700_000_000_000L)
        val sealed = NoteCodec.seal(k, note)
        assertEquals(note, NoteCodec.open(k, "id-1", sealed))

        // Neither title nor body appears in the stored blob.
        val blob = String(sealed, Charsets.ISO_8859_1)
        assertFalse(blob.contains("Bank codes"))
        assertFalse(blob.contains("line one"))
    }

    @Test fun noteBoundToItsId() {
        val k = key("2580")
        val sealed = NoteCodec.seal(k, Note("id-1", "t", "b", 0))
        assertNull(NoteCodec.open(k, "id-2", sealed))
        assertNull(NoteCodec.open(key("1111"), "id-1", sealed))
    }

    @Test fun wipeZeroesKey() {
        val k = key("2580")
        VaultCrypto.wipe(k)
        assertTrue(k.all { it == 0.toByte() })
    }
}
