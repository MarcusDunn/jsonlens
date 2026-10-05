package ca.marcusdunn.jsonlens.path.evaluator.iregexp;

import ca.marcusdunn.jsonlens.model.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import org.jspecify.annotations.Nullable;

/**
 * A recursive-descent parser for the I-Regexp grammar (RFC 9485, Figure 1).
 *
 * <p>A method that fails returns {@code null} after it records the problem. The parser does not
 * use exceptions.
 */
final class RegexParser {

    /** The maximum nesting of groups. A deeper regexp is too complex. */
    static final int MAX_GROUP_DEPTH = 256;

    /** The result of a method that returns a character, when there is no character. */
    private static final int NONE = -1;

    private final String text;
    private int pos;
    private int depth;
    private IRegexp.@Nullable Problem problem;

    private RegexParser(String text) {
        this.text = text;
    }

    static Result<RegexNode, IRegexp.Problem> parse(String pattern) {
        RegexParser parser = new RegexParser(pattern);
        RegexNode tree = parser.regexp();
        if (tree != null && parser.pos == pattern.length()) {
            return Result.ok(tree);
        }
        // A method that returns null records a problem. A complete tree with more text after it, such
        // as "a)", has no problem yet.
        IRegexp.Problem problem = parser.problem;
        return Result.err(problem != null ? problem : new IRegexp.Problem.Invalid(parser.pos));
    }

    /** i-regexp = branch *( "|" branch ) */
    private @Nullable RegexNode regexp() {
        List<RegexNode> branches = new ArrayList<>();
        while (true) {
            RegexNode branch = branch();
            if (branch == null) {
                return null;
            }
            branches.add(branch);
            if (peek() != '|') {
                break;
            }
            pos++;
        }
        return branches.size() == 1 ? branches.getFirst() : new RegexNode.Choice(branches);
    }

    /** branch = *piece */
    private @Nullable RegexNode branch() {
        List<RegexNode> pieces = new ArrayList<>();
        while (pos < text.length() && peek() != '|' && peek() != ')') {
            RegexNode piece = piece();
            if (piece == null) {
                return null;
            }
            pieces.add(piece);
        }
        return switch (pieces.size()) {
            case 0 -> new RegexNode.Empty();
            case 1 -> pieces.getFirst();
            default -> new RegexNode.Sequence(pieces);
        };
    }

    /** piece = atom [ quantifier ] */
    private @Nullable RegexNode piece() {
        RegexNode atom = atom();
        if (atom == null) {
            return null;
        }
        return switch (peek()) {
            case '*' -> {
                pos++;
                yield new RegexNode.Repeat(atom, 0, -1);
            }
            case '+' -> {
                pos++;
                yield new RegexNode.Repeat(atom, 1, -1);
            }
            case '?' -> {
                pos++;
                yield new RegexNode.Repeat(atom, 0, 1);
            }
            case '{' -> rangeQuantifier(atom);
            default -> atom;
        };
    }

    /** range-quantifier = "{" QuantExact [ "," [ QuantExact ] ] "}" */
    private @Nullable RegexNode rangeQuantifier(RegexNode atom) {
        pos++; // '{'
        int min = quantExact();
        if (min < 0) {
            invalid();
            return null;
        }
        int max = min;
        if (peek() == ',') {
            pos++;
            if (peek() == '}') {
                max = -1;
            } else {
                max = quantExact();
                if (max < 0) {
                    invalid();
                    return null;
                }
            }
        }
        if (peek() != '}' || (max >= 0 && max < min)) {
            invalid();
            return null;
        }
        pos++;
        return new RegexNode.Repeat(atom, min, max);
    }

    /** QuantExact = 1*%x30-39. Returns -1 if there is no digit. A large value is capped. */
    private int quantExact() {
        int start = pos;
        long value = 0;
        while (isDigit(peek())) {
            value = Math.min(Integer.MAX_VALUE, value * 10 + (peek() - '0'));
            pos++;
        }
        return pos == start ? -1 : (int) value;
    }

    /** atom = NormalChar / charClass / ( "(" i-regexp ")" ) */
    private @Nullable RegexNode atom() {
        int c = peek();
        return switch (c) {
            case '(' -> group();
            case '.' -> {
                pos++;
                yield new RegexNode.CharacterSet(cp -> cp != '\n' && cp != '\r');
            }
            // Anchors, as the JSONPath Compliance Test Suite and the mappings of RFC 9485, Section 5
            // require. XSD makes "^" and "$" ordinary characters (see spec/requirements.txt).
            case '^' -> {
                pos++;
                yield new RegexNode.Anchor(true);
            }
            case '$' -> {
                pos++;
                yield new RegexNode.Anchor(false);
            }
            case '[' -> characterSet(charClassExpr());
            case '\\' -> characterSet(escape());
            default -> {
                if (!isNormalChar(c)) {
                    yield invalid();
                }
                pos += Character.charCount(c);
                yield new RegexNode.CharacterSet(cp -> cp == c);
            }
        };
    }

    private static @Nullable RegexNode characterSet(@Nullable IntPredicate set) {
        return set == null ? null : new RegexNode.CharacterSet(set);
    }

    /** "(" i-regexp ")" */
    private @Nullable RegexNode group() {
        if (depth >= MAX_GROUP_DEPTH) {
            problem = new IRegexp.Problem.TooComplex(MAX_GROUP_DEPTH);
            return null;
        }
        depth++;
        pos++;
        RegexNode inner = regexp();
        if (inner == null) {
            return null;
        }
        if (peek() != ')') {
            invalid();
            return null;
        }
        pos++;
        depth--;
        return inner;
    }

    /** SingleCharEsc / charClassEsc, at a backslash. */
    private @Nullable IntPredicate escape() {
        if (text.startsWith("\\p{", pos) || text.startsWith("\\P{", pos)) {
            return categoryEscape();
        }
        int c = singleCharEscape();
        if (c == NONE) {
            return null;
        }
        return cp -> cp == c;
    }

    /** SingleCharEsc. Returns the character, or NONE after it records the problem. */
    private int singleCharEscape() {
        pos++; // '\'
        int c = peek();
        int value = switch (c) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '(', ')', '*', '+', '-', '.', '?', '[', '\\', ']', '^', '{', '|', '}' -> c;
            default -> NONE;
        };
        if (value == NONE) {
            invalid();
            return NONE;
        }
        pos++;
        return value;
    }

    /** catEsc = "\p{" charProp "}" / complEsc = "\P{" charProp "}" */
    private @Nullable IntPredicate categoryEscape() {
        boolean complement = text.charAt(pos + 1) == 'P';
        pos += 3;
        int end = text.indexOf('}', pos);
        if (end == -1) {
            invalid();
            return null;
        }
        int mask = categoryMask(text.substring(pos, end));
        if (mask == 0) {
            invalid();
            return null;
        }
        pos = end + 1;
        return complement
                ? cp -> (mask & (1 << Character.getType(cp))) == 0
                : cp -> (mask & (1 << Character.getType(cp))) != 0;
    }

    /** charClassExpr = "[" [ "^" ] ( "-" / CCE1 ) *CCE1 [ "-" ] "]" */
    private @Nullable IntPredicate charClassExpr() {
        pos++; // '['
        boolean negated = peek() == '^';
        if (negated) {
            pos++;
        }
        List<IntPredicate> items = new ArrayList<>();
        if (peek() == '-') {
            pos++;
            items.add(cp -> cp == '-');
        } else {
            IntPredicate item = cce1();
            if (item == null) {
                return null;
            }
            items.add(item);
        }
        while (peek() != ']' && !text.startsWith("-]", pos)) {
            IntPredicate item = cce1();
            if (item == null) {
                return null;
            }
            items.add(item);
        }
        if (peek() == '-') {
            pos++;
            items.add(cp -> cp == '-');
        }
        pos++; // ']'
        IntPredicate[] all = items.toArray(IntPredicate[]::new);
        IntPredicate union = cp -> {
            for (IntPredicate item : all) {
                if (item.test(cp)) {
                    return true;
                }
            }
            return false;
        };
        return negated ? union.negate() : union;
    }

    /** CCE1 = ( CCchar [ "-" CCchar ] ) / charClassEsc */
    private @Nullable IntPredicate cce1() {
        if (text.startsWith("\\p{", pos) || text.startsWith("\\P{", pos)) {
            return categoryEscape();
        }
        int low = ccChar();
        if (low == NONE) {
            return null;
        }
        if (peek() == '-' && !text.startsWith("-]", pos)) {
            pos++;
            int high = ccChar();
            if (high == NONE) {
                return null;
            }
            if (high < low) {
                invalid();
                return null;
            }
            return cp -> cp >= low && cp <= high;
        }
        return cp -> cp == low;
    }

    /** CCchar. Returns the character, or NONE after it records the problem. */
    private int ccChar() {
        int c = peek();
        if (c == '\\') {
            return singleCharEscape();
        }
        if (c == NONE || c == '-' || c == '[' || c == ']' || (c >= 0xD800 && c <= 0xDFFF)) {
            invalid();
            return NONE;
        }
        pos += Character.charCount(c);
        return c;
    }

    /** NormalChar: all scalar values except . ( ) * + ? [ \ ] { | } */
    private static boolean isNormalChar(int c) {
        return switch (c) {
            case '.', '(', ')', '*', '+', '?', '[', '\\', ']', '{', '|', '}' -> false;
            default -> !(c >= 0xD800 && c <= 0xDFFF);
        };
    }

    private int peek() {
        return pos < text.length() ? text.codePointAt(pos) : -1;
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    /** Records an Invalid problem at the position. Always returns null. Each caller returns at once. */
    // The method always returns null, so the type parameter hides no unchecked cast.
    @SuppressWarnings("TypeParameterUnusedInFormals")
    private <T> @Nullable T invalid() {
        problem = new IRegexp.Problem.Invalid(pos);
        return null;
    }

    // The Unicode general categories of RFC 9485 (IsCategory), as masks of Character.getType() values.

    /**
     * Returns the mask for a category name.
     *
     * @param name a name such as "L" or "Nd"
     * @return the mask, or 0 if the name is not an IsCategory of RFC 9485
     */
    static int categoryMask(String name) {
        return switch (name) {
            case "L" -> categoryMask(Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
                    Character.MODIFIER_LETTER, Character.OTHER_LETTER);
            case "Lu" -> categoryMask(Character.UPPERCASE_LETTER);
            case "Ll" -> categoryMask(Character.LOWERCASE_LETTER);
            case "Lt" -> categoryMask(Character.TITLECASE_LETTER);
            case "Lm" -> categoryMask(Character.MODIFIER_LETTER);
            case "Lo" -> categoryMask(Character.OTHER_LETTER);
            case "M" -> categoryMask(Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK);
            case "Mn" -> categoryMask(Character.NON_SPACING_MARK);
            case "Mc" -> categoryMask(Character.COMBINING_SPACING_MARK);
            case "Me" -> categoryMask(Character.ENCLOSING_MARK);
            case "N" -> categoryMask(Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER);
            case "Nd" -> categoryMask(Character.DECIMAL_DIGIT_NUMBER);
            case "Nl" -> categoryMask(Character.LETTER_NUMBER);
            case "No" -> categoryMask(Character.OTHER_NUMBER);
            case "P" -> categoryMask(Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
                    Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION,
                    Character.FINAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION);
            case "Pc" -> categoryMask(Character.CONNECTOR_PUNCTUATION);
            case "Pd" -> categoryMask(Character.DASH_PUNCTUATION);
            case "Ps" -> categoryMask(Character.START_PUNCTUATION);
            case "Pe" -> categoryMask(Character.END_PUNCTUATION);
            case "Pi" -> categoryMask(Character.INITIAL_QUOTE_PUNCTUATION);
            case "Pf" -> categoryMask(Character.FINAL_QUOTE_PUNCTUATION);
            case "Po" -> categoryMask(Character.OTHER_PUNCTUATION);
            case "Z" -> categoryMask(Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR);
            case "Zs" -> categoryMask(Character.SPACE_SEPARATOR);
            case "Zl" -> categoryMask(Character.LINE_SEPARATOR);
            case "Zp" -> categoryMask(Character.PARAGRAPH_SEPARATOR);
            case "S" -> categoryMask(Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL, Character.MODIFIER_SYMBOL,
                    Character.OTHER_SYMBOL);
            case "Sm" -> categoryMask(Character.MATH_SYMBOL);
            case "Sc" -> categoryMask(Character.CURRENCY_SYMBOL);
            case "Sk" -> categoryMask(Character.MODIFIER_SYMBOL);
            case "So" -> categoryMask(Character.OTHER_SYMBOL);
            // "C" also covers surrogates (Cs), as in XSD. RFC 9485 has no separate name for Cs.
            case "C" -> categoryMask(Character.CONTROL, Character.FORMAT, Character.UNASSIGNED, Character.PRIVATE_USE,
                    Character.SURROGATE);
            case "Cc" -> categoryMask(Character.CONTROL);
            case "Cf" -> categoryMask(Character.FORMAT);
            case "Cn" -> categoryMask(Character.UNASSIGNED);
            case "Co" -> categoryMask(Character.PRIVATE_USE);
            default -> 0;
        };
    }

    private static int categoryMask(int... types) {
        int mask = 0;
        for (int type : types) {
            mask |= 1 << type;
        }
        return mask;
    }
}
