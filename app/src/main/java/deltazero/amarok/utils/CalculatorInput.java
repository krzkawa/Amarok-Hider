package deltazero.amarok.utils;

/**
 * The expression being typed on the calculator disguise keypad.
 * <p>
 * Keeps the expression in the form the display shows and rejects the keystrokes a calculator
 * would not accept, so what reaches {@link CalculatorEngine} is always something it can parse.
 * Contains no Android dependency, so it is covered by plain JVM unit tests.
 */
public class CalculatorInput {

    /**
     * Longest expression the display accepts. Real calculators stop somewhere too, and it keeps
     * the parser's recursion shallow however hard the keypad is hammered.
     */
    public static final int MAX_LENGTH = 128;

    private final StringBuilder expression = new StringBuilder();

    public String getExpression() {
        return expression.toString();
    }

    public boolean isEmpty() {
        return expression.length() == 0;
    }

    public void clear() {
        expression.setLength(0);
    }

    /** Replace everything on the display, e.g. with the result of the previous calculation. */
    public void set(String value) {
        expression.setLength(0);
        expression.append(value);
    }

    public void appendDigit(char digit) {
        if (hasRoomFor(1))
            expression.append(digit);
    }

    /** Whether the display can take another {@code count} characters. */
    private boolean hasRoomFor(int count) {
        return expression.length() + count <= MAX_LENGTH;
    }

    /**
     * Add a decimal point, opening a new number when the expression does not end in one.
     * A number that already carries a point is left alone.
     */
    public void appendDecimalPoint() {
        if (currentNumberHasDecimalPoint() || !hasRoomFor(2))
            return;

        if (isEmpty() || !isValueEnd(last()))
            expression.append('0');

        expression.append('.');
    }

    /**
     * Add an operator. A trailing operator is replaced rather than stacked, and an expression
     * that cannot start with one only accepts a leading minus.
     *
     * @param operator One of the keypad's operator glyphs.
     */
    public void appendOperator(char operator) {
        // A trailing operator is replaced, so only a fresh one needs room.
        if (!isEmpty() && !isOperator(last()) && !hasRoomFor(1))
            return;

        if (isEmpty() || last() == '(') {
            // Only a sign makes sense here.
            if (operator == CalculatorEngine.DISPLAY_MINUS)
                expression.append(operator);
            return;
        }

        if (isOperator(last()))
            expression.setLength(expression.length() - 1);

        if (isEmpty() && operator != CalculatorEngine.DISPLAY_MINUS)
            return;

        expression.append(operator);
    }

    /**
     * Add whichever bracket fits: an opening one where a value may start, a closing one where
     * there is an unclosed bracket to close. An opening bracket straight after a value is
     * multiplied by it, the way it reads on paper.
     */
    public void appendParenthesis() {
        if (!hasRoomFor(2))
            return;

        if (isEmpty() || !isValueEnd(last())) {
            expression.append('(');
            return;
        }

        if (countUnclosed() > 0)
            expression.append(')');
        else
            expression.append(CalculatorEngine.DISPLAY_MULTIPLY).append('(');
    }

    public void backspace() {
        if (!isEmpty())
            expression.setLength(expression.length() - 1);
    }

    /** Flip the sign of the number being typed. */
    public void toggleSign() {
        int start = currentNumberStart();

        if (!hasRoomFor(1) && !(start > 0 && expression.charAt(start - 1) == CalculatorEngine.DISPLAY_MINUS))
            return;

        if (start > 0 && expression.charAt(start - 1) == CalculatorEngine.DISPLAY_MINUS
                && isSignPosition(start - 1)) {
            expression.deleteCharAt(start - 1);
        } else {
            expression.insert(start, CalculatorEngine.DISPLAY_MINUS);
        }
    }

    /**
     * Check whether an equation can actually be entered on the keypad, so the user cannot set an
     * unlock equation they would never be able to type.
     *
     * @param equation The equation, in either keypad or ASCII form.
     * @return Whether replaying it keystroke by keystroke reproduces it exactly.
     */
    public static boolean isTypeable(String equation) {

        String target = CalculatorEngine.normalize(equation);
        if (target.isEmpty())
            return false;

        var replay = new CalculatorInput();

        for (char c : target.toCharArray()) {
            switch (c) {
                case '+' -> replay.appendOperator('+');
                case '-' -> replay.appendOperator(CalculatorEngine.DISPLAY_MINUS);
                case '*' -> replay.appendOperator(CalculatorEngine.DISPLAY_MULTIPLY);
                case '/' -> replay.appendOperator(CalculatorEngine.DISPLAY_DIVIDE);
                case '(', ')' -> replay.appendParenthesis();
                case '.' -> replay.appendDecimalPoint();
                default -> {
                    if (!isNumberChar(c))
                        return false;
                    replay.appendDigit(c);
                }
            }
        }

        return CalculatorEngine.normalize(replay.getExpression()).equals(target);
    }

    /** Where the number currently being typed starts, or the end of the expression if none is. */
    private int currentNumberStart() {
        int i = expression.length();
        while (i > 0 && isNumberChar(expression.charAt(i - 1)))
            i--;
        return i;
    }

    private boolean currentNumberHasDecimalPoint() {
        for (int i = currentNumberStart(); i < expression.length(); i++) {
            if (expression.charAt(i) == '.')
                return true;
        }
        return false;
    }

    /** Whether a minus at this index is a sign rather than a subtraction. */
    private boolean isSignPosition(int index) {
        return index == 0 || expression.charAt(index - 1) == '(';
    }

    private int countUnclosed() {
        int open = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(') open++;
            else if (c == ')') open--;
        }
        return open;
    }

    private char last() {
        return expression.charAt(expression.length() - 1);
    }

    /** Whether a character ends a value, and so cannot be followed by another value. */
    private static boolean isValueEnd(char c) {
        return isNumberChar(c) || c == ')';
    }

    private static boolean isNumberChar(char c) {
        return (c >= '0' && c <= '9') || c == '.';
    }

    private static boolean isOperator(char c) {
        return c == '+'
                || c == CalculatorEngine.DISPLAY_MINUS
                || c == CalculatorEngine.DISPLAY_MULTIPLY
                || c == CalculatorEngine.DISPLAY_DIVIDE;
    }
}
