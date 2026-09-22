package deltazero.amarok.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;

public class CalculatorEngineTest {

    private static final double DELTA = 1e-9;

    @Test
    public void normalize_rewritesKeypadGlyphsAndDropsWhitespace() {
        assertEquals("6*7", CalculatorEngine.normalize("6×7"));
        assertEquals("6/7", CalculatorEngine.normalize("6÷7"));
        assertEquals("6-7", CalculatorEngine.normalize("6−7"));
        assertEquals("1+2", CalculatorEngine.normalize(" 1 + 2 "));
        assertEquals("", CalculatorEngine.normalize(null));
    }

    @Test
    public void evaluate_appliesOperatorPrecedence() {
        assertEquals(14, CalculatorEngine.evaluate("2+3*4"), DELTA);
        assertEquals(20, CalculatorEngine.evaluate("(2+3)*4"), DELTA);
        assertEquals(1, CalculatorEngine.evaluate("10-3*4+3"), DELTA);
        assertEquals(2.5, CalculatorEngine.evaluate("10/4"), DELTA);
    }

    @Test
    public void evaluate_handlesKeypadGlyphs() {
        assertEquals(42, CalculatorEngine.evaluate("6×7"), DELTA);
        assertEquals(-1, CalculatorEngine.evaluate("−1"), DELTA);
    }

    @Test
    public void evaluate_appliesUnarySigns() {
        assertEquals(-6, CalculatorEngine.evaluate("-2*3"), DELTA);
        assertEquals(6, CalculatorEngine.evaluate("-2*-3"), DELTA);
        assertEquals(5, CalculatorEngine.evaluate("+5"), DELTA);
    }

    @Test
    public void evaluate_readsDecimals() {
        assertEquals(0.75, CalculatorEngine.evaluate("0.25+0.5"), DELTA);
        assertEquals(1.5, CalculatorEngine.evaluate("3/2"), DELTA);
    }

    @Test
    public void evaluate_rejectsIncompleteExpressions() {
        assertThrows(CalculatorEngine.ExpressionException.class, () -> CalculatorEngine.evaluate(""));
        assertThrows(CalculatorEngine.ExpressionException.class, () -> CalculatorEngine.evaluate("1+"));
        assertThrows(CalculatorEngine.ExpressionException.class, () -> CalculatorEngine.evaluate("(1+2"));
        assertThrows(CalculatorEngine.ExpressionException.class, () -> CalculatorEngine.evaluate("1+2)"));
        assertThrows(CalculatorEngine.ExpressionException.class, () -> CalculatorEngine.evaluate("1..2"));
    }

    @Test
    public void evaluate_rejectsDivisionByZero() {
        assertThrows(CalculatorEngine.ExpressionException.class, () -> CalculatorEngine.evaluate("1/0"));
        assertThrows(CalculatorEngine.ExpressionException.class, () -> CalculatorEngine.evaluate("1/(2-2)"));
    }

    @Test
    public void format_dropsTrailingZeros() {
        assertEquals("4", CalculatorEngine.format(4.0));
        assertEquals("0", CalculatorEngine.format(-0.0));
        assertEquals("2.5", CalculatorEngine.format(2.5));
        assertEquals("-3.25", CalculatorEngine.format(-3.25));
    }

    @Test
    public void format_roundsAwayBinaryFloatingPointNoise() {
        // 0.1 + 0.2 is 0.30000000000000004 as a double.
        assertEquals("0.3", CalculatorEngine.evaluateToString("0.1+0.2"));
    }

    @Test
    public void format_fallsBackToScientificNotationAtTheExtremes() {
        assertTrue(CalculatorEngine.format(1e15).contains("e"));
        assertTrue(CalculatorEngine.format(1e-12).contains("e"));
    }

    @Test
    public void matchesUnlockEquation_ignoresHowTheOperatorsWereWritten() {
        assertTrue(CalculatorEngine.matchesUnlockEquation("12×34", "12*34"));
        assertTrue(CalculatorEngine.matchesUnlockEquation("1234+5678", "1234+5678"));
    }

    @Test
    public void matchesUnlockEquation_comparesTheExpressionRatherThanItsValue() {
        // 2*6 and 3*4 are both 12, but only the configured equation opens the app.
        assertFalse(CalculatorEngine.matchesUnlockEquation("3×4", "2*6"));
        assertFalse(CalculatorEngine.matchesUnlockEquation("12", "2*6"));
    }

    @Test
    public void matchesUnlockEquation_neverMatchesOnAnUnsetEquation() {
        assertFalse(CalculatorEngine.matchesUnlockEquation("", ""));
        assertFalse(CalculatorEngine.matchesUnlockEquation("1+1", null));
        assertFalse(CalculatorEngine.matchesUnlockEquation("", "1+1"));
    }

    @Test
    public void format_usesADotWhateverTheDeviceLocaleIs() {
        Locale original = Locale.getDefault();
        try {
            // A locale that would otherwise render 1,5 rather than 1.5.
            Locale.setDefault(Locale.GERMANY);
            assertTrue(CalculatorEngine.format(1.5).contains("."));
            assertTrue(CalculatorEngine.format(1e15).contains("."));
        } finally {
            Locale.setDefault(original);
        }
    }
}
