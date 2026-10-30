package ai.kbird.calculatorvault.vault

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import java.util.UUID

/**
 * On-disk vault state. Deliberately bland names: nothing in prefs or the database
 * says "vault", "note", or "pin".
 *
 * prefs  "calc_state": salt, verifier, iteration count, attempt backoff state.
 * sqlite "calc.db", table b(id TEXT PRIMARY KEY, d BLOB): one sealed blob per note.
 */
class VaultRepository(context: Context) {
    private val prefs = context.getSharedPreferences("calc_state", Context.MODE_PRIVATE)
    private val db = BlobDb(context)

    val isConfigured: Boolean get() = prefs.contains(K_VERIFIER)

    var attempts: AttemptState
        get() = AttemptState(prefs.getInt(K_FAILURES, 0), prefs.getLong(K_UNTIL, 0L))
        set(v) {
            // commit(), not apply(): attempt state is security-critical and
            // written rarely. apply() is async on disk — if the process dies
            // right after a successful unlock (the 2026-10-28 crash did
            // exactly that), the reset is lost and stale failures/lockout
            // resurrect on the next launch.
            prefs.edit().putInt(K_FAILURES, v.consecutiveFailures).putLong(K_UNTIL, v.lockedUntil).commit()
        }

    /** First-run setup. Returns the session key. Call off the main thread (PBKDF2). */
    fun setup(pin: CharArray): ByteArray {
        check(!isConfigured) { "vault already configured" }
        val salt = VaultCrypto.newSalt()
        val iterations = VaultCrypto.DEFAULT_ITERATIONS
        val key = VaultCrypto.deriveKey(pin, salt, iterations)
        prefs.edit()
            .putString(K_SALT, b64(salt))
            .putInt(K_ITERATIONS, iterations)
            .putString(K_VERIFIER, b64(VaultCrypto.makeVerifier(key)))
            .commit()
        return key
    }

    /** Returns the session key, or null for a wrong PIN. Call off the main thread. */
    fun unlock(pin: CharArray): ByteArray? {
        val salt = prefs.getString(K_SALT, null)?.let(::unb64) ?: return null
        val verifier = prefs.getString(K_VERIFIER, null)?.let(::unb64) ?: return null
        val iterations = prefs.getInt(K_ITERATIONS, VaultCrypto.DEFAULT_ITERATIONS)
        val key = VaultCrypto.deriveKey(pin, salt, iterations)
        if (VaultCrypto.checkVerifier(key, verifier)) return key
        VaultCrypto.wipe(key)
        return null
    }

    fun loadNotes(key: ByteArray): List<Note> {
        val out = ArrayList<Note>()
        db.readableDatabase.query("b", arrayOf("id", "d"), null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                NoteCodec.open(key, c.getString(0), c.getBlob(1))?.let(out::add)
            }
        }
        return out.sortedByDescending { it.updatedAt }
    }

    /** Store an already-sealed note blob (see [NoteCodec.seal]). */
    fun writeSealed(id: String, sealed: ByteArray) {
        val values = ContentValues().apply {
            put("id", id)
            put("d", sealed)
        }
        db.writableDatabase.insertWithOnConflict("b", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteNote(id: String) {
        db.writableDatabase.delete("b", "id = ?", arrayOf(id))
    }

    fun newNoteId(): String = UUID.randomUUID().toString()

    private class BlobDb(context: Context) : SQLiteOpenHelper(context, "calc.db", null, 1) {
        override fun onConfigure(db: SQLiteDatabase) {
            // Overwrite freed pages so deleted ciphertext does not linger.
            db.rawQuery("PRAGMA secure_delete = ON", null).use { it.moveToFirst() }
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE b (id TEXT PRIMARY KEY, d BLOB NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        private const val K_SALT = "s"
        private const val K_VERIFIER = "v"
        private const val K_ITERATIONS = "i"
        private const val K_FAILURES = "f"
        private const val K_UNTIL = "u"

        /** What a typed formula must look like to be tried as a PIN. */
        val PIN_PATTERN = Regex("""\d{4,12}""")
    }
}
