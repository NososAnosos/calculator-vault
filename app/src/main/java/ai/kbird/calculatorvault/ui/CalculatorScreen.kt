package ai.kbird.calculatorvault.ui

import ai.kbird.calculatorvault.CalcUiState
import ai.kbird.calculatorvault.calc.CalcKey
import ai.kbird.calculatorvault.calc.HistoryEntry
import ai.kbird.calculatorvault.calc.NumberFormatter
import ai.kbird.calculatorvault.ui.theme.DeviceFonts
import ai.kbird.calculatorvault.ui.theme.LocalCalcColors
import ai.kbird.calculatorvault.ui.theme.ThemeChoice
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.content.res.Configuration

/** Callbacks from the calculator surface to the view model. */
interface CalculatorActions {
    fun press(key: CalcKey)
    fun pressEquals()
    fun equalsLongPress(): Boolean
    fun toggleScientific()
    fun toggleInverse()
    fun toggleDegrees()
    fun setHistoryOpen(open: Boolean)
    fun restoreHistory(entry: HistoryEntry)
    fun clearHistory()
    fun setTheme(choice: ThemeChoice)
}

/** Vertical metrics as fractions of window width, from capture state 01 (1080 px). */
private object Layout {
    const val TOOLBAR = 182f / 1080f
    const val ICON_BUTTON = 107f / 1080f
    const val TOOLBAR_START = 27f / 1080f
    const val CHEVRON_ROW = 198f / 1080f
    const val CHEVRON_START = 29f / 1080f
    const val HISTORY_START = 47f / 1080f
    const val STANDARD_ASPECT = 250f / 260f
    const val SCIENTIFIC_ASPECT = 154f / 260f
    const val HISTORY_ASPECT = 188f / 260f
}

@Composable
fun CalculatorScreen(
    state: CalcUiState,
    theme: ThemeChoice,
    actions: CalculatorActions,
) {
    val colors = LocalCalcColors.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler(enabled = state.historyOpen) { actions.setHistoryOpen(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val w = maxWidth
        when {
            landscape -> LandscapeLayout(state, theme, actions, w)
            state.historyOpen -> HistoryLayout(state, theme, actions, w)
            else -> PortraitLayout(state, theme, actions, w)
        }
    }
}

@Composable
private fun PortraitLayout(state: CalcUiState, theme: ThemeChoice, actions: CalculatorActions, w: Dp) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Toolbar(state, theme, actions, w, height = w * Layout.TOOLBAR)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pullDownToOpen { actions.setHistoryOpen(true) },
        ) {
            CalculatorDisplay(state.display, windowWidth = w)
        }
        ChevronRow(state.scientific, actions, w)
        CalcPad(
            state = state,
            actions = actions,
            rows = if (state.scientific) scientificRows(state) + standardRows() else standardRows(),
            aspect = if (state.scientific) Layout.SCIENTIFIC_ASPECT else Layout.STANDARD_ASPECT,
        )
    }
}

@Composable
private fun HistoryLayout(state: CalcUiState, theme: ThemeChoice, actions: CalculatorActions, w: Dp) {
    val colors = LocalCalcColors.current
    val density = LocalDensity.current
    fun sp(fraction: Float) = with(density) { (w * fraction).toSp() }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(colors.historyPanel)
                .statusBarsPadding(),
        ) {
            Toolbar(state, theme, actions, w, height = w * Layout.TOOLBAR)
            if (state.history.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No History", color = colors.historyFormula, style = TextStyle(fontSize = sp(40f / 1080f)))
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = "Show more history" },
                    reverseLayout = true,
                ) {
                    items(state.history.asReversed()) { entry ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { actions.restoreHistory(entry) }
                                .padding(horizontal = w * (52f / 1080f), vertical = 6.dp),
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(
                                NumberFormatter.group(entry.formula),
                                color = colors.historyFormula,
                                maxLines = 1,
                                style = TextStyle(fontFamily = DeviceFonts.display, fontSize = sp(64f / 1080f)),
                            )
                            Text(
                                NumberFormatter.group(entry.result),
                                color = colors.historyResult,
                                maxLines = 1,
                                style = TextStyle(fontFamily = DeviceFonts.display, fontSize = sp(96f / 1080f)),
                            )
                        }
                    }
                }
            }
        }
        // Drag handle: dragging up (or tapping) closes the history.
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -40f) actions.setHistoryOpen(false) },
                    ) { _, dy -> total += dy }
                }
                .clickable { actions.setHistoryOpen(false) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.handle),
            )
        }
        Text(
            "Current expression",
            color = colors.formula,
            modifier = Modifier.padding(start = w * Layout.HISTORY_START, top = 4.dp, bottom = 4.dp),
            style = TextStyle(fontSize = sp(40f / 1080f)),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(end = w * (52f / 1080f))
                .height(w * (120f / 1080f)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                NumberFormatter.group(state.display.formula),
                color = colors.formula,
                maxLines = 1,
                style = TextStyle(fontFamily = DeviceFonts.display, fontSize = sp(80f / 1080f)),
            )
        }
        ChevronRow(state.scientific, actions, w)
        Box(Modifier.navigationBarsPadding()) {
            CalcPad(
                state = state,
                actions = actions,
                rows = if (state.scientific) scientificRows(state) + standardRows() else standardRows(),
                aspect = if (state.scientific) Layout.SCIENTIFIC_ASPECT * 0.75f else Layout.HISTORY_ASPECT,
            )
        }
    }
}

/**
 * Landscape: display on top, scientific and basic pads side by side.
 * Not capture-verified (no landscape capture yet) — approximates the stock layout.
 */
@Composable
private fun LandscapeLayout(state: CalcUiState, theme: ThemeChoice, actions: CalculatorActions, w: Dp) {
    // Size display text off the portrait-equivalent width so it is not huge.
    val displayWidth = w * 0.5f
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Toolbar(state, theme, actions, displayWidth, height = 48.dp)
        Box(Modifier.fillMaxWidth().weight(0.38f).pullDownToOpen { actions.setHistoryOpen(true) }) {
            CalculatorDisplay(state.display, windowWidth = displayWidth, modifier = Modifier.align(Alignment.BottomEnd))
        }
        Row(Modifier.fillMaxWidth().weight(0.62f)) {
            CalcPad(state, actions, scientificRows(state), aspect = 1f, modifier = Modifier.weight(1f))
            CalcPad(state, actions, standardRows(), aspect = 1f, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CalcPad(
    state: CalcUiState,
    actions: CalculatorActions,
    rows: List<List<PadKey>>,
    aspect: Float,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCalcColors.current
    val style = remember(colors, aspect) {
        KbirdPadStyle(
            colors = KbirdPadColors(
                digitContainer = colors.digitKey,
                digitContent = colors.digitText,
                operatorContainer = colors.opKey,
                operatorContent = colors.opText,
                functionContainer = colors.opKey,
                functionContent = colors.opText,
                clearContainer = colors.clearKey,
                clearContent = colors.clearText,
                equalsContainer = colors.equalsKey,
                equalsContent = colors.equalsText,
            ),
            cellAspect = aspect,
            fontFamily = DeviceFonts.keys,
        )
    }
    KbirdPad(
        rows = rows,
        style = style,
        modifier = modifier,
        onKey = { key -> dispatch(key.id, state, actions) },
        onLongPress = { key ->
            when (key.id) {
                "eq" -> actions.equalsLongPress()
                "del" -> { actions.press(CalcKey.Clear); true }
                else -> false
            }
        },
    )
}

private fun dispatch(id: String, state: CalcUiState, actions: CalculatorActions) {
    if (id.startsWith("digit_")) {
        actions.press(CalcKey.Digit(id.removePrefix("digit_").toInt()))
        return
    }
    val inv = state.inverse
    when (id) {
        "dec_point" -> actions.press(CalcKey.Point)
        "op_add" -> actions.press(CalcKey.Op('+'))
        "op_sub" -> actions.press(CalcKey.Op('−'))
        "op_mul" -> actions.press(CalcKey.Op('×'))
        "op_div" -> actions.press(CalcKey.Op('÷'))
        "op_pct" -> actions.press(CalcKey.Percent)
        "parens" -> actions.press(CalcKey.Parens)
        "clr" -> actions.press(CalcKey.Clear)
        "del" -> actions.press(CalcKey.Delete)
        "eq" -> actions.pressEquals()
        "op_sqrt" -> actions.press(if (inv) CalcKey.Square else CalcKey.Sqrt)
        "const_pi" -> actions.press(CalcKey.Pi)
        "op_pow" -> actions.press(CalcKey.Op('^'))
        "op_fact" -> actions.press(CalcKey.Factorial)
        "toggle_mode" -> actions.toggleDegrees()
        "fun_sin" -> actions.press(CalcKey.Func(if (inv) "sin⁻¹" else "sin"))
        "fun_cos" -> actions.press(CalcKey.Func(if (inv) "cos⁻¹" else "cos"))
        "fun_tan" -> actions.press(CalcKey.Func(if (inv) "tan⁻¹" else "tan"))
        "toggle_inv" -> actions.toggleInverse()
        "const_e" -> actions.press(CalcKey.Euler)
        "fun_ln" -> actions.press(if (inv) CalcKey.ExpE else CalcKey.Func("ln"))
        "fun_log" -> actions.press(if (inv) CalcKey.Exp10 else CalcKey.Func("log"))
    }
}

/** Ids and content descriptions follow the stock app's (capture XML). */
private fun standardRows(): List<List<PadKey>> = listOf(
    listOf(
        PadKey("clr", "AC", role = KeyRole.Clear, contentDescription = "all clear"),
        PadKey("parens", "( )", role = KeyRole.Operator, contentDescription = "left or right parenthesis"),
        PadKey("op_pct", "%", role = KeyRole.Operator, contentDescription = "percent"),
        PadKey("op_div", "÷", role = KeyRole.Operator, contentDescription = "divide"),
    ),
    listOf(digit(7), digit(8), digit(9), PadKey("op_mul", "×", role = KeyRole.Operator, contentDescription = "multiply")),
    listOf(digit(4), digit(5), digit(6), PadKey("op_sub", "−", role = KeyRole.Operator, contentDescription = "minus")),
    listOf(digit(1), digit(2), digit(3), PadKey("op_add", "+", role = KeyRole.Operator, contentDescription = "plus")),
    listOf(
        digit(0),
        PadKey("dec_point", ".", contentDescription = "point"),
        PadKey("del", icon = Icons.AutoMirrored.Outlined.Backspace, contentDescription = "delete"),
        PadKey("eq", "=", role = KeyRole.Equals, contentDescription = "equals"),
    ),
)

private fun digit(d: Int) = PadKey("digit_$d", d.toString(), contentDescription = d.toString())

private fun scientificRows(state: CalcUiState): List<List<PadKey>> {
    val inv = state.inverse
    val f = KeyRole.Function
    return listOf(
        listOf(
            if (inv) PadKey("op_sqrt", "x²", role = f, contentDescription = "square")
            else PadKey("op_sqrt", "√", role = f, contentDescription = "square root"),
            PadKey("const_pi", "π", role = f, contentDescription = "pi"),
            PadKey("op_pow", "^", role = f, contentDescription = "power"),
            PadKey("op_fact", "!", role = f, contentDescription = "factorial"),
        ),
        listOf(
            if (state.degrees) PadKey("toggle_mode", "Rad", role = f, contentDescription = "switch to radians")
            else PadKey("toggle_mode", "Deg", role = f, contentDescription = "switch to degrees"),
            PadKey("fun_sin", if (inv) "sin⁻¹" else "sin", role = f, contentDescription = if (inv) "arcsine" else "sine"),
            PadKey("fun_cos", if (inv) "cos⁻¹" else "cos", role = f, contentDescription = if (inv) "arccosine" else "cosine"),
            PadKey("fun_tan", if (inv) "tan⁻¹" else "tan", role = f, contentDescription = if (inv) "arctangent" else "tangent"),
        ),
        listOf(
            PadKey(
                "toggle_inv", "Inv",
                role = if (inv) KeyRole.Clear else f,
                contentDescription = if (inv) "hide inverse functions" else "show inverse functions",
            ),
            PadKey("const_e", "e", role = f, contentDescription = "Euler's number"),
            PadKey("fun_ln", if (inv) "eˣ" else "ln", role = f, contentDescription = if (inv) "exponential" else "natural logarithm"),
            PadKey("fun_log", if (inv) "10ˣ" else "log", role = f, contentDescription = if (inv) "power of ten" else "logarithm"),
        ),
    )
}

@Composable
private fun Toolbar(
    state: CalcUiState,
    theme: ThemeChoice,
    actions: CalculatorActions,
    w: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var themeDialog by rememberSaveable { mutableStateOf(false) }
    val button = w * Layout.ICON_BUTTON

    Row(
        modifier.fillMaxWidth().height(height).padding(start = w * Layout.TOOLBAR_START),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            icon = Icons.Outlined.History,
            description = if (state.historyOpen) "History mode on" else "History mode off",
            size = button,
            active = state.historyOpen,
            onClick = { actions.setHistoryOpen(!state.historyOpen) },
        )
        Spacer(Modifier.weight(1f))
        Box {
            RoundIconButton(
                icon = Icons.Filled.MoreVert,
                description = "More options",
                size = button,
                active = false,
                onClick = { menuOpen = true },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Choose theme") }, onClick = { menuOpen = false; themeDialog = true })
                if (state.history.isNotEmpty()) {
                    DropdownMenuItem(text = { Text("Clear history") }, onClick = { menuOpen = false; actions.clearHistory() })
                }
            }
        }
    }
    if (themeDialog) ThemeDialog(theme, onPick = { actions.setTheme(it); themeDialog = false }, onDismiss = { themeDialog = false })
}

@Composable
private fun ThemeDialog(current: ThemeChoice, onPick: (ThemeChoice) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(ThemeChoice.LIGHT to "Light", ThemeChoice.DARK to "Dark", ThemeChoice.SYSTEM to "System default")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose theme") },
        text = {
            Column {
                options.forEach { (choice, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = choice == current, role = Role.RadioButton) { onPick(choice) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == current, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChevronRow(scientific: Boolean, actions: CalculatorActions, w: Dp) {
    Row(
        Modifier.fillMaxWidth().height(w * Layout.CHEVRON_ROW).padding(start = w * Layout.CHEVRON_START),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RoundIconButton(
            icon = if (scientific) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
            description = if (scientific) "Hide scientific buttons" else "Show scientific buttons",
            size = w * Layout.ICON_BUTTON,
            active = scientific,
            activeColor = LocalCalcColors.current.digitKey,
            onClick = actions::toggleScientific,
        )
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    active: Boolean,
    onClick: () -> Unit,
    activeColor: androidx.compose.ui.graphics.Color = LocalCalcColors.current.iconActiveBg,
) {
    val colors = LocalCalcColors.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) activeColor else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.icon, modifier = Modifier.size(size * 0.4f))
    }
}

/** Downward drag on the display pulls the history open (part of the disguise). */
private fun Modifier.pullDownToOpen(onOpen: () -> Unit): Modifier = pointerInput(Unit) {
    var total = 0f
    detectVerticalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = { if (total > 80f) onOpen() },
    ) { _, dy -> total += dy }
}
