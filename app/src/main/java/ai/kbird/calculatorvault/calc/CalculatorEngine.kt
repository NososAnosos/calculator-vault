package ai.kbird.calculatorvault.calc

/** A key press, independent of how the pad renders it. */
sealed interface CalcKey {
    data class Digit(val d: Int) : CalcKey
    data object Point : CalcKey
    /** Binary operator: '+', '−', '×', '÷', '^'. */
    data class Op(val c: Char) : CalcKey
    data object Percent : CalcKey
    data object Factorial : CalcKey
    data object Parens : CalcKey
    /** Inserts "name(" — sin, cos, tan, sin⁻¹, cos⁻¹, tan⁻¹, ln, log. */
    data class Func(val name: String) : CalcKey
    data object Sqrt : CalcKey
    data object Square : CalcKey
    data object ExpE : CalcKey
    data object Exp10 : CalcKey
    data object Pi : CalcKey
    data object Euler : CalcKey
    data object Clear : CalcKey
    data object Delete : CalcKey
}

enum class DisplayMode { EDIT, RESULT, ERROR }

data class HistoryEntry(val formula: String, val result: String)

data class DisplayState(
    /** Raw formula (no grouping commas), or the result after equals. */
    val formula: String = "",
    /** Live result preview while typing; empty when there is nothing to show. */
    val preview: String = "",
    val mode: DisplayMode = DisplayMode.EDIT,
    val errorMessage: String? = null,
)

/**
 * Editing model behind the pad. Pure Kotlin so it is unit-testable on the JVM.
 * The formula is kept as a list of edit tokens so delete removes whole units
 * ("sin(" goes in one press).
 */
class CalculatorEngine(history: List<HistoryEntry> = emptyList()) {

    private val tokens = ArrayList<String>()
    private var mode = DisplayMode.EDIT
    private var resultText = ""
    private var errorMessage: String? = null

    var degrees: Boolean = true

    private val _history = ArrayList(history)
    val history: List<HistoryEntry> get() = _history

    val formula: String get() = tokens.joinToString("")

    fun state(): DisplayState = when (mode) {
        DisplayMode.RESULT -> DisplayState(formula = resultText, mode = DisplayMode.RESULT)
        DisplayMode.ERROR -> DisplayState(formula = formula, mode = DisplayMode.ERROR, errorMessage = errorMessage)
        DisplayMode.EDIT -> DisplayState(formula = formula, preview = preview())
    }

    fun clearHistory() = _history.clear()

    /** Put a history formula back on the display for editing. */
    fun restore(text: String) {
        tokens.clear()
        tokens += editTokens(text)
        mode = DisplayMode.EDIT
    }

    fun press(key: CalcKey) {
        when (mode) {
            DisplayMode.RESULT -> {
                val continues = key is CalcKey.Op || key == CalcKey.Percent ||
                    key == CalcKey.Factorial || key == CalcKey.Square
                tokens.clear()
                if (continues) tokens += editTokens(resultText)
                mode = DisplayMode.EDIT
                if (key == CalcKey.Delete) return
            }
            DisplayMode.ERROR -> mode = DisplayMode.EDIT
            DisplayMode.EDIT -> Unit
        }
        if (tokens.size >= MAX_TOKENS && key != CalcKey.Delete && key != CalcKey.Clear) return
        when (key) {
            is CalcKey.Digit -> digit(key.d)
            CalcKey.Point -> point()
            is CalcKey.Op -> operator(key.c)
            CalcKey.Percent, CalcKey.Factorial -> postfix(if (key == CalcKey.Percent) "%" else "!")
            CalcKey.Square -> if (endsWithOperand()) { tokens += "^"; tokens += "2" }
            CalcKey.Parens -> parens()
            is CalcKey.Func -> operand("${key.name}(")
            CalcKey.Sqrt -> operand("√")
            CalcKey.ExpE -> { operand("e"); tokens += "^" }
            CalcKey.Exp10 -> {
                if (endsWithOperand()) tokens += "×"
                tokens += "1"; tokens += "0"; tokens += "^"
            }
            CalcKey.Pi -> operand("π")
            CalcKey.Euler -> operand("e")
            CalcKey.Clear -> tokens.clear()
            CalcKey.Delete -> if (tokens.isNotEmpty()) tokens.removeAt(tokens.size - 1)
        }
    }

    /**
     * Equals. Returns the formula as it stood before evaluation so the caller can
     * test it as a vault PIN. A plain number or incomplete formula is a no-op,
     * as on the stock app.
     */
    fun equals(): String {
        if (mode != DisplayMode.EDIT) return ""
        val before = formula
        if (tokens.isEmpty() || isPlainNumber(before)) return before
        when (val r = Evaluator(degrees).evaluate(before)) {
            is EvalResult.Value -> {
                resultText = NumberFormatter.format(r.value)
                _history += HistoryEntry(before, resultText)
                if (_history.size > MAX_HISTORY) _history.removeAt(0)
                mode = DisplayMode.RESULT
            }
            is EvalResult.Error -> {
                errorMessage = r.kind.message
                mode = DisplayMode.ERROR
            }
            EvalResult.Incomplete -> Unit
        }
        return before
    }

    // ---- editing rules ----

    private fun last(): String? = tokens.lastOrNull()

    private fun endsWithOperand(): Boolean {
        val l = last() ?: return false
        val c = l.last()
        return c.isDigit() || c == '.' || c == ')' || c == 'π' || c == 'e' || c == '!' || c == '%'
    }

    /** Operand that is not a bare number: needs an explicit × after ), π, e, !, %. */
    private fun endsWithClosedOperand(): Boolean {
        val c = last()?.last() ?: return false
        return c == ')' || c == 'π' || c == 'e' || c == '!' || c == '%'
    }

    private fun isBinaryOp(t: String?) = t != null && t.length == 1 && t[0] in BINARY_OPS

    /** Digits (and point) of the number currently being typed. */
    private fun currentNumber(): String {
        val sb = StringBuilder()
        for (k in tokens.indices.reversed()) {
            val t = tokens[k]
            if (t.length == 1 && (t[0].isDigit() || t[0] == '.')) sb.insert(0, t) else break
        }
        return sb.toString()
    }

    private fun digit(d: Int) {
        if (endsWithClosedOperand()) tokens += "×"
        if (currentNumber() == "0") tokens.removeAt(tokens.size - 1)
        tokens += d.toString()
    }

    private fun point() {
        val num = currentNumber()
        if ('.' in num) return
        if (num.isEmpty()) {
            if (endsWithClosedOperand()) tokens += "×"
            tokens += "0"
        }
        tokens += "."
    }

    private fun operator(c: Char) {
        val l = last()
        if (l == null || l.endsWith("(") || l == "√") {
            if (c == '−') tokens += "−"
            return
        }
        if (isBinaryOp(l)) {
            if (c == '−' && l[0] in "×÷^") { tokens += "−"; return }
            while (isBinaryOp(last())) tokens.removeAt(tokens.size - 1)
            val now = last()
            if (now == null || now.endsWith("(") || now == "√") {
                if (c == '−') tokens += "−"
                return
            }
        }
        tokens += c.toString()
    }

    private fun postfix(t: String) {
        if (endsWithOperand()) tokens += t
    }

    private fun parens() {
        val text = formula
        val open = text.count { it == '(' }
        val close = text.count { it == ')' }
        tokens += if (open > close && endsWithOperand()) ")" else "("
    }

    private fun operand(t: String) {
        if (endsWithClosedOperand()) tokens += "×"
        tokens += t
    }

    private fun preview(): String {
        val text = formula
        if (text.isEmpty() || isPlainNumber(text)) return ""
        return when (val r = Evaluator(degrees).evaluate(text)) {
            is EvalResult.Value -> NumberFormatter.format(r.value)
            else -> ""
        }
    }

    companion object {
        const val MAX_TOKENS = 200
        const val MAX_HISTORY = 100
        private const val BINARY_OPS = "+−×÷^"
        private val PLAIN_NUMBER = Regex("""[−-]?\d*\.?\d*(E[−-]?\d+)?""")

        fun isPlainNumber(text: String) = PLAIN_NUMBER.matches(text)

        /** Split display text back into edit tokens (function names stay whole). */
        fun editTokens(text: String): List<String> {
            val out = ArrayList<String>()
            var i = 0
            while (i < text.length) {
                val name = Evaluator.FUNCTIONS.firstOrNull { text.startsWith("$it(", i) }
                if (name != null) {
                    out += "$name("
                    i += name.length + 1
                } else {
                    out += text[i].toString()
                    i++
                }
            }
            return out
        }
    }
}
