package ai.kbird.calculatorvault.ui.theme

import android.graphics.fonts.SystemFonts
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Uses the fonts already installed on the device instead of shipping any: on a
 * Pixel the system carries Google Sans Flex, which the stock calculator renders
 * with. Nothing is bundled in the APK; on other devices this falls back to the
 * platform default.
 *
 * [DISPLAY_WIDTH] narrows the display digits to match the condensed look in the
 * captures — tune it during the side-by-side feel check.
 */
@OptIn(ExperimentalTextApi::class)
object DeviceFonts {
    private const val DISPLAY_WIDTH = 80f
    private const val KEY_WIDTH = 100f

    private val flexFile by lazy {
        runCatching {
            SystemFonts.getAvailableFonts()
                .mapNotNull { it.file }
                .firstOrNull { it.name.startsWith("GoogleSansFlex") && it.canRead() }
        }.getOrNull()
    }

    private val plainFile by lazy {
        runCatching {
            SystemFonts.getAvailableFonts()
                .mapNotNull { it.file }
                .firstOrNull { it.name == "GoogleSans-Regular.ttf" && it.canRead() }
        }.getOrNull()
    }

    private fun family(width: Float): FontFamily {
        flexFile?.let { file ->
            return runCatching {
                FontFamily(
                    Font(
                        file,
                        weight = FontWeight.Normal,
                        variationSettings = FontVariation.Settings(
                            FontVariation.weight(400),
                            FontVariation.width(width),
                        ),
                    ),
                )
            }.getOrDefault(FontFamily.Default)
        }
        plainFile?.let { file ->
            return runCatching { FontFamily(Font(file)) }.getOrDefault(FontFamily.Default)
        }
        return FontFamily.Default
    }

    val display: FontFamily by lazy { family(DISPLAY_WIDTH) }
    val keys: FontFamily by lazy { family(KEY_WIDTH) }
}
