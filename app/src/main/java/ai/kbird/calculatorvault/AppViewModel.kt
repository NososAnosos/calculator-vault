package ai.kbird.calculatorvault

import ai.kbird.calculatorvault.calc.CalcKey
import ai.kbird.calculatorvault.calc.CalculatorEngine
import ai.kbird.calculatorvault.calc.DisplayMode
import ai.kbird.calculatorvault.calc.DisplayState
import ai.kbird.calculatorvault.calc.HistoryEntry
import ai.kbird.calculatorvault.calc.HistoryStore
import ai.kbird.calculatorvault.ui.theme.ThemeChoice
import ai.kbird.calculatorvault.vault.AttemptLimiter
import ai.kbird.calculatorvault.vault.Note
import ai.kbird.calculatorvault.vault.NoteCodec
import ai.kbird.calculatorvault.vault.VaultCrypto
import ai.kbird.calculatorvault.vault.VaultRepository
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { CALCULATOR, VAULT_SETUP, VAULT }

data class CalcUiState(
    val display: DisplayState = DisplayState(),
    val history: List<HistoryEntry> = emptyList(),
    val historyOpen: Boolean = false,
    val scientific: Boolean = false,
    val inverse: Boolean = false,
    val degrees: Boolean = true,
)

data class VaultUiState(
    val notes: List<Note> = emptyList(),
    /** Note open in the editor, or null for the list. */
    val editingId: String? = null,
    val busy: Boolean = false,
    val setupError: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val historyStore = HistoryStore(app)
    private val vault = VaultRepository(app)
    private val settings = app.getSharedPreferences("calc_settings", Context.MODE_PRIVATE)
    private val engine = CalculatorEngine(historyStore.load())

    private val _calc = MutableStateFlow(CalcUiState(display = engine.state(), history = engine.history.toList()))
    val calc: StateFlow<CalcUiState> = _calc.asStateFlow()

    private val _screen = MutableStateFlow(Screen.CALCULATOR)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _vault = MutableStateFlow(VaultUiState())
    val vaultState: StateFlow<VaultUiState> = _vault.asStateFlow()

    private val _theme = MutableStateFlow(
        runCatching { ThemeChoice.valueOf(settings.getString("theme", null) ?: "") }.getOrDefault(ThemeChoice.SYSTEM),
    )
    val theme: StateFlow<ThemeChoice> = _theme.asStateFlow()

    /** Session key; non-null only while the vault is open. Never persisted. */
    private var key: ByteArray? = null
    private var unlockInFlight = false

    /**
     * Bumped by every [lock]. Key derivation takes ~1 s; if the app is backgrounded
     * meanwhile, the stale result must not open the vault behind the user's back.
     */
    private var lockGeneration = 0

    /** Serialises note writes/deletes so they hit disk in the order they were made. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val writeLane = Dispatchers.IO.limitedParallelism(1)

    // ---- calculator ----

    fun press(k: CalcKey) {
        engine.press(k)
        publish()
    }

    fun pressEquals() {
        val typed = engine.equals()
        publish()
        maybeUnlock(typed)
    }

    fun toggleScientific() = _calc.update { it.copy(scientific = !it.scientific) }
    fun toggleInverse() = _calc.update { it.copy(inverse = !it.inverse) }

    fun toggleDegrees() {
        engine.degrees = !engine.degrees
        publish()
    }

    fun setHistoryOpen(open: Boolean) = _calc.update { it.copy(historyOpen = open) }

    fun restoreHistory(entry: HistoryEntry) {
        engine.restore(entry.formula)
        _calc.update { it.copy(historyOpen = false) }
        publish()
    }

    fun clearHistory() {
        engine.clearHistory()
        historyStore.save(emptyList())
        publish()
    }

    fun setTheme(choice: ThemeChoice) {
        _theme.value = choice
        settings.edit().putString("theme", choice.name).apply()
    }

    private fun publish() {
        val history = engine.history.toList()
        if (history != _calc.value.history) historyStore.save(history)
        _calc.update { it.copy(display = engine.state(), history = history, degrees = engine.degrees) }
    }

    // ---- vault entry ----

    /**
     * v1 placeholder gesture: long-press "=" on an empty display opens first-run
     * setup. Once a vault exists the gesture does nothing, so it reveals nothing.
     */
    fun equalsLongPress(): Boolean {
        val d = engine.state()
        if (d.mode != DisplayMode.EDIT || d.formula.isNotEmpty() || vault.isConfigured) return false
        _vault.value = VaultUiState()
        _screen.value = Screen.VAULT_SETUP
        return true
    }

    private fun maybeUnlock(typed: String) {
        if (unlockInFlight || !vault.isConfigured || !VaultRepository.PIN_PATTERN.matches(typed)) return
        val attempts = vault.attempts
        // Locked out: don't even check. The calculator just behaves as a calculator.
        if (!AttemptLimiter.canAttempt(attempts, System.currentTimeMillis())) return
        unlockInFlight = true
        val gen = lockGeneration
        viewModelScope.launch {
            // Distinguish three outcomes. Only a verifier mismatch (unlock
            // returns null) is a failed attempt. An exception from key
            // derivation is an internal error, not a wrong PIN. And a
            // correct PIN whose downstream open then fails must not burn
            // an attempt either — that was the 2026-10-28 wart, where
            // crash-interrupted correct-PIN opens locked the owner out.
            val k = try {
                withContext(Dispatchers.Default) { vault.unlock(typed.toCharArray()) }
            } catch (e: Exception) {
                unlockInFlight = false
                vault.attempts = AttemptLimiter.onInternalError(vault.attempts)
                return@launch
            }
            unlockInFlight = false
            if (k == null) {
                vault.attempts = AttemptLimiter.onFailure(vault.attempts, System.currentTimeMillis())
                return@launch
            }
            // PIN verified. Reset the limiter now, durably (the setter
            // commits), before anything downstream can fail or crash.
            vault.attempts = AttemptLimiter.onSuccess()
            if (gen != lockGeneration) {
                VaultCrypto.wipe(k)
                return@launch
            }
            // Leave no trace of the PIN on the calculator display.
            engine.press(CalcKey.Clear)
            _calc.update { it.copy(historyOpen = false) }
            publish()
            // openVault handles its own failures: on error it wipes the
            // key and returns to the calculator, with the limiter left
            // at the reset above — no attempt is burned.
            openVault(k)
        }
    }

    fun completeSetup(pin: String, confirm: String) {
        val error = when {
            !VaultRepository.PIN_PATTERN.matches(pin) -> "PIN must be 4 to 12 digits"
            pin != confirm -> "PINs don't match"
            else -> null
        }
        if (error != null) {
            _vault.update { it.copy(setupError = error) }
            return
        }
        if (vault.isConfigured) return
        _vault.update { it.copy(busy = true, setupError = null) }
        val gen = lockGeneration
        viewModelScope.launch {
            val k = try {
                withContext(Dispatchers.Default) { vault.setup(pin.toCharArray()) }
            } catch (e: Exception) {
                _vault.update { it.copy(busy = false, setupError = "Setup failed. Please try again.") }
                return@launch
            }
            // Backgrounded during setup: the vault exists but stays locked.
            if (gen != lockGeneration) VaultCrypto.wipe(k) else openVault(k)
        }
    }

    fun cancelSetup() {
        _vault.value = VaultUiState()
        _screen.value = Screen.CALCULATOR
    }

    /**
     * Open the vault UI for an already-verified [k]. Returns true on success.
     *
     * A failure loading notes is an internal error, not an auth failure:
     * it must neither crash the app nor burn an unlock attempt. On failure
     * the key is wiped, no decrypted state is kept, and the user is left
     * back on the calculator — indistinguishable from no vault at all.
     */
    private suspend fun openVault(k: ByteArray): Boolean {
        key = k
        _vault.value = VaultUiState(busy = true)
        _screen.value = Screen.VAULT
        val notes = try {
            withContext(Dispatchers.IO) { vault.loadNotes(k) }
        } catch (e: Exception) {
            if (key === k) {
                VaultCrypto.wipe(k)
                key = null
            }
            _vault.value = VaultUiState()
            _screen.value = Screen.CALCULATOR
            return false
        }
        if (key === k) _vault.value = VaultUiState(notes = notes)
        return key === k
    }

    /** Lock: drop the key and every decrypted note from memory. */
    fun lock() {
        lockGeneration++
        VaultCrypto.wipe(key)
        key = null
        _vault.value = VaultUiState()
        if (_screen.value != Screen.CALCULATOR) _screen.value = Screen.CALCULATOR
    }

    // ---- notepad ----

    fun newNote() {
        if (key == null) return
        // Not persisted until it has content; see saveNote / closeEditor.
        val note = Note(vault.newNoteId(), "", "", System.currentTimeMillis())
        _vault.update { it.copy(notes = listOf(note) + it.notes, editingId = note.id) }
    }

    fun openNote(id: String) = _vault.update { it.copy(editingId = id) }

    /**
     * Autosave, called on every edit. Sealing happens right here on the caller's
     * thread (AES-GCM on a note is microseconds), so an edit made just before the
     * app is backgrounded is already ciphertext when [lock] wipes the key; only
     * the disk write is deferred, in order, on a single IO lane.
     */
    fun saveNote(id: String, title: String, body: String) {
        val k = key ?: return
        val current = _vault.value.notes.firstOrNull { it.id == id } ?: return
        if (current.title == title && current.body == body) return
        val updated = current.copy(title = title, body = body, updatedAt = System.currentTimeMillis())
        _vault.update { s ->
            s.copy(notes = (listOf(updated) + s.notes.filter { it.id != id }).sortedByDescending { it.updatedAt })
        }
        val sealed = NoteCodec.seal(k, updated)
        viewModelScope.launch(writeLane) { vault.writeSealed(id, sealed) }
    }

    /** Leaving the editor: blank notes are discarded rather than kept as empty rows. */
    fun closeEditor() {
        val id = _vault.value.editingId ?: return
        val note = _vault.value.notes.firstOrNull { it.id == id }
        if (note != null && note.title.isBlank() && note.body.isBlank()) deleteNote(id)
        _vault.update { it.copy(editingId = null) }
    }

    fun deleteNote(id: String) {
        _vault.update { s -> s.copy(notes = s.notes.filter { it.id != id }, editingId = null) }
        viewModelScope.launch(writeLane) { vault.deleteNote(id) }
    }

    override fun onCleared() {
        lock()
    }
}
