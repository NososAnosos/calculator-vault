package ai.kbird.calculatorvault.calc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Result formatting. The stock app renders 99999999×99999999 in full
 * (9999999800000001, 16 digits; capture state 05), so up to [MAX_DIGITS]
 * significant digits are shown plainly before falling back to E notation.
 *
 * [format] returns the raw (ungrouped) text, which is also what the formula
 * holds when a result is continued. Grouping commas are a render-only layer
 * applied by [group], mirroring the stock app where the accessibility text is
 * unformatted (capture state 02).
 */
object NumberFormatter {
    const val MAX_DIGITS = 16
    private const val SCI_MANTISSA_DIGITS = 10
    private const val MIN_PLAIN_EXPONENT = -6
    const val MINUS = '−'

    fun format(v: BigDecimal): String {
        if (v.signum() == 0) return "0"
        val rounded = v.round(MathContext(MAX_DIGITS, RoundingMode.HALF_UP))
        val exp10 = rounded.precision() - rounded.scale() - 1
        val text = if (exp10 >= MAX_DIGITS || exp10 < MIN_PLAIN_EXPONENT) {
            scientific(v)
        } else {
            val intDigits = maxOf(exp10 + 1, 1)
            v.setScale(MAX_DIGITS - intDigits, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
        }
        return text.replace('-', MINUS)
    }

    private fun scientific(v: BigDecimal): String {
        val m = v.round(MathContext(SCI_MANTISSA_DIGITS, RoundingMode.HALF_UP)).stripTrailingZeros()
        val e = m.precision() - m.scale() - 1
        val mantissa = m.movePointLeft(e).stripTrailingZeros().toPlainString()
        return "${mantissa}E$e"
    }

    private val NUMBER = Regex("""(\d+)(\.\d*)?(E[−-]?\d+)?""")

    /** Insert grouping commas into the integer part of every number in [text]. */
    fun group(text: String): String = NUMBER.replace(text) { m ->
        val intPart = m.groupValues[1]
        val grouped = buildString(intPart.length + intPart.length / 3) {
            intPart.forEachIndexed { idx, c ->
                if (idx > 0 && (intPart.length - idx) % 3 == 0) append(',')
                append(c)
            }
        }
        grouped + m.groupValues[2] + m.groupValues[3]
    }
}
