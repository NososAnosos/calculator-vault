package ai.kbird.calculatorvault

import ai.kbird.calculatorvault.calc.CalcKey
import ai.kbird.calculatorvault.calc.HistoryEntry
import ai.kbird.calculatorvault.ui.CalculatorActions
import ai.kbird.calculatorvault.ui.CalculatorScreen
import ai.kbird.calculatorvault.ui.VaultActions
import ai.kbird.calculatorvault.ui.VaultScreen
import ai.kbird.calculatorvault.ui.VaultSetupScreen
import ai.kbird.calculatorvault.ui.theme.CalcTheme
import ai.kbird.calculatorvault.ui.theme.ThemeChoice
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val calcActions = object : CalculatorActions {
        override fun press(key: CalcKey) = vm.press(key)
        override fun pressEquals() = vm.pressEquals()
        override fun equalsLongPress() = vm.equalsLongPress()
        override fun toggleScientific() = vm.toggleScientific()
        override fun toggleInverse() = vm.toggleInverse()
        override fun toggleDegrees() = vm.toggleDegrees()
        override fun setHistoryOpen(open: Boolean) = vm.setHistoryOpen(open)
        override fun restoreHistory(entry: HistoryEntry) = vm.restoreHistory(entry)
        override fun clearHistory() = vm.clearHistory()
        override fun setTheme(choice: ThemeChoice) = vm.setTheme(choice)
    }

    private val vaultActions = object : VaultActions {
        override fun completeSetup(pin: String, confirm: String) = vm.completeSetup(pin, confirm)
        override fun cancelSetup() = vm.cancelSetup()
        override fun lock() = vm.lock()
        override fun newNote() = vm.newNote()
        override fun openNote(id: String) = vm.openNote(id)
        override fun saveNote(id: String, title: String, body: String) = vm.saveNote(id, title, body)
        override fun closeEditor() = vm.closeEditor()
        override fun deleteNote(id: String) = vm.deleteNote(id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val screen by vm.screen.collectAsStateWithLifecycle()
            val theme by vm.theme.collectAsStateWithLifecycle()
            val calc by vm.calc.collectAsStateWithLifecycle()
            val vault by vm.vaultState.collectAsStateWithLifecycle()

            LaunchedEffect(screen) { setPrivateWindow(screen != Screen.CALCULATOR) }

            CalcTheme(theme) {
                when (screen) {
                    Screen.CALCULATOR -> CalculatorScreen(calc, theme, calcActions)
                    Screen.VAULT_SETUP -> VaultSetupScreen(vault, vaultActions)
                    Screen.VAULT -> VaultScreen(vault, vaultActions)
                }
            }
        }
    }

    /**
     * While the vault (or setup) is showing: no screenshots, no recents thumbnail.
     * The calculator itself stays normal so it looks like any calculator in recents.
     */
    private fun setPrivateWindow(secure: Boolean) {
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!secure)
    }

    /** Leaving the foreground locks the vault. Rotation does not recreate (configChanges). */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.lock()
    }
}
