package ai.kbird.calculatorvault.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorEngineTest {

    private fun CalculatorEngine.type(keys: String) {
        for (c in keys) press(
            when (c) {
                in '0'..'9' -> CalcKey.Digit(c - '0')
                '.' -> CalcKey.Point
                '+', '−', '×', '÷', '^' -> CalcKey.Op(c)
                '%' -> CalcKey.Percent
                '!' -> CalcKey.Factorial
                '(' , ')' -> CalcKey.Parens
                '<' -> CalcKey.Delete
                else -> error("no key for $c")
            }
        )
    }

    @Test fun emptyDisplayIsBlank() {
        val s = CalculatorEngine().state()
        assertEquals("", s.formula)
        assertEquals("", s.preview)
    }

    @Test fun livePreviewOnlyWithAnOperation() {
        val e = CalculatorEngine()
        e.type("1234567")
        assertEquals("", e.state().preview)
        e.type("+89")
        assertEquals("1234567+89", e.state().formula)
        assertEquals("1234656", e.state().preview)
    }

    @Test fun equalsMovesEntryToHistory() {
        val e = CalculatorEngine()
        e.type("1234567+89")
        e.equals()
        val s = e.state()
        assertEquals(DisplayMode.RESULT, s.mode)
        assertEquals("1234656", s.formula)
        assertEquals(listOf(HistoryEntry("1234567+89", "1234656")), e.history)
    }

    @Test fun divideByZeroKeepsFormulaAndShowsError() {
        val e = CalculatorEngine()
        e.type("1÷0")
        assertEquals("", e.state().preview)
        e.equals()
        val s = e.state()
        assertEquals(DisplayMode.ERROR, s.mode)
        assertEquals("1÷0", s.formula)
        assertEquals("Can't divide by 0", s.errorMessage)
        assertTrue(e.history.isEmpty())
        // Editing resumes from the red formula.
        e.type("<2")
        assertEquals("1÷2", e.state().formula)
        assertEquals(DisplayMode.EDIT, e.state().mode)
    }

    @Test fun plainNumberEqualsIsNoOpAndReturnsFormula() {
        val e = CalculatorEngine()
        e.type("2580")
        assertEquals("2580", e.equals())
        assertEquals(DisplayMode.EDIT, e.state().mode)
        assertEquals("2580", e.state().formula)
        assertTrue(e.history.isEmpty())
    }

    @Test fun afterResultDigitStartsFreshOperatorContinues() {
        val e = CalculatorEngine()
        e.type("2+3")
        e.equals()
        e.type("×2")
        assertEquals("5×2", e.state().formula)
        e.equals()
        e.type("7")
        assertEquals("7", e.state().formula)
    }

    @Test fun operatorReplacementAndUnaryMinus() {
        val e = CalculatorEngine()
        e.type("+")
        assertEquals("", e.formula)
        e.type("−5+×")
        assertEquals("−5×", e.formula)
        e.type("−")
        assertEquals("−5×−", e.formula)
        e.type("+")
        assertEquals("−5+", e.formula)
    }

    @Test fun decimalPointRules() {
        val e = CalculatorEngine()
        e.type(".")
        assertEquals("0.", e.formula)
        e.type("5.5")
        assertEquals("0.55", e.formula)
    }

    @Test fun leadingZeroIsReplaced() {
        val e = CalculatorEngine()
        e.type("05")
        assertEquals("5", e.formula)
    }

    @Test fun smartParens() {
        val e = CalculatorEngine()
        e.type("(2+3)")
        assertEquals("(2+3)", e.formula)
        e.type("4")
        assertEquals("(2+3)×4", e.formula)
    }

    @Test fun deleteRemovesWholeFunctionToken() {
        val e = CalculatorEngine()
        e.press(CalcKey.Func("sin"))
        e.type("30")
        assertEquals("sin(30", e.formula)
        assertEquals("0.5", e.state().preview)
        e.type("<<<")
        assertEquals("", e.formula)
    }

    @Test fun restoreFromHistory() {
        val e = CalculatorEngine()
        e.restore("cos⁻¹(1)+2")
        assertEquals("2", e.state().preview)
        e.type("<")
        assertEquals("cos⁻¹(1)+", e.formula)
    }

    @Test fun capturedSixteenDigitResult() {
        val e = CalculatorEngine()
        e.type("99999999×99999999")
        e.equals()
        assertEquals("9999999800000001", e.state().formula)
    }
}
