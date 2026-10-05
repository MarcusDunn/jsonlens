package ca.marcusdunn.jsonlens.fuzz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.evaluator.iregexp.IRegexp;
import com.code_intelligence.jazzer.junit.DictionaryEntries;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Compares the I-Regexp implementation with java.util.regex. The data is the regexp, a NUL byte, and
 * the input.
 *
 * <p>The comparison translates a valid I-Regexp as RFC 9485, Section 5 describes: "." becomes
 * [^\n\r], and the anchors "^" and "$" become \A and \z. In a character class, "&amp;" is escaped,
 * because java.util.regex uses "&amp;&amp;" for intersection. A java.util.regex match that takes
 * too long is not compared.
 *
 * <p>java.util.regex does not accept all iterations that match the empty string in a counted loop.
 * For example, (\A|a){2} does not match "a", but (\A|a)(\A|a) does. So the translation writes a
 * counted repetition of a group as copies of the group.
 */
class RegexFuzzTest {

    private static final int BUDGET = 200_000;
    /** The most group copies in a translation. More copies can make java.util.regex backtrack for years. */
    private static final int MAX_COPIES = 10;
    private static final Pattern COUNTED = Pattern.compile("\\{([0-9]+)(,([0-9]*))?\\}");

    @FuzzTest(maxDuration = "5m")
    @DictionaryEntries({
        "\u0000", "(", ")", "|", "*", "+", "?", "{2}", "{1,3}", "{2,}", ".", "[", "]", "[^", "-", "^", "$",
        "\\p{L}", "\\P{Lu}", "\\p{Nd}", "\\p{C}", "\\n", "\\.", "\\-", "&&", "a", "b", "\u00e9", "\ud83d\ude00"
    })
    void compare(byte[] data) {
        String[] parts = Inputs.split(data, 40, 40);
        if (parts == null) {
            return;
        }
        String pattern = parts[0];
        String input = parts[1];
        if (!(IRegexp.compile(pattern, 1_000) instanceof Result.Ok<IRegexp, IRegexp.Problem>(IRegexp regexp))) {
            return;
        }
        String translated = translate(pattern);
        if (translated == null) {
            // Still run our engine, to find exceptions and slow inputs.
            regexp.matches(input);
            regexp.find(input);
            return;
        }
        Pattern java;
        try {
            java = Pattern.compile(translated);
        } catch (PatternSyntaxException e) {
            fail("valid I-Regexp " + pattern + " gives " + translated + ": " + e.getMessage());
            return;
        }
        Boolean javaMatches = run(java, input, true);
        if (javaMatches != null) {
            assertEquals(javaMatches, regexp.matches(input), "match(" + input + ", " + pattern + ")");
        }
        Boolean javaFinds = run(java, input, false);
        if (javaFinds != null) {
            assertEquals(javaFinds, regexp.find(input), "search(" + input + ", " + pattern + ")");
        }
    }

    /** Runs java.util.regex with a budget of character reads. Returns null if the budget ends. */
    private static Boolean run(Pattern pattern, String input, boolean full) {
        BudgetedText text = new BudgetedText(input);
        try {
            return full ? pattern.matcher(text).matches() : pattern.matcher(text).find();
        } catch (BudgetedText.OverBudget e) {
            return null;
        }
    }

    /** Translates a valid I-Regexp into java.util.regex syntax, or returns null if the translation is too large. */
    static String translate(String pattern) {
        StringBuilder java = new StringBuilder();
        Deque<Integer> groups = new ArrayDeque<>();
        long copies = 0;
        boolean inClass = false;
        int i = 0;
        while (i < pattern.length()) {
            int c = pattern.codePointAt(i);
            int length = Character.charCount(c);
            if (c == '\\') {
                // An escape: \p{...}, \P{...}, or one character.
                int end = pattern.charAt(i + 1) == 'p' || pattern.charAt(i + 1) == 'P'
                        ? pattern.indexOf('}', i) + 1
                        : i + 1 + Character.charCount(pattern.codePointAt(i + 1));
                java.append(pattern, i, end);
                i = end;
                continue;
            }
            if (inClass) {
                if (c == ']') {
                    inClass = false;
                }
                java.append(c == '&' ? "\\&" : Character.toString(c));
            } else {
                switch (c) {
                    case '[' -> {
                        inClass = true;
                        java.append('[');
                    }
                    case '(' -> {
                        groups.push(java.length());
                        java.append("(?:");
                    }
                    case ')' -> {
                        int start = groups.pop();
                        java.append(')');
                        Matcher counted = COUNTED.matcher(pattern).region(i + 1, pattern.length());
                        if (counted.lookingAt()) {
                            String group = java.substring(start);
                            java.setLength(start);
                            int min = count(counted.group(1));
                            int max = counted.group(2) == null || counted.group(3).isEmpty()
                                    ? min + 1
                                    : count(counted.group(3));
                            copies += Math.max(min, max);
                            if (copies > MAX_COPIES) {
                                return null;
                            }
                            java.append(group.repeat(min));
                            if (counted.group(2) == null) {
                                // {n}
                            } else if (counted.group(3).isEmpty()) {
                                java.append(group).append('*');
                            } else {
                                java.append((group + '?').repeat(max - min));
                            }
                            i = counted.end();
                            continue;
                        }
                    }
                    case '.' -> java.append("[^\\n\\r]");
                    case '^' -> java.append("\\A");
                    case '$' -> java.append("\\z");
                    default -> java.appendCodePoint(c);
                }
            }
            i += length;
        }
        return java.toString();
    }

    /** Reads a repetition count. A count with many digits is above MAX_COPIES, so its exact value is not necessary. */
    private static int count(String digits) {
        return digits.length() > 4 ? Integer.MAX_VALUE : Integer.parseInt(digits);
    }

    /** Text that stops a match after too many character reads. */
    private static final class BudgetedText implements CharSequence {
        private final String text;
        private int reads;

        BudgetedText(String text) {
            this.text = text;
        }

        @Override
        public char charAt(int index) {
            if (++reads > BUDGET) {
                throw new OverBudget();
            }
            return text.charAt(index);
        }

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return text.subSequence(start, end);
        }

        @Override
        public String toString() {
            return text;
        }

        static final class OverBudget extends RuntimeException {
            private static final long serialVersionUID = 1L;

            OverBudget() {
                super(null, null, false, false);
            }
        }
    }
}
