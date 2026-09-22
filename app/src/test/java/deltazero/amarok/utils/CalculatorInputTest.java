package deltazero.amarok.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class CalculatorInputTest {

    private static final char MINUS = CalculatorEngine.DISPLAY_MINUS;
    private static final char MULTIPLY = CalculatorEngine.DISPLAY_MULTIPLY;
    private static final char DIVIDE = CalculatorEngine.DISPLAY_DIVIDE;

    private CalculatorInput input;

    @Before
    public void setUp() {
        input = new CalculatorInput();
    }

    /** Type a run of digits, so the tests read like the keys being pressed. */
    private void type(String digits) {
        for (char c : digits.toCharArray())
            input.appendDigit(c);
    }

    @Test
    public void startsEmpty() {
        assertTrue(input.isEmpty());
        assertEquals("", input.getExpression());
    }

    @Test
    public void appendOperator_replacesATrailingOperatorInsteadOfStackingIt() {
        type("12");
        input.appendOperator('+');
        input.appendOperator(MULTIPLY);
        assertEquals("12" + MULTIPLY, input.getExpression());
    }

    @Test
    public void appendOperator_onlyAcceptsAMinusWhereAValueMustStart() {
        input.appendOperator(MULTIPLY);
        assertEquals("", input.getExpression());

        input.appendOperator(MINUS);
        assertEquals(String.valueOf(MINUS), input.getExpression());
    }

    @Test
    public void appendOperator_onlyAcceptsAMinusAfterAnOpeningBracket() {
        input.appendParenthesis();
        input.appendOperator(DIVIDE);
        assertEquals("(", input.getExpression());

        input.appendOperator(MINUS);
        assertEquals("(" + MINUS, input.getExpression());
    }

    @Test
    public void appendDecimalPoint_opensANumberWhenThereIsNoneToExtend() {
        input.appendDecimalPoint();
        assertEquals("0.", input.getExpression());

        input.appendDigit('5');
        input.appendOperator('+');
        input.appendDecimalPoint();
        assertEquals("0.5+0.", input.getExpression());
    }

    @Test
    public void appendDecimalPoint_refusesASecondPointInTheSameNumber() {
        type("12");
        input.appendDecimalPoint();
        input.appendDecimalPoint();
        type("5");
        input.appendDecimalPoint();
        assertEquals("12.5", input.getExpression());
    }

    @Test
    public void appendParenthesis_opensWhereAValueMayStart() {
        input.appendParenthesis();
        assertEquals("(", input.getExpression());

        type("2");
        input.appendOperator('+');
        input.appendParenthesis();
        assertEquals("(2+(", input.getExpression());
    }

    @Test
    public void appendParenthesis_closesAnUnclosedBracket() {
        input.appendParenthesis();
        type("2");
        input.appendParenthesis();
        assertEquals("(2)", input.getExpression());
    }

    @Test
    public void appendParenthesis_multipliesWhenABracketFollowsAValue() {
        type("3");
        input.appendParenthesis();
        assertEquals("3" + MULTIPLY + "(", input.getExpression());
    }

    @Test
    public void toggleSign_addsAndRemovesTheSignOfTheNumberBeingTyped() {
        type("42");
        input.toggleSign();
        assertEquals(MINUS + "42", input.getExpression());

        input.toggleSign();
        assertEquals("42", input.getExpression());
    }

    @Test
    public void toggleSign_leavesASubtractionAlone() {
        type("7");
        input.appendOperator(MINUS);
        type("3");
        input.toggleSign();
        assertEquals("7" + MINUS + MINUS + "3", input.getExpression());
    }

    @Test
    public void backspaceAndClear_removeWhatWasTyped() {
        type("123");
        input.backspace();
        assertEquals("12", input.getExpression());

        input.clear();
        assertTrue(input.isEmpty());

        // Backspacing an empty display is harmless.
        input.backspace();
        assertTrue(input.isEmpty());
    }

    @Test
    public void everyTypedExpressionStaysParseable() {
        // Whatever the keypad produces, the engine has to be able to read it back.
        type("12");
        input.appendOperator('+');
        input.appendOperator(MULTIPLY);
        type("3");
        assertEquals(36, CalculatorEngine.evaluate(input.getExpression()), 1e-9);
    }

    @Test
    public void isTypeable_acceptsEquationsTheKeypadCanProduce() {
        assertTrue(CalculatorInput.isTypeable("1234+5678"));
        assertTrue(CalculatorInput.isTypeable("12×34"));
        assertTrue(CalculatorInput.isTypeable("12*34"));
        assertTrue(CalculatorInput.isTypeable("(2+3)"));
        assertTrue(CalculatorInput.isTypeable("0.5+1"));
    }

    @Test
    public void isTypeable_rejectsEquationsTheKeypadCannotProduce() {
        // A leading '+' cannot be entered, and neither can a bare leading decimal point.
        assertFalse(CalculatorInput.isTypeable("+5"));
        assertFalse(CalculatorInput.isTypeable(".5"));
        assertFalse(CalculatorInput.isTypeable("1++2"));
        assertFalse(CalculatorInput.isTypeable("1.2.3"));
        assertFalse(CalculatorInput.isTypeable("2^8"));
        assertFalse(CalculatorInput.isTypeable(""));
    }

    @Test
    public void isTypeable_acceptsTheDefaultUnlockEquation() {
        assertTrue(CalculatorInput.isTypeable("1234+5678"));
    }

    @Test
    public void theDisplayStopsAtItsMaximumLength() {
        for (int i = 0; i < CalculatorInput.MAX_LENGTH * 2; i++)
            input.appendDigit('9');
        assertEquals(CalculatorInput.MAX_LENGTH, input.getExpression().length());

        // Nothing else gets in either, so the parser is never handed a runaway expression.
        input.appendOperator('+');
        input.appendParenthesis();
        input.appendDecimalPoint();
        assertEquals(CalculatorInput.MAX_LENGTH, input.getExpression().length());
    }

    @Test
    public void aFullDisplayStillAcceptsAnOperatorReplacement() {
        for (int i = 0; i < CalculatorInput.MAX_LENGTH - 1; i++)
            input.appendDigit('9');
        input.appendOperator('+');
        assertEquals(CalculatorInput.MAX_LENGTH, input.getExpression().length());

        input.appendOperator(MULTIPLY);
        assertEquals(MULTIPLY, input.getExpression().charAt(CalculatorInput.MAX_LENGTH - 1));
    }
}
