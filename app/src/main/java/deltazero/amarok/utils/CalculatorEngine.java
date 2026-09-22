package deltazero.amarok.utils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Arithmetic behind the calculator disguise.
 * <p>
 * Contains no Android dependency, so it is covered by plain JVM unit tests.
 */
public final class CalculatorEngine {

    /** Glyphs shown on the keypad, stored in their ASCII form. */
    public static final char DISPLAY_MULTIPLY = '×';   // ×
    public static final char DISPLAY_DIVIDE = '÷';     // ÷
    public static final char DISPLAY_MINUS = '−';      // −

    /** Significant digits kept in a result, matching what a pocket calculator shows. */
    private static final int PRECISION = 12;

    private CalculatorEngine() {
    }

    /** Thrown when an expression cannot be evaluated. */
    public static class ExpressionException extends RuntimeException {
        public ExpressionException(String message) {
            super(message);
        }
    }

    /**
     * Rewrite an expression into its canonical ASCII form: keypad glyphs become the operators
     * they stand for and whitespace is dropped. Both the typed expression and the stored unlock
     * equation go through this, so the two can be compared whichever way they were entered.
     *
     * @param expression Expression to rewrite. May be null.
     * @return The canonical form, never null.
     */
    public static String normalize(String expression) {
        if (expression == null)
            return "";

        var sb = new StringBuilder(expression.length());
        for (char c : expression.toCharArray()) {
            switch (c) {
                case DISPLAY_MULTIPLY -> sb.append('*');
                case DISPLAY_DIVIDE -> sb.append('/');
                case DISPLAY_MINUS -> sb.append('-');
                default -> {
                    if (!Character.isWhitespace(c))
                        sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Check whether a typed expression is the equation that unlocks Amarok.
     * <p>
     * The comparison is on the expression itself rather than on its value, so an equation that
     * happens to give the same result does not open the app.
     *
     * @param typed         Expression currently on the display.
     * @param unlockEquation The equation the user configured.
     * @return Whether the two match.
     */
    public static boolean matchesUnlockEquation(String typed, String unlockEquation) {
        String equation = normalize(unlockEquation);
        return !equation.isEmpty() && equation.equals(normalize(typed));
    }

    /**
     * Evaluate an expression.
     *
     * @param expression Expression in either keypad or ASCII form.
     * @return The value of the expression.
     * @throws ExpressionException If the expression is incomplete, malformed, divides by zero or
     *                             overflows.
     */
    public static double evaluate(String expression) {
        return new Parser(normalize(expression)).parse();
    }

    /**
     * Evaluate an expression and render it the way the display shows it.
     *
     * @param expression Expression in either keypad or ASCII form.
     * @return The formatted result.
     * @throws ExpressionException If the expression cannot be evaluated.
     */
    public static String evaluateToString(String expression) {
        return format(evaluate(expression));
    }

    /**
     * Render a result without the trailing zeros a raw double carries.
     *
     * @param value Value to render.
     * @return The value as the display shows it.
     */
    public static String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value))
            throw new ExpressionException("Result is out of range");

        // -0 reads as an error to the user; it is just zero.
        if (value == 0)
            return "0";

        double magnitude = Math.abs(value);
        if (magnitude >= 1e12 || magnitude < 1e-9)
            // Locale.ROOT keeps the separator the same as the plain form below.
            return String.format(Locale.ROOT, "%.6e", value);

        return BigDecimal.valueOf(value)
                .round(new MathContext(PRECISION, RoundingMode.HALF_UP))
                .stripTrailingZeros()
                .toPlainString();
    }

    /**
     * Recursive descent over the grammar
     * <pre>
     * expression := term (('+' | '-') term)*
     * term       := factor (('*' | '/') factor)*
     * factor     := ('+' | '-') factor | '(' expression ')' | number
     * number     := digit+ ('.' digit*)?
     * </pre>
     */
    private static final class Parser {

        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        double parse() {
            if (src.isEmpty())
                throw new ExpressionException("Empty expression");

            double value = parseExpression();
            if (pos < src.length())
                throw new ExpressionException("Unexpected '" + src.charAt(pos) + "'");

            if (Double.isNaN(value) || Double.isInfinite(value))
                throw new ExpressionException("Result is out of range");

            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                if (eat('+')) value += parseTerm();
                else if (eat('-')) value -= parseTerm();
                else return value;
            }
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                if (eat('*')) {
                    value *= parseFactor();
                } else if (eat('/')) {
                    double divisor = parseFactor();
                    if (divisor == 0)
                        throw new ExpressionException("Division by zero");
                    value /= divisor;
                } else {
                    return value;
                }
            }
        }

        private double parseFactor() {
            if (eat('+')) return parseFactor();
            if (eat('-')) return -parseFactor();

            if (eat('(')) {
                double value = parseExpression();
                if (!eat(')'))
                    throw new ExpressionException("Missing ')'");
                return value;
            }

            return parseNumber();
        }

        private double parseNumber() {
            int start = pos;
            boolean seenDot = false;

            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '.') {
                    if (seenDot)
                        throw new ExpressionException("Malformed number");
                    seenDot = true;
                } else if (!isDigit(c)) {
                    break;
                }
                pos++;
            }

            String number = src.substring(start, pos);
            if (number.isEmpty() || number.equals("."))
                throw new ExpressionException("Expected a number");

            return Double.parseDouble(number);
        }

        private boolean eat(char expected) {
            if (pos < src.length() && src.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }
}
