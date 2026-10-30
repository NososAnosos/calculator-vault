package ai.kbird.calculatorvault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Calculator palette. [Dark] is sampled by eye from reference/capture (Pixel 11,
 * dark theme, Oct 28 2026) — re-sample precisely during the feel check.
 * [Light] is NOT captured yet; it is an estimate so the theme picker works.
 *
 * Note: the stock app probably derives these from the wallpaper (Material You
 * dynamic color). The capture device's wallpaper produced the blue-gray/lavender
 * set below; a different wallpaper will diverge until dynamic mapping is done.
 */
@Immutable
data class CalcColors(
    val background: Color,
    val digitKey: Color,
    val digitText: Color,
    val opKey: Color,
    val opText: Color,
    val clearKey: Color,
    val clearText: Color,
    val equalsKey: Color,
    val equalsText: Color,
    val formula: Color,
    val result: Color,
    val error: Color,
    val cursor: Color,
    val icon: Color,
    val iconActiveBg: Color,
    val historyPanel: Color,
    val historyFormula: Color,
    val historyResult: Color,
    val handle: Color,
) {
    companion object {
        val Dark = CalcColors(
            background = Color(0xFF0F0F14),
            digitKey = Color(0xFF24252E),
            digitText = Color(0xFFE3E2EA),
            opKey = Color(0xFF393C51),
            opText = Color(0xFFC6C8E4),
            clearKey = Color(0xFF4B4F78),
            clearText = Color(0xFFDFE0FF),
            equalsKey = Color(0xFFF7DEF8),
            equalsText = Color(0xFF6B5470),
            formula = Color(0xFFE3E1EC),
            result = Color(0xFFF2DAF4),
            error = Color(0xFFF07A8B),
            cursor = Color(0xFFA8AED8),
            icon = Color(0xFFC6C5D0),
            iconActiveBg = Color(0xFF2F3141),
            historyPanel = Color(0xFF1B1C23),
            historyFormula = Color(0xFF8F9099),
            historyResult = Color(0xFFC7C7CF),
            handle = Color(0xFF8F9099),
        )

        val Light = CalcColors(
            background = Color(0xFFFBF8FF),
            digitKey = Color(0xFFECEBF4),
            digitText = Color(0xFF1B1B21),
            opKey = Color(0xFFDDE1F9),
            opText = Color(0xFF3C4058),
            clearKey = Color(0xFFC4C8EE),
            clearText = Color(0xFF151A3C),
            equalsKey = Color(0xFF6E4F72),
            equalsText = Color(0xFFFFFFFF),
            formula = Color(0xFF1B1B21),
            result = Color(0xFF6E4F72),
            error = Color(0xFFBA1A1A),
            cursor = Color(0xFF5A5F8A),
            icon = Color(0xFF45464F),
            iconActiveBg = Color(0xFFE2E1EC),
            historyPanel = Color(0xFFF0EEF7),
            historyFormula = Color(0xFF6F7079),
            historyResult = Color(0xFF45464F),
            handle = Color(0xFF6F7079),
        )
    }
}

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

val LocalCalcColors = staticCompositionLocalOf { CalcColors.Dark }

@Composable
fun CalcTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val colors = if (dark) CalcColors.Dark else CalcColors.Light
    val scheme = if (dark) {
        darkColorScheme(background = colors.background, surface = colors.background)
    } else {
        lightColorScheme(background = colors.background, surface = colors.background)
    }
    CompositionLocalProvider(LocalCalcColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
