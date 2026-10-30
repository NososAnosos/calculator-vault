package ai.kbird.calculatorvault.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** A decrypted note. Exists only in memory while the vault is unlocked. */
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val updatedAt: Long,
)

/**
 * Serialises everything about a note (title, body, timestamp) into one blob that
 * is sealed as a whole, so the database holds only an opaque id and ciphertext.
 * The id is bound as GCM AAD, so rows cannot be swapped between ids.
 */
object NoteCodec {
    private const val VERSION = 1

    fun encode(note: Note): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(VERSION)
            out.writeLong(note.updatedAt)
            writeString(out, note.title)
            writeString(out, note.body)
        }
        return bytes.toByteArray()
    }

    fun decode(id: String, data: ByteArray): Note {
        DataInputStream(ByteArrayInputStream(data)).use { input ->
            val version = input.readUnsignedByte()
            require(version == VERSION) { "unknown note format $version" }
            val updated = input.readLong()
            val title = readString(input)
            val body = readString(input)
            return Note(id, title, body, updated)
        }
    }

    fun seal(key: ByteArray, note: Note): ByteArray =
        VaultCrypto.seal(key, encode(note), aad(note.id))

    fun open(key: ByteArray, id: String, sealed: ByteArray): Note? =
        VaultCrypto.open(key, sealed, aad(id))?.let { decode(id, it) }

    private fun aad(id: String) = "note:$id".toByteArray()

    private fun writeString(out: DataOutputStream, s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        out.writeInt(b.size)
        out.write(b)
    }

    private fun readString(input: DataInputStream): String {
        val n = input.readInt()
        val b = ByteArray(n)
        input.readFully(b)
        return String(b, Charsets.UTF_8)
    }
}
