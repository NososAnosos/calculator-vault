package ai.kbird.calculatorvault.ui

import ai.kbird.calculatorvault.VaultUiState
import ai.kbird.calculatorvault.vault.Note
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.text.DateFormat
import java.util.Date

/** Callbacks from the vault screens to the view model. */
interface VaultActions {
    fun completeSetup(pin: String, confirm: String)
    fun cancelSetup()
    fun lock()
    fun newNote()
    fun openNote(id: String)
    fun saveNote(id: String, title: String, body: String)
    fun closeEditor()
    fun deleteNote(id: String)
}

/**
 * First-run setup, reached by the v1 placeholder gesture (long-press "=" on an
 * empty display while no vault exists).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSetupScreen(state: VaultUiState, actions: VaultActions) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    BackHandler { actions.cancelSetup() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set up vault") },
                navigationIcon = {
                    IconButton(onClick = actions::cancelSetup) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Choose a PIN of 4 to 12 digits. To open the vault, type the PIN on the " +
                    "calculator and press =. A wrong PIN is just a number.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "There is no recovery: if you forget the PIN, the notes cannot be decrypted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            PinField(pin, "PIN") { pin = it }
            PinField(confirm, "Confirm PIN") { confirm = it }
            state.setupError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { actions.completeSetup(pin, confirm) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.busy) CircularProgressIndicator(Modifier.height(20.dp)) else Text("Create vault")
            }
        }
    }
}

@Composable
private fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(12)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun VaultScreen(state: VaultUiState, actions: VaultActions) {
    val editing = state.editingId?.let { id -> state.notes.firstOrNull { it.id == id } }
    // key(): each note gets fresh EditText views rather than reusing another note's.
    if (editing != null) key(editing.id) { NoteEditor(editing, actions) } else NotesList(state, actions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesList(state: VaultUiState, actions: VaultActions) {
    // Back from the list leaves the vault entirely, locked.
    BackHandler { actions.lock() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notes") },
                actions = {
                    IconButton(onClick = actions::lock) {
                        Icon(Icons.Filled.Lock, contentDescription = "Lock")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.busy) {
                FloatingActionButton(onClick = actions::newNote) {
                    Icon(Icons.Filled.Add, contentDescription = "New note")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.busy -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.notes.isEmpty() -> Text(
                    "No notes yet",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.notes, key = { it.id }) { note ->
                        NoteRow(note) { actions.openNote(note.id) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            note.title.ifBlank { note.body.lineSequence().firstOrNull { it.isNotBlank() } ?: "Untitled" },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note.updatedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditor(note: Note, actions: VaultActions) {
    // Local copies; every change is autosaved (sealed immediately) via saveNote.
    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(note.body) }
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler { actions.closeEditor() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = actions::closeEditor) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete note")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).imePadding()) {
            PrivateTextField(
                value = title,
                hint = "Title",
                singleLine = true,
                textSizeSp = 22f,
                bold = true,
                onChange = { title = it; actions.saveNote(note.id, it, body) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            PrivateTextField(
                value = body,
                hint = "Note",
                singleLine = false,
                textSizeSp = 16f,
                bold = false,
                onChange = { body = it; actions.saveNote(note.id, title, it) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete note?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; actions.deleteNote(note.id) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Platform EditText so we can set IME_FLAG_NO_PERSONALIZED_LEARNING (Compose's
 * KeyboardOptions cannot): the keyboard must not learn words typed in the vault.
 * Suggestions are off too, so no vault text lands in the keyboard's dictionary.
 */
@Composable
private fun PrivateTextField(
    value: String,
    hint: String,
    singleLine: Boolean,
    textSizeSp: Float,
    bold: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            EditText(ctx).apply {
                background = null
                gravity = Gravity.TOP or Gravity.START
                imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
                    (if (singleLine) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_NONE)
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    (if (singleLine) 0 else InputType.TYPE_TEXT_FLAG_MULTI_LINE)
                isSingleLine = singleLine
                textSize = textSizeSp
                if (bold) setTypeface(typeface, Typeface.BOLD)
                setText(value)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        val text = s?.toString().orEmpty()
                        if (text != (tag as? String)) {
                            tag = text
                            onChange(text)
                        }
                    }
                })
                tag = value
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setHintTextColor(hintColor)
            view.hint = hint
        },
    )
}
