package ai.kbird.calculatorvault.ui

import ai.kbird.calculatorvault.calc.DisplayMode
import ai.kbird.calculatorvault.calc.DisplayState
import ai.kbird.calculatorvault.calc.NumberFormatter
import ai.kbird.calculatorvault.ui.theme.DeviceFonts
import ai.kbird.calculatorvault.ui.theme.LocalCalcColors
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Formula + result display. Proportions are fractions of window width taken from
 * capture state 01 (1080 px): formula area 262 px, result line 175 px, text end
 * inset 52 px; formula glyphs ~176 px font, result preview ~79 px.
 */
object DisplayMetrics {
    const val FORMULA_HEIGHT = 262f / 1080f
    const val RESULT_HEIGHT = 175f / 1080f
    const val END_INSET = 52f / 1080f
    const val FORMULA_FONT = 176f / 1080f
    const val FORMULA_MIN_FONT = 88f / 1080f
    const val PREVIEW_FONT = 79f / 1080f
}

@Composable
fun CalculatorDisplay(state: DisplayState, windowWidth: Dp, modifier: Modifier = Modifier) {
    val colors = LocalCalcColors.current
    val density = LocalDensity.current
    fun sp(fraction: Float) = with(density) { (windowWidth * fraction).toSp() }

    val formulaColor = when (state.mode) {
        DisplayMode.EDIT -> colors.formula
        DisplayMode.RESULT -> colors.result
        DisplayMode.ERROR -> colors.error
    }
    val formulaDesc = state.formula.ifEmpty { "No formula" }
    val resultText = when (state.mode) {
        DisplayMode.ERROR -> state.errorMessage.orEmpty()
        else -> state.preview.let(NumberFormatter::group)
    }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(windowWidth * DisplayMetrics.FORMULA_HEIGHT)
                .padding(end = windowWidth * DisplayMetrics.END_INSET)
                .semantics { contentDescription = formulaDesc },
            contentAlignment = Alignment.CenterEnd,
        ) {
            AutoSizeLine(
                text = NumberFormatter.group(state.formula),
                color = formulaColor,
                maxSize = sp(DisplayMetrics.FORMULA_FONT),
                minSize = sp(DisplayMetrics.FORMULA_MIN_FONT),
                fontFamily = DeviceFonts.display,
                showCursor = state.mode != DisplayMode.RESULT,
                cursorHeight = windowWidth * DisplayMetrics.FORMULA_HEIGHT * 0.7f,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(windowWidth * DisplayMetrics.RESULT_HEIGHT)
                .padding(end = windowWidth * DisplayMetrics.END_INSET)
                .semantics { contentDescription = resultText.ifEmpty { "No result" } },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = resultText,
                color = if (state.mode == DisplayMode.ERROR) colors.error else colors.result,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(fontFamily = DeviceFonts.display, fontSize = sp(DisplayMetrics.PREVIEW_FONT)),
            )
        }
    }
}

/**
 * One line of right-aligned text that shrinks from [maxSize] to [minSize] to fit
 * the width, then scrolls horizontally (pinned to the end) beyond that.
 */
@Composable
fun AutoSizeLine(
    text: String,
    color: Color,
    maxSize: TextUnit,
    minSize: TextUnit,
    fontFamily: FontFamily,
    showCursor: Boolean,
    cursorHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCalcColors.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        val available = constraints.maxWidth
        val cursorReserve = with(LocalDensity.current) { 6.dp.roundToPx() }
        val size = remember(text, available, maxSize, minSize) {
            var s = maxSize.value
            while (s > minSize.value) {
                val w = measurer.measure(
                    text,
                    TextStyle(fontFamily = fontFamily, fontSize = s.sp),
                    softWrap = false,
                    maxLines = 1,
                ).size.width
                if (w + cursorReserve <= available) break
                s -= 2f
            }
            s.coerceAtLeast(minSize.value).sp
        }
        val scroll = rememberScrollState()
        LaunchedEffect(text, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }

        var cursorOn by remember { mutableStateOf(true) }
        LaunchedEffect(showCursor, text) {
            cursorOn = true
            while (showCursor) {
                delay(500)
                cursorOn = !cursorOn
            }
        }

        Row(Modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                color = color,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                style = TextStyle(fontFamily = fontFamily, fontSize = size),
            )
            if (showCursor) {
                Spacer(Modifier.width(2.dp))
                Box(
                    Modifier
                        .width(2.dp)
                        .height(cursorHeight)
                        .alpha(if (cursorOn) 1f else 0f)
                        .background(colors.cursor),
                )
            }
        }
    }
}
