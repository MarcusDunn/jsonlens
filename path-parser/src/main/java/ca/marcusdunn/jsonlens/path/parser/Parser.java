package ca.marcusdunn.jsonlens.path.parser;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.function.FunctionSignature;
import ca.marcusdunn.jsonlens.path.core.function.FunctionType;
import ca.marcusdunn.jsonlens.path.core.query.ComparableExpression;
import ca.marcusdunn.jsonlens.path.core.query.ComparisonOperator;
import ca.marcusdunn.jsonlens.path.core.query.FilterQuery;
import ca.marcusdunn.jsonlens.path.core.query.FunctionArgument;
import ca.marcusdunn.jsonlens.path.core.query.FunctionCall;
import ca.marcusdunn.jsonlens.path.core.query.Identifier;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.core.query.Literal;
import ca.marcusdunn.jsonlens.path.core.query.LogicalExpression;
import ca.marcusdunn.jsonlens.path.core.query.Segment;
import ca.marcusdunn.jsonlens.path.core.query.Selector;
import ca.marcusdunn.jsonlens.path.core.query.SingularQuery;
import ca.marcusdunn.jsonlens.path.core.query.SingularSegment;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A recursive-descent parser for one query text. It follows the ABNF of RFC 9535.
 *
 * <p>The parser does not use exceptions. A method that can fail returns {@code null} after it
 * records the error with {@link #fail(ParseError)}. Its caller then returns {@code null} too.
 * The parser keeps only the first error.
 */
final class Parser {

    private static final long MAX_INTEGER = (1L << 53) - 1;

    private final String text;
    private final Map<String, FunctionSignature> functions;
    private final int maxDepth;
    private int pos;
    private int depth;
    private @Nullable ParseError error;

    Parser(String text, Map<String, FunctionSignature> functions, int maxDepth) {
        this.text = text;
        this.functions = functions;
        this.maxDepth = maxDepth;
    }

    /** jsonpath-query = root-identifier segments */
    Result<JsonPathQuery, ParseError> parse() {
        int surrogate = firstUnpairedSurrogate();
        if (surrogate >= 0) {
            return Result.err(new ParseError.UnpairedSurrogate(surrogate));
        }
        @Nullable JsonPathQuery query = null;
        if (expect('$', "the root identifier '$'")) {
            List<Segment> segments = segments();
            if (segments != null) {
                if (pos < text.length()) {
                    fail(unexpected("a segment or the end of the query"));
                } else {
                    query = new JsonPathQuery(segments);
                }
            }
        }
        if (query == null) {
            // Each method that returns null records the error first.
            return Result.err(Objects.requireNonNull(error));
        }
        return Result.ok(query);
    }

    // ---------------------------------------------------------------------------------------
    // Segments and selectors
    // ---------------------------------------------------------------------------------------

    /** segments = *(S segment) */
    private @Nullable List<Segment> segments() {
        List<Segment> segments = new ArrayList<>();
        while (true) {
            int start = pos;
            skipBlank();
            int c = peek();
            if (c != '[' && c != '.') {
                pos = start;
                return segments;
            }
            Segment segment = segment();
            if (segment == null) {
                return null;
            }
            segments.add(segment);
        }
    }

    /** segment = child-segment / descendant-segment */
    private @Nullable Segment segment() {
        if (text.startsWith("..", pos)) {
            pos += 2;
            int c = peek();
            if (c == '[') {
                List<Selector> selectors = bracketedSelection();
                return selectors == null ? null : new Segment.Descendant(selectors);
            }
            if (c == '*') {
                pos++;
                return new Segment.Descendant(List.of(new Selector.Wildcard()));
            }
            if (isNameFirst(c)) {
                return new Segment.Descendant(List.of(new Selector.Name(memberNameShorthand())));
            }
            fail(unexpected("'[', '*', or a member name after '..'"));
            return null;
        }
        if (peek() == '.') {
            pos++;
            int c = peek();
            if (c == '*') {
                pos++;
                return new Segment.Child(List.of(new Selector.Wildcard()));
            }
            if (isNameFirst(c)) {
                return new Segment.Child(List.of(new Selector.Name(memberNameShorthand())));
            }
            fail(unexpected("'*' or a member name after '.'"));
            return null;
        }
        List<Selector> selectors = bracketedSelection();
        return selectors == null ? null : new Segment.Child(selectors);
    }

    /** bracketed-selection = "[" S selector *(S "," S selector) S "]" */
    private @Nullable List<Selector> bracketedSelection() {
        pos++; // '[': the caller has checked it
        List<Selector> selectors = new ArrayList<>();
        skipBlank();
        while (true) {
            Selector selector = selector();
            if (selector == null) {
                return null;
            }
            selectors.add(selector);
            skipBlank();
            int c = peek();
            if (c == ']') {
                pos++;
                return selectors;
            }
            if (c != ',') {
                fail(unexpected("',' or ']'"));
                return null;
            }
            pos++;
            skipBlank();
        }
    }

    /** selector = name-selector / wildcard-selector / slice-selector / index-selector / filter-selector */
    private @Nullable Selector selector() {
        int c = peek();
        if (c == '\'' || c == '"') {
            String name = stringLiteral();
            return name == null ? null : new Selector.Name(name);
        }
        if (c == '*') {
            pos++;
            return new Selector.Wildcard();
        }
        if (c == '?') {
            if (!enter()) {
                return null;
            }
            pos++;
            skipBlank();
            LogicalExpression expression = logicalExpression();
            depth--;
            return expression == null ? null : new Selector.Filter(expression);
        }
        if (c == ':' || isIntStart(c)) {
            return indexOrSlice();
        }
        fail(unexpected("a selector"));
        return null;
    }

    /**
     * index-selector = int
     * slice-selector = [start S] ":" S [end S] [":" [S step]]
     */
    private @Nullable Selector indexOrSlice() {
        Maybe<Long> start = Maybe.none();
        if (peek() != ':') {
            Long index = integer();
            if (index == null) {
                return null;
            }
            start = Maybe.some(index);
            int afterStart = pos;
            skipBlank();
            if (peek() != ':') {
                pos = afterStart;
                return new Selector.Index(index);
            }
        }
        pos++; // ':'
        skipBlank();
        Maybe<Long> end = Maybe.none();
        if (isIntStart(peek())) {
            Long value = integer();
            if (value == null) {
                return null;
            }
            end = Maybe.some(value);
        }
        int afterEnd = pos;
        skipBlank();
        Maybe<Long> step = Maybe.none();
        if (peek() == ':') {
            pos++;
            int afterColon = pos;
            skipBlank();
            if (isIntStart(peek())) {
                Long value = integer();
                if (value == null) {
                    return null;
                }
                step = Maybe.some(value);
            } else {
                pos = afterColon;
            }
        } else {
            pos = afterEnd;
        }
        return new Selector.Slice(start, end, step);
    }

    /** int = "0" / (["-"] DIGIT1 *DIGIT), in the I-JSON range. */
    private @Nullable Long integer() {
        int start = pos;
        boolean negative = peek() == '-';
        if (negative) {
            pos++;
        }
        if (!isDigit(peek())) {
            fail(unexpected("a digit"));
            return null;
        }
        int digitsStart = pos;
        while (isDigit(peek())) {
            pos++;
        }
        String digits = text.substring(digitsStart, pos);
        if (digits.charAt(0) == '0' && (digits.length() > 1 || negative)) {
            fail(new ParseError.InvalidInteger(start, text.substring(start, pos)));
            return null;
        }
        if (digits.length() > 16) {
            fail(new ParseError.IntegerOutOfRange(start, text.substring(start, pos)));
            return null;
        }
        long value = Long.parseLong(digits);
        if (value > MAX_INTEGER) {
            fail(new ParseError.IntegerOutOfRange(start, text.substring(start, pos)));
            return null;
        }
        return negative ? -value : value;
    }

    /** member-name-shorthand = name-first *name-char. The caller checks name-first. */
    private String memberNameShorthand() {
        int start = pos;
        while (isNameFirst(peek()) || isDigit(peek())) {
            pos += Character.charCount(peek());
        }
        return text.substring(start, pos);
    }

    // ---------------------------------------------------------------------------------------
    // String literals
    // ---------------------------------------------------------------------------------------

    /** string-literal = %x22 *double-quoted %x22 / %x27 *single-quoted %x27 */
    private @Nullable String stringLiteral() {
        int quote = peek();
        pos++;
        StringBuilder value = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                fail(new ParseError.UnexpectedEnd(pos, "the closing quote " + (char) quote));
                return null;
            }
            int c = text.codePointAt(pos);
            if (c == quote) {
                pos++;
                return value.toString();
            }
            if (c == '\\') {
                if (!escape(quote, value)) {
                    return null;
                }
            } else if (c < 0x20) {
                fail(new ParseError.ControlCharacter(pos, c));
                return null;
            } else {
                value.appendCodePoint(c);
                pos += Character.charCount(c);
            }
        }
    }

    /** ESC escapable, and ESC with the own quote character. */
    private boolean escape(int quote, StringBuilder value) {
        int start = pos;
        pos++; // '\'
        if (pos >= text.length()) {
            fail(new ParseError.UnexpectedEnd(pos, "an escape character"));
            return false;
        }
        char c = text.charAt(pos);
        pos++;
        switch (c) {
            case 'b' -> value.append('\b');
            case 'f' -> value.append('\f');
            case 'n' -> value.append('\n');
            case 'r' -> value.append('\r');
            case 't' -> value.append('\t');
            case '/' -> value.append('/');
            case '\\' -> value.append('\\');
            case 'u' -> {
                return unicodeEscape(start, value);
            }
            default -> {
                if (c != quote) {
                    fail(new ParseError.InvalidEscape(start, text.substring(start, pos)));
                    return false;
                }
                value.append(c);
            }
        }
        return true;
    }

    /** hexchar = non-surrogate / (high-surrogate "\" %x75 low-surrogate). The text is after "\\u". */
    private boolean unicodeEscape(int start, StringBuilder value) {
        int unit = hex4();
        if (unit < 0) {
            fail(new ParseError.InvalidEscape(start, text.substring(start, Math.min(text.length(), start + 6))));
            return false;
        }
        if (Character.isLowSurrogate((char) unit)) {
            fail(new ParseError.UnpairedSurrogate(start));
            return false;
        }
        if (Character.isHighSurrogate((char) unit)) {
            if (!text.startsWith("\\u", pos)) {
                fail(new ParseError.UnpairedSurrogate(start));
                return false;
            }
            int lowStart = pos;
            pos += 2;
            int low = hex4();
            if (low < 0) {
                fail(new ParseError.InvalidEscape(lowStart, text.substring(lowStart, Math.min(text.length(), lowStart + 6))));
                return false;
            }
            if (!Character.isLowSurrogate((char) low)) {
                fail(new ParseError.UnpairedSurrogate(start));
                return false;
            }
            value.append((char) unit).append((char) low);
            return true;
        }
        value.append((char) unit);
        return true;
    }

    /** Reads four hexadecimal digits. Returns -1 if they are not there. */
    private int hex4() {
        if (pos + 4 > text.length()) {
            return -1;
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(text.charAt(pos + i), 16);
            if (digit < 0 || text.charAt(pos + i) > 'f') {
                return -1;
            }
            value = value * 16 + digit;
        }
        pos += 4;
        return value;
    }

    // ---------------------------------------------------------------------------------------
    // Logical expressions
    // ---------------------------------------------------------------------------------------

    /** logical-expr = logical-or-expr */
    private @Nullable LogicalExpression logicalExpression() {
        LogicalExpression first = basicExpression();
        if (first == null) {
            return null;
        }
        LogicalExpression and = andRest(first);
        return and == null ? null : orRest(and);
    }

    /** logical-or-expr = logical-and-expr *(S "||" S logical-and-expr), after the first operand. */
    private @Nullable LogicalExpression orRest(LogicalExpression first) {
        List<LogicalExpression> operands = new ArrayList<>(List.of(first));
        while (true) {
            int start = pos;
            skipBlank();
            if (!text.startsWith("||", pos)) {
                pos = start;
                break;
            }
            pos += 2;
            skipBlank();
            LogicalExpression basic = basicExpression();
            if (basic == null) {
                return null;
            }
            LogicalExpression and = andRest(basic);
            if (and == null) {
                return null;
            }
            operands.add(and);
        }
        return operands.size() == 1 ? first : new LogicalExpression.Or(operands);
    }

    /** logical-and-expr = basic-expr *(S "&&" S basic-expr), after the first operand. */
    private @Nullable LogicalExpression andRest(LogicalExpression first) {
        List<LogicalExpression> operands = new ArrayList<>(List.of(first));
        while (true) {
            int start = pos;
            skipBlank();
            if (!text.startsWith("&&", pos)) {
                pos = start;
                break;
            }
            pos += 2;
            skipBlank();
            LogicalExpression basic = basicExpression();
            if (basic == null) {
                return null;
            }
            operands.add(basic);
        }
        return operands.size() == 1 ? first : new LogicalExpression.And(operands);
    }

    /** basic-expr = paren-expr / comparison-expr / test-expr */
    private @Nullable LogicalExpression basicExpression() {
        int c = peek();
        if (c == '!') {
            pos++;
            skipBlank();
            int start = pos;
            if (peek() == '(') {
                LogicalExpression inner = parenthesized();
                return inner == null ? null : new LogicalExpression.Not(inner);
            }
            Operand operand = operand();
            if (operand == null) {
                return null;
            }
            LogicalExpression test = test(operand, start);
            return test == null ? null : new LogicalExpression.Not(test);
        }
        if (c == '(') {
            return parenthesized();
        }
        int start = pos;
        Operand operand = operand();
        return operand == null ? null : comparisonOrTest(operand, start);
    }

    /** After an operand: a comparison if an operator follows, else a test expression. */
    private @Nullable LogicalExpression comparisonOrTest(Operand left, int leftStart) {
        int afterLeft = pos;
        skipBlank();
        ComparisonOperator operator = comparisonOperator();
        if (operator == null) {
            pos = afterLeft;
            return test(left, leftStart);
        }
        ComparableExpression leftSide = comparable(left, leftStart);
        if (leftSide == null) {
            return null;
        }
        skipBlank();
        int rightStart = pos;
        Operand right = operand();
        if (right == null) {
            return null;
        }
        ComparableExpression rightSide = comparable(right, rightStart);
        return rightSide == null ? null : new LogicalExpression.Comparison(leftSide, operator, rightSide);
    }

    /** paren-expr without the optional "!": "(" S logical-expr S ")" */
    private @Nullable LogicalExpression parenthesized() {
        if (!enter()) {
            return null;
        }
        pos++; // '('
        skipBlank();
        LogicalExpression inner = logicalExpression();
        if (inner == null) {
            return null;
        }
        skipBlank();
        if (!expect(')', "')'")) {
            return null;
        }
        depth--;
        return inner;
    }

    /** comparison-op = "==" / "!=" / "<=" / ">=" / "<" / ">" */
    private @Nullable ComparisonOperator comparisonOperator() {
        for (ComparisonOperator operator : List.of(
                ComparisonOperator.EQUAL,
                ComparisonOperator.NOT_EQUAL,
                ComparisonOperator.LESS_OR_EQUAL,
                ComparisonOperator.GREATER_OR_EQUAL,
                ComparisonOperator.LESS,
                ComparisonOperator.GREATER)) {
            if (text.startsWith(operator.symbol(), pos)) {
                pos += operator.symbol().length();
                return operator;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------
    // Operands: queries, literals, and function calls
    // ---------------------------------------------------------------------------------------

    /** An operand before its context (comparison, test, or argument) is known. */
    private sealed interface Operand {}

    private record QueryOperand(FilterQuery query) implements Operand {}

    private record LiteralOperand(Literal literal) implements Operand {}

    private record FunctionOperand(FunctionCall call) implements Operand {}

    /** A query, a literal, or a function call. */
    private @Nullable Operand operand() {
        int c = peek();
        if (c == '@' || c == '$') {
            pos++;
            List<Segment> segments = segments();
            return segments == null
                    ? null
                    : new QueryOperand(new FilterQuery(c == '@' ? Identifier.CURRENT : Identifier.ROOT, segments));
        }
        if (c == '\'' || c == '"') {
            String value = stringLiteral();
            return value == null ? null : new LiteralOperand(new Literal.StringLiteral(value));
        }
        if (c == '-' || isDigit(c)) {
            Literal.NumberLiteral number = number();
            return number == null ? null : new LiteralOperand(number);
        }
        if (c >= 'a' && c <= 'z') {
            int start = pos;
            while (isFunctionNameChar(peek())) {
                pos++;
            }
            String name = text.substring(start, pos);
            if (peek() == '(') {
                FunctionCall call = functionCall(name, start);
                return call == null ? null : new FunctionOperand(call);
            }
            return switch (name) {
                case "true" -> new LiteralOperand(new Literal.BooleanLiteral(true));
                case "false" -> new LiteralOperand(new Literal.BooleanLiteral(false));
                case "null" -> new LiteralOperand(new Literal.NullLiteral());
                default -> fail(unexpected("'(' after the function name '" + name + "'"));
            };
        }
        fail(unexpected("a query, a literal, or a function expression"));
        return null;
    }

    /** number = (int / "-0") [ frac ] [ exp ] */
    private Literal.@Nullable NumberLiteral number() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        if (!isDigit(peek())) {
            fail(unexpected("a digit"));
            return null;
        }
        if (peek() == '0') {
            pos++;
            if (isDigit(peek())) {
                fail(new ParseError.InvalidInteger(start, text.substring(start, pos + 1)));
                return null;
            }
        } else {
            while (isDigit(peek())) {
                pos++;
            }
        }
        if (peek() == '.') {
            pos++;
            if (!isDigit(peek())) {
                fail(unexpected("a digit after '.'"));
                return null;
            }
            while (isDigit(peek())) {
                pos++;
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            pos++;
            if (peek() == '+' || peek() == '-') {
                pos++;
            }
            if (!isDigit(peek())) {
                fail(unexpected("a digit in the exponent"));
                return null;
            }
            while (isDigit(peek())) {
                pos++;
            }
        }
        String number = text.substring(start, pos);
        try {
            return new Literal.NumberLiteral(new BigDecimal(number));
        } catch (NumberFormatException e) {
            // Only an exponent outside the int range causes this: the text agrees with the grammar.
            fail(new ParseError.NumberOutOfRange(start, number));
            return null;
        }
    }

    /** function-expr = function-name "(" S [function-argument *(S "," S function-argument)] S ")" */
    private @Nullable FunctionCall functionCall(String name, int nameStart) {
        FunctionSignature signature = functions.get(name);
        if (signature == null) {
            fail(new ParseError.UnknownFunction(nameStart, name));
            return null;
        }
        if (!enter()) {
            return null;
        }
        pos++; // '('
        skipBlank();
        List<RawArgument> raw = new ArrayList<>();
        if (peek() != ')') {
            while (true) {
                int start = pos;
                RawArgument argument = argument(start);
                if (argument == null) {
                    return null;
                }
                raw.add(argument);
                skipBlank();
                if (peek() != ',') {
                    break;
                }
                pos++;
                skipBlank();
            }
        }
        if (!expect(')', "',' or ')'")) {
            return null;
        }
        depth--;
        List<FunctionType> parameters = signature.parameterTypes();
        if (raw.size() != parameters.size()) {
            fail(new ParseError.WrongArgumentCount(nameStart, name, parameters.size(), raw.size()));
            return null;
        }
        List<FunctionArgument> arguments = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            FunctionArgument argument = convert(raw.get(i), parameters.get(i), name, i + 1);
            if (argument == null) {
                return null;
            }
            arguments.add(argument);
        }
        return switch (signature.resultType()) {
            case VALUE -> new FunctionCall.Value(name, arguments);
            case LOGICAL -> new FunctionCall.Logical(name, arguments);
            case NODES -> new FunctionCall.Nodes(name, arguments);
        };
    }

    /** A function argument before the parser knows the declared type of its parameter. */
    private sealed interface RawArgument {
        int start();
    }

    /** A literal, a query, or a function call alone. */
    private record BareArgument(Operand operand, int start) implements RawArgument {}

    /** A logical expression that is not only one operand. */
    private record LogicalArgument(LogicalExpression expression, int start) implements RawArgument {}

    /** function-argument = literal / filter-query / logical-expr / function-expr */
    private @Nullable RawArgument argument(int start) {
        int c = peek();
        if (c == '!' || c == '(') {
            LogicalExpression expression = logicalExpression();
            return expression == null ? null : new LogicalArgument(expression, start);
        }
        Operand operand = operand();
        if (operand == null) {
            return null;
        }
        int afterOperand = pos;
        skipBlank();
        boolean operator = text.startsWith("&&", pos) || text.startsWith("||", pos);
        boolean comparison = !operator && comparisonOperator() != null;
        pos = afterOperand;
        if (!operator && !comparison) {
            return new BareArgument(operand, start);
        }
        LogicalExpression first = comparisonOrTest(operand, start);
        if (first == null) {
            return null;
        }
        LogicalExpression and = andRest(first);
        if (and == null) {
            return null;
        }
        LogicalExpression or = orRest(and);
        return or == null ? null : new LogicalArgument(or, start);
    }

    /** Applies the well-typedness rules of RFC 9535, Section 2.4.3 to one argument. */
    private @Nullable FunctionArgument convert(RawArgument raw, FunctionType type, String function, int index) {
        ParseError mismatch = new ParseError.ArgumentTypeMismatch(raw.start(), function, index, type);
        return switch (raw) {
            case LogicalArgument logical ->
                    type == FunctionType.LOGICAL ? new FunctionArgument.Logical(logical.expression()) : fail(mismatch);
            case BareArgument bare -> switch (bare.operand()) {
                case LiteralOperand literal ->
                        type == FunctionType.VALUE ? new FunctionArgument.Value(literal.literal()) : fail(mismatch);
                case QueryOperand query -> switch (type) {
                    case VALUE -> {
                        SingularQuery singular = singular(query.query());
                        yield singular == null
                                ? fail(new ParseError.NonSingularQuery(raw.start()))
                                : new FunctionArgument.Value(singular);
                    }
                    case LOGICAL -> new FunctionArgument.Logical(query.query());
                    case NODES -> new FunctionArgument.Nodes(query.query());
                };
                case FunctionOperand call -> convert(call.call(), type, mismatch);
            };
        };
    }

    /** Applies the well-typedness rules to a function call as an argument (Section 2.4.3). */
    private @Nullable FunctionArgument convert(FunctionCall call, FunctionType type, ParseError mismatch) {
        return switch (call) {
            case FunctionCall.Value value -> type == FunctionType.VALUE ? new FunctionArgument.Value(value) : fail(mismatch);
            case FunctionCall.Logical logical ->
                    type == FunctionType.LOGICAL ? new FunctionArgument.Logical(logical) : fail(mismatch);
            case FunctionCall.Nodes nodes -> switch (type) {
                case VALUE -> fail(mismatch);
                case LOGICAL -> new FunctionArgument.Logical(nodes);
                case NODES -> new FunctionArgument.Nodes(nodes);
            };
        };
    }

    /** An operand as a side of a comparison: a literal, a singular query, or a ValueType function. */
    private @Nullable ComparableExpression comparable(Operand operand, int start) {
        return switch (operand) {
            case LiteralOperand(Literal literal) -> literal;
            case QueryOperand(FilterQuery query) -> {
                SingularQuery singular = singular(query);
                yield singular == null ? fail(new ParseError.NonSingularQuery(start)) : singular;
            }
            case FunctionOperand(FunctionCall call) -> switch (call) {
                case FunctionCall.Value value -> value;
                default -> fail(new ParseError.NotComparable(start, call.name(), call.resultType()));
            };
        };
    }

    /** An operand as a test expression: a query, or a LogicalType or NodesType function. */
    private @Nullable LogicalExpression test(Operand operand, int start) {
        return switch (operand) {
            case LiteralOperand literal -> fail(new ParseError.LiteralWithoutComparison(start));
            case QueryOperand(FilterQuery query) -> query;
            case FunctionOperand(FunctionCall call) -> switch (call) {
                case FunctionCall.Logical logical -> logical;
                case FunctionCall.Nodes nodes -> nodes;
                case FunctionCall.Value value -> fail(new ParseError.ValueTypeInTest(start, value.name()));
            };
        };
    }

    /** Returns the query as a singular query, or null if it is not singular (Section 2.3.5.1). */
    private static @Nullable SingularQuery singular(FilterQuery query) {
        List<SingularSegment> segments = new ArrayList<>();
        for (Segment segment : query.segments()) {
            if (!(segment instanceof Segment.Child child) || child.selectors().size() != 1) {
                return null;
            }
            switch (child.selectors().getFirst()) {
                case Selector.Name(String name) -> segments.add(new SingularSegment.Name(name));
                case Selector.Index index -> segments.add(new SingularSegment.Index(index.index()));
                default -> {
                    return null;
                }
            }
        }
        return new SingularQuery(query.identifier(), segments);
    }

    // ---------------------------------------------------------------------------------------
    // Characters and errors
    // ---------------------------------------------------------------------------------------

    /** Returns the code point at the position, or -1 at the end. */
    private int peek() {
        return pos < text.length() ? text.codePointAt(pos) : -1;
    }

    private boolean expect(char c, String expected) {
        if (peek() == c) {
            pos++;
            return true;
        }
        fail(unexpected(expected));
        return false;
    }

    /** S = *B, B = %x20 / %x09 / %x0A / %x0D */
    private void skipBlank() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            pos++;
        }
    }

    /** Starts one more level of nesting. Returns false if the query is too deep. */
    private boolean enter() {
        if (depth >= maxDepth) {
            fail(new ParseError.NestingTooDeep(pos, maxDepth));
            return false;
        }
        depth++;
        return true;
    }

    private ParseError unexpected(String expected) {
        int c = peek();
        return c < 0 ? new ParseError.UnexpectedEnd(pos, expected) : new ParseError.UnexpectedCharacter(pos, c, expected);
    }

    /**
     * Records the error. Always returns null, so that a caller can return the result.
     *
     * <p>Each caller returns null at once, so the parser records only one error.
     */
    // The method always returns null, so the type parameter hides no unchecked cast.
    @SuppressWarnings("TypeParameterUnusedInFormals")
    private <T> @Nullable T fail(ParseError e) {
        error = e;
        return null;
    }

    private int firstUnpairedSurrogate() {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    i++;
                } else {
                    return i;
                }
            } else if (Character.isLowSurrogate(c)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIntStart(int c) {
        return c == '-' || isDigit(c);
    }

    /** name-first = ALPHA / "_" / %x80-D7FF / %xE000-10FFFF */
    private static boolean isNameFirst(int c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || c == '_'
                || (c >= 0x80 && c <= 0xD7FF)
                || c >= 0xE000; // A code point is never above U+10FFFF.
    }

    private static boolean isFunctionNameChar(int c) {
        return (c >= 'a' && c <= 'z') || isDigit(c) || c == '_';
    }
}
