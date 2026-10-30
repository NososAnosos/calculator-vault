package ai.kbird.calculatorvault.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluatorTest {

    private fun eval(s: String, degrees: Boolean = true): String =
        when (val r = Evaluator(degrees).evaluate(s)) {
            is EvalResult.Value -> NumberFormatter.format(r.value)
            is EvalResult.Error -> r.kind.message
            EvalResult.Incomplete -> "<incomplete>"
        }

    // ---- captured examples (reference/capture) ----

    @Test fun capturedAddition() = assertEquals("1234656", eval("1234567+89"))

    @Test fun capturedDivideByZero() = assertEquals("Can't divide by 0", eval("1÷0"))

    @Test fun capturedSixteenDigitProduct() =
        assertEquals("9999999800000001", eval("99999999×99999999"))

    @Test fun capturedGroupingIsRenderOnly() {
        assertEquals("1,234,567+89", NumberFormatter.group("1234567+89"))
        assertEquals("9,999,999,800,000,001", NumberFormatter.group("9999999800000001"))
    }

    // ---- precedence and parentheses ----

    @Test fun precedence() {
        assertEquals("14", eval("2+3×4"))
        assertEquals("20", eval("(2+3)×4"))
        assertEquals("1", eval("10−3−6"))
        assertEquals("2", eval("16÷4÷2"))
        assertEquals("−4", eval("−2^2"))
        assertEquals("512", eval("2^3^2"))
        assertEquals("0.5", eval("2^−1"))
    }

    @Test fun implicitMultiplicationAndAutoClose() {
        assertEquals("6", eval("2(3)"))
        assertEquals("20", eval("(2+3)(4"))
        assertEquals("5", eval("(2+3"))
        assertEquals("6.283185307179586", eval("2π"))
    }

    @Test fun asciiAliases() = assertEquals("7", eval("10-6/2"))

    @Test fun incompleteExpressions() {
        assertEquals("<incomplete>", eval("5+"))
        assertEquals("<incomplete>", eval("sin("))
        assertEquals("<incomplete>", eval("()"))
        assertEquals("<incomplete>", eval(""))
    }

    // ---- percent ----

    @Test fun percent() {
        assertEquals("0.5", eval("50%"))
        assertEquals("110", eval("100+10%"))
        assertEquals("100", eval("200−50%"))
        assertEquals("10", eval("100×10%"))
        assertEquals("2.5", eval("50%×5"))
    }

    // ---- decimals and formatting ----

    @Test fun decimals() {
        assertEquals("0.3", eval("0.1+0.2"))
        assertEquals("0.333333333333333", eval("1÷3"))
        assertEquals("0.666666666666667", eval("2÷3"))
        assertEquals("3.5", eval("7÷2"))
        assertEquals("−5", eval("3−8"))
    }

    @Test fun scientificNotation() {
        assertEquals("1E20", eval("10^20"))
        assertEquals("1.23456789E17", eval("123456789012345678"))
        assertEquals("1.234567891E17", eval("123456789123456789"))
        assertEquals("1E−7", eval("1÷10000000"))
        assertEquals("0.000001", eval("1÷1000000"))
        assertEquals("2E−7", eval("2E−7"))
    }

    @Test fun groupingSkipsFractionAndExponent() {
        assertEquals("1,234.56789", NumberFormatter.group("1234.56789"))
        assertEquals("1.5E−12345", NumberFormatter.group("1.5E−12345"))
        assertEquals("−1,000×2", NumberFormatter.group("−1000×2"))
    }

    // ---- scientific functions ----

    @Test fun scientificFunctions() {
        assertEquals("120", eval("5!"))
        assertEquals("3", eval("√9"))
        assertEquals("0", eval("sin(180)"))
        assertEquals("0.5", eval("sin(30)"))
        assertEquals("1", eval("cos(0)"))
        assertEquals("0", eval("sin(π)", degrees = false))
        assertEquals("90", eval("sin⁻¹(1)"))
        assertEquals("2", eval("log(100)"))
        assertEquals("1", eval("ln(e)"))
    }

    @Test fun domainErrors() {
        assertEquals(CalcError.DOMAIN.message, eval("√(−1)"))
        assertEquals(CalcError.DOMAIN.message, eval("ln(0)"))
        assertEquals(CalcError.DOMAIN.message, eval("tan(90)"))
        assertEquals(CalcError.DOMAIN.message, eval("2.5!"))
        assertEquals(CalcError.DIVIDE_BY_ZERO.message, eval("0^−1"))
        assertEquals(CalcError.DIVIDE_BY_ZERO.message, eval("5÷(3−3)"))
    }

    @Test fun hugeValuesDoNotHang() {
        assertTrue(eval("1000!").contains("E"))
        assertEquals(CalcError.TOO_LARGE.message, eval("1001!"))
        assertEquals(CalcError.TOO_LARGE.message, eval("10^9999×10^9999"))
    }
}
