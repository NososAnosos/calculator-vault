package ai.kbird.calculatorvault.calc

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/** Outcome of evaluating a formula as typed on the pad. */
sealed interface EvalResult {
    data class Value(val value: BigDecimal) : EvalResult
    data class Error(val kind: CalcError) : EvalResult
    /** Formula is not (yet) a complete expression, e.g. "5+" or "sin(". */
    data object Incomplete : EvalResult
}

/**
 * Error kinds and their display strings. Only DIVIDE_BY_ZERO is verified against
 * the stock app capture (state 04); the others are best guesses pending capture.
 */
enum class CalcError(val message: String) {
    DIVIDE_BY_ZERO("Can't divide by 0"),
    DOMAIN("Domain error"),
    TOO_LARGE("Value too large"),
}

/**
 * Recursive-descent evaluator over the pad's display glyphs:
 *   + − × ÷ ^ % ! √ π e ( )  sin cos tan sin⁻¹ cos⁻¹ tan⁻¹ ln log
 * ASCII - * / are accepted as aliases. Numbers may carry an exponent ("1.5E−7")
 * because results in scientific notation can be continued with an operator.
 *
 * Grammar (highest binding last):
 *   expr    := term (('+'|'−') term)*
 *   term    := unary (('×'|'÷') unary | <implicit ×> unary)*
 *   unary   := ('−'|'+') unary | power
 *   power   := postfix ('^' unary)?            right-associative
 *   postfix := primary ('!' | '%')*
 *   primary := number | π | e | '(' expr ')'? | func '(' expr ')'? | '√' unary
 *
 * Percent: a bare `b%` is b/100, except as the right operand of + or −, where it
 * is relative to the left operand (100+10% = 110, 200−50% = 100).
 * Unclosed parentheses are closed implicitly at the end of input.
 */
class Evaluator(private val degrees: Boolean = true) {

    fun evaluate(formula: String): EvalResult {
        val tokens = try {
            tokenize(formula)
        } catch (_: IncompleteException) {
            return EvalResult.Incomplete
        }
        if (tokens.isEmpty()) return EvalResult.Incomplete
        return try {
            val p = Parser(tokens)
            val v = p.parseExpr().value
            if (!p.atEnd()) EvalResult.Incomplete
            else {
                checkMagnitude(v)
                EvalResult.Value(v)
            }
        } catch (_: IncompleteException) {
            EvalResult.Incomplete
        } catch (e: CalcException) {
            EvalResult.Error(e.kind)
        } catch (_: ArithmeticException) {
            EvalResult.Error(CalcError.TOO_LARGE)
        }
    }

    // ---- tokens ----

    private sealed interface Tok {
        data class Num(val v: BigDecimal) : Tok
        data class Op(val c: Char) : Tok
        data class Func(val name: String) : Tok
        data object LParen : Tok
        data object RParen : Tok
    }

    private fun tokenize(s: String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() || c == ',' -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    var text = s.substring(start, i)
                    if (text.count { it == '.' } > 1) throw IncompleteException()
                    if (text == ".") throw IncompleteException()
                    if (i < s.length && s[i] == 'E') {
                        var j = i + 1
                        val sb = StringBuilder("E")
                        if (j < s.length && (s[j] == '−' || s[j] == '-' || s[j] == '+')) {
                            if (s[j] != '+') sb.append('-')
                            j++
                        }
                        val digitsStart = j
                        while (j < s.length && s[j].isDigit()) j++
                        if (j == digitsStart) throw IncompleteException()
                        sb.append(s, digitsStart, j)
                        text += sb
                        i = j
                    }
                    out += Tok.Num(BigDecimal(text))
                }
                c == '(' -> { out += Tok.LParen; i++ }
                c == ')' -> { out += Tok.RParen; i++ }
                c == '+' -> { out += Tok.Op('+'); i++ }
                c == '−' || c == '-' -> { out += Tok.Op('−'); i++ }
                c == '×' || c == '*' -> { out += Tok.Op('×'); i++ }
                c == '÷' || c == '/' -> { out += Tok.Op('÷'); i++ }
                c == '^' || c == '%' || c == '!' || c == '√' -> { out += Tok.Op(c); i++ }
                c == 'π' -> { out += Tok.Num(PI); i++ }
                else -> {
                    val name = FUNCTIONS.firstOrNull { s.startsWith(it, i) }
                    when {
                        name != null -> { out += Tok.Func(name); i += name.length }
                        c == 'e' -> { out += Tok.Num(E); i++ }
                        else -> throw IncompleteException()
                    }
                }
            }
        }
        return out
    }

    // ---- parser ----

    private class Term(val value: BigDecimal, val relativePercent: Boolean)

    private inner class Parser(private val t: List<Tok>) {
        private var i = 0

        fun atEnd() = i >= t.size
        private fun peek(): Tok? = t.getOrNull(i)
        private fun peekOp(c: Char) = (peek() as? Tok.Op)?.c == c

        fun parseExpr(): Term {
            val first = parseTerm()
            var value = first.value
            var single = true
            while (peekOp('+') || peekOp('−')) {
                val op = (peek() as Tok.Op).c
                i++
                val rhs = parseTerm()
                val r = if (rhs.relativePercent) value.multiply(rhs.value, MC) else rhs.value
                value = if (op == '+') value.add(r, MC) else value.subtract(r, MC)
                single = false
            }
            return if (single) first else Term(value, false)
        }

        private fun startsOperand(tok: Tok?) = when (tok) {
            is Tok.Num, is Tok.Func, Tok.LParen -> true
            is Tok.Op -> tok.c == '√'
            else -> false
        }

        private fun parseTerm(): Term {
            var left = parseUnary()
            while (true) {
                val tok = peek()
                left = when {
                    peekOp('×') -> { i++; Term(left.value.multiply(parseUnary().value, MC), false) }
                    peekOp('÷') -> { i++; Term(divide(left.value, parseUnary().value), false) }
                    startsOperand(tok) -> Term(left.value.multiply(parseUnary().value, MC), false)
                    else -> return left
                }
            }
        }

        private fun parseUnary(): Term {
            if (peekOp('−')) { i++; val v = parseUnary(); return Term(v.value.negate(), v.relativePercent) }
            if (peekOp('+')) { i++; return parseUnary() }
            return parsePower()
        }

        private fun parsePower(): Term {
            val base = parsePostfix()
            if (peekOp('^')) {
                i++
                val exp = parseUnary().value
                return Term(power(base.value, exp), false)
            }
            return base
        }

        private fun parsePostfix(): Term {
            var v = parsePrimary()
            var pct = false
            while (true) {
                when {
                    peekOp('!') -> { i++; v = factorial(v); pct = false }
                    peekOp('%') -> { i++; v = v.divide(HUNDRED, MC); pct = true }
                    else -> return Term(v, pct)
                }
            }
        }

        private fun parsePrimary(): BigDecimal {
            val tok = peek() ?: throw IncompleteException()
            i++
            return when (tok) {
                is Tok.Num -> tok.v
                Tok.LParen -> group()
                is Tok.Func -> {
                    if (peek() != Tok.LParen) throw IncompleteException()
                    i++
                    applyFunction(tok.name, group())
                }
                is Tok.Op -> {
                    if (tok.c != '√') throw IncompleteException()
                    sqrt(parseUnary().value)
                }
                Tok.RParen -> throw IncompleteException()
            }
        }

        /** Body of a parenthesised group; the opening paren is already consumed. */
        private fun group(): BigDecimal {
            if (peek() == Tok.RParen) throw IncompleteException() // "()"
            val v = parseExpr().value
            when {
                peek() == Tok.RParen -> i++
                atEnd() -> Unit // implicit close
                else -> throw IncompleteException()
            }
            return v
        }
    }

    // ---- arithmetic ----

    private fun divide(a: BigDecimal, b: BigDecimal): BigDecimal {
        if (b.signum() == 0) throw CalcException(CalcError.DIVIDE_BY_ZERO)
        return a.divide(b, MC)
    }

    private fun power(base: BigDecimal, exp: BigDecimal): BigDecimal {
        val intExp = exp.toIntegralOrNull()
        if (intExp != null && intExp.abs() <= BigInteger.valueOf(9999)) {
            val n = intExp.toInt()
            if (base.signum() == 0 && n < 0) throw CalcException(CalcError.DIVIDE_BY_ZERO)
            // Bound the result size before doing exact big arithmetic.
            val approxDigits = (base.precision() - base.scale()).toLong() * n
            if (approxDigits > 20000) throw CalcException(CalcError.TOO_LARGE)
            return base.pow(n, MC)
        }
        if (base.signum() < 0) throw CalcException(CalcError.DOMAIN)
        return fromDouble(Math.pow(base.toDouble(), exp.toDouble()))
    }

    private fun factorial(v: BigDecimal): BigDecimal {
        val n = v.toIntegralOrNull() ?: throw CalcException(CalcError.DOMAIN)
        if (n.signum() < 0) throw CalcException(CalcError.DOMAIN)
        if (n > BigInteger.valueOf(1000)) throw CalcException(CalcError.TOO_LARGE)
        var r = BigInteger.ONE
        for (k in 2..n.toInt()) r = r.multiply(BigInteger.valueOf(k.toLong()))
        return BigDecimal(r)
    }

    private fun sqrt(v: BigDecimal): BigDecimal {
        if (v.signum() < 0) throw CalcException(CalcError.DOMAIN)
        return fromDouble(Math.sqrt(v.toDouble()))
    }

    private fun applyFunction(name: String, x: BigDecimal): BigDecimal {
        val d = x.toDouble()
        val toRad = if (degrees) Math.PI / 180.0 else 1.0
        return when (name) {
            "sin" -> trig(Math.sin(d * toRad))
            "cos" -> trig(Math.cos(d * toRad))
            "tan" -> {
                if (degrees && x.remainder(BigDecimal(180)).abs().compareTo(BigDecimal(90)) == 0) {
                    throw CalcException(CalcError.DOMAIN)
                }
                val c = Math.cos(d * toRad)
                if (Math.abs(c) < 1e-15) throw CalcException(CalcError.DOMAIN)
                trig(Math.tan(d * toRad))
            }
            "sin⁻¹", "cos⁻¹" -> {
                if (Math.abs(d) > 1.0) throw CalcException(CalcError.DOMAIN)
                val r = if (name == "sin⁻¹") Math.asin(d) else Math.acos(d)
                fromDouble(r / toRad)
            }
            "tan⁻¹" -> fromDouble(Math.atan(d) / toRad)
            "ln" -> {
                if (x.signum() <= 0) throw CalcException(CalcError.DOMAIN)
                fromDouble(Math.log(d))
            }
            "log" -> {
                if (x.signum() <= 0) throw CalcException(CalcError.DOMAIN)
                fromDouble(Math.log10(d))
            }
            else -> throw IncompleteException()
        }
    }

    /** Snap floating-point noise like sin(180°) = 1.2e-16 to exact zero. */
    private fun trig(d: Double): BigDecimal = if (Math.abs(d) < 1e-14) BigDecimal.ZERO else fromDouble(d)

    private fun fromDouble(d: Double): BigDecimal {
        if (d.isNaN()) throw CalcException(CalcError.DOMAIN)
        if (d.isInfinite()) throw CalcException(CalcError.TOO_LARGE)
        // 15 significant digits removes binary noise (0.1+0.2 style) from double paths.
        return BigDecimal(d.toString()).round(MathContext(15, RoundingMode.HALF_EVEN))
    }

    private fun checkMagnitude(v: BigDecimal) {
        if (v.signum() != 0 && v.precision() - v.scale() - 1 > 9999) {
            throw CalcException(CalcError.TOO_LARGE)
        }
    }

    private fun BigDecimal.toIntegralOrNull(): BigInteger? =
        try { stripTrailingZeros().toBigIntegerExact() } catch (_: ArithmeticException) { null }

    private class IncompleteException : RuntimeException()
    private class CalcException(val kind: CalcError) : RuntimeException()

    companion object {
        val MC = MathContext(50, RoundingMode.HALF_EVEN)
        private val HUNDRED = BigDecimal(100)
        val PI = BigDecimal("3.14159265358979323846264338327950288419716939937511")
        val E = BigDecimal("2.71828182845904523536028747135266249775724709369996")

        /** Longest names first so "sin⁻¹" wins over "sin". */
        val FUNCTIONS = listOf("sin⁻¹", "cos⁻¹", "tan⁻¹", "sin", "cos", "tan", "ln", "log")
    }
}
