package ai.kbird.calculatorvault.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min

/*
 * KbirdPad — reusable Compose keypad.
 *
 * Self-contained on purpose: this file depends only on Compose + Material3, not on
 * anything else in the calculator, so it can be copied into AllStar Explorer as a
 * DTMF pad unchanged. The caller supplies rows of [PadKey]s, colours, and a
 * cell aspect ratio; the pad handles geometry, press morph, ripple, and haptics.
 *
 * Geometry defaults come from the stock calculator capture (1080 px wide window):
 * 9 px edge margin, 8 px gaps, 260 x 250 px cells, keys filling their cell with
 * fully rounded corners (circles in a square-ish cell, pills in a short cell).
 */

enum class KeyRole { Digit, Operator, Function, Clear, Equals }

@Immutable
data class PadKey(
    val id: String,
    val label: String? = null,
    val icon: ImageVector? = null,
    val role: KeyRole = KeyRole.Digit,
    val contentDescription: String = label ?: id,
)

@Immutable
data class KbirdPadColors(
    val digitContainer: Color,
    val digitContent: Color,
    val operatorContainer: Color,
    val operatorContent: Color,
    val functionContainer: Color,
    val functionContent: Color,
    val clearContainer: Color,
    val clearContent: Color,
    val equalsContainer: Color,
    val equalsContent: Color,
) {
    fun container(role: KeyRole) = when (role) {
        KeyRole.Digit -> digitContainer
        KeyRole.Operator -> operatorContainer
        KeyRole.Function -> functionContainer
        KeyRole.Clear -> clearContainer
        KeyRole.Equals -> equalsContainer
    }

    fun content(role: KeyRole) = when (role) {
        KeyRole.Digit -> digitContent
        KeyRole.Operator -> operatorContent
        KeyRole.Function -> functionContent
        KeyRole.Clear -> clearContent
        KeyRole.Equals -> equalsContent
    }
}

@Immutable
data class KbirdPadStyle(
    val colors: KbirdPadColors,
    /** Row height / cell width. Stock basic pad: 250/260; scientific rows: 154/260. */
    val cellAspect: Float = 250f / 260f,
    /** Edge margin and gaps as fractions of pad width (9 px and 8 px of 1080). */
    val edgeFraction: Float = 9f / 1080f,
    val gapFraction: Float = 8f / 1080f,
    /** Label font size as a fraction of the key height (role-specific). */
    val labelScale: Float = 0.46f,
    val functionLabelScale: Float = 0.36f,
    /** Corner radius as % of the short side when idle and while pressed. */
    val idleCornerPercent: Int = 50,
    val pressedCornerPercent: Int = 30,
    /** How far the container lightens towards white while pressed. */
    val pressedLighten: Float = 0.12f,
    val fontFamily: FontFamily = FontFamily.Default,
    val haptics: Boolean = true,
)

/**
 * @param rows keys laid out row by row; rows may have different lengths but are
 *   sized by the widest row.
 * @param onLongPress return true to consume a long press (otherwise it is ignored).
 */
@Composable
fun KbirdPad(
    rows: List<List<PadKey>>,
    style: KbirdPadStyle,
    onKey: (PadKey) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((PadKey) -> Boolean)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = rows.maxOf { it.size }
        val width = maxWidth
        val edge = width * style.edgeFraction
        val gap = width * style.gapFraction
        val cellWidth = (width - edge * 2 - gap * (columns - 1)) / columns
        var rowHeight = cellWidth * style.cellAspect
        // Compress rows if the parent gives less height than the natural size.
        if (constraints.hasBoundedHeight) {
            val fit = (maxHeight - edge * 2 - gap * (rows.size - 1)) / rows.size
            rowHeight = min(rowHeight, fit)
        }
        Column(
            Modifier.padding(horizontal = edge, vertical = edge),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            for (row in rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (key in row) {
                        PadButton(
                            key = key,
                            style = style,
                            width = cellWidth,
                            height = rowHeight,
                            onClick = { onKey(key) },
                            onLongClick = onLongPress?.let { lp -> { lp(key) } },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PadButton(
    key: PadKey,
    style: KbirdPadStyle,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    onLongClick: (() -> Boolean)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val view = LocalView.current

    LaunchedEffect(pressed) {
        if (pressed && style.haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    val corner by animateIntAsState(
        if (pressed) style.pressedCornerPercent else style.idleCornerPercent,
        animationSpec = tween(durationMillis = 120),
        label = "corner",
    )
    val baseColor = style.colors.container(key.role)
    val container by animateColorAsState(
        if (pressed) lerp(baseColor, Color.White, style.pressedLighten) else baseColor,
        animationSpec = tween(durationMillis = 120),
        label = "container",
    )
    val content = style.colors.content(key.role)
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = Modifier
            .size(width, height)
            .clip(shape)
            .background(container)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(color = content),
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick?.let { lc -> { lc(); Unit } },
            )
            .semantics { contentDescription = key.contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val scale = if (key.role == KeyRole.Function) style.functionLabelScale else style.labelScale
        val fontPx = with(density) { height.toPx() } * scale
        when {
            key.icon != null -> Icon(
                imageVector = key.icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(height * scale * 0.9f),
            )
            key.label != null -> Text(
                text = key.label,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = TextStyle(
                    fontFamily = style.fontFamily,
                    fontSize = with(density) { fontPx.toSp() },
                ),
            )
        }
    }
}

/** Convenience: a phone-style 4x3 DTMF layout (1-9, *, 0, #) for reuse. */
fun dtmfRows(): List<List<PadKey>> = listOf(
    listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#"),
).map { row -> row.map { PadKey(id = it, label = it) } }
