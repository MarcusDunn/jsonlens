package ca.marcusdunn.jsonlens.path.parser;

import ca.marcusdunn.jsonlens.path.core.function.FunctionType;

/// The reason why a query is not well-formed or not valid (RFC 9535, Section 2.1).
///
/// Each error is a record, so a `switch` can match the errors that need special handling.
/// [#message()] gives a description of all errors:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.ParsingSnippets region=errors}
///
/// ## Errors
///
/// | Error | Example query |
/// |---|---|
/// | [UnexpectedCharacter] | `$.a b`, `$[?@.a = 1]` |
/// | [UnexpectedEnd] | `$[0`, `$['a` |
/// | [InvalidUtf8] | bytes that are not UTF-8 |
/// | [UnpairedSurrogate] | a string with half of a surrogate pair |
/// | [ControlCharacter] | a string literal with an unescaped tab character |
/// | [InvalidEscape] | `$['\a']` |
/// | [InvalidInteger] | `$[01]`, `$[-0]` |
/// | [IntegerOutOfRange] | `$[9007199254740992]` |
/// | [NumberOutOfRange] | `$[?@ == 1e99999999999]` |
/// | [UnknownFunction] | `$[?foo(@)]` |
/// | [WrongArgumentCount] | `$[?length() == 1]` |
/// | [ArgumentTypeMismatch] | `$[?count(1) == 1]` |
/// | [NonSingularQuery] | `$[?@.* == 1]`, `$[?length(@.*) == 1]` |
/// | [NotComparable] | `$[?match(@, 'a') == true]` |
/// | [ValueTypeInTest] | `$[?length(@)]` |
/// | [LiteralWithoutComparison] | `$[?1]`, `$[?true]` |
/// | [NestingTooDeep] | more than [JsonPathParser#MAX_NESTING_DEPTH] nested parentheses |
///
/// ## Positions
///
/// Each error has a position. For query text, the position is the index of a UTF-16 code
/// unit in the text. For UTF-8 bytes, [InvalidUtf8] gives the index of a byte; all other
/// errors give the index in the decoded text.
public sealed interface ParseError {

    /// Returns the position of the error.
    ///
    /// @return the position of the error
    int position();

    /// Returns a description of the error.
    ///
    /// @return a description of the error, with the position
    default String message() {
        String reason = switch (this) {
            case UnexpectedCharacter e -> "Expected " + e.expected() + ", but found " + describe(e.codePoint()) + ".";
            case UnexpectedEnd e -> "Expected " + e.expected() + ", but the query ends.";
            case InvalidUtf8 e -> "The bytes are not well-formed UTF-8.";
            case UnpairedSurrogate e -> "The query contains an unpaired surrogate. It is not a Unicode scalar value.";
            case ControlCharacter e ->
                    "A string cannot contain the control character " + describe(e.codePoint()) + ". Use an escape.";
            case InvalidEscape e -> "The escape '" + e.sequence() + "' is not valid.";
            case InvalidInteger e ->
                    "The integer '" + e.text() + "' is not valid. An integer must not have a leading zero or be '-0'.";
            case IntegerOutOfRange e ->
                    "The integer '" + e.text() + "' is outside the range [-(2^53)+1, (2^53)-1].";
            case NumberOutOfRange e -> "The number '" + e.text() + "' is too large to represent.";
            case UnknownFunction e -> "There is no function with the name '" + e.name() + "'.";
            case WrongArgumentCount e ->
                    "The function '" + e.function() + "' takes " + e.expected() + " arguments, but the query gives "
                            + e.actual() + ".";
            case ArgumentTypeMismatch e ->
                    "Argument " + e.argument() + " of the function '" + e.function() + "' must be of the type "
                            + typeName(e.expected()) + ".";
            case NonSingularQuery e ->
                    "The query can select more than one node. Only a singular query can supply a value.";
            case NotComparable e ->
                    "The function '" + e.function() + "' has the result type " + typeName(e.type())
                            + ". Only ValueType can be compared.";
            case ValueTypeInTest e ->
                    "The function '" + e.function() + "' has the result type ValueType. It cannot be a test.";
            case LiteralWithoutComparison e -> "A literal can only be a side of a comparison.";
            case NestingTooDeep e ->
                    "The query has more than " + e.limit() + " levels of nested filters, parentheses, and functions.";
        };
        return reason + " Position: " + position() + ".";
    }

    private static String describe(int codePoint) {
        if (codePoint >= 0x20 && codePoint != 0x7F) {
            return "'" + Character.toString(codePoint) + "'";
        }
        return String.format("U+%04X", codePoint);
    }

    private static String typeName(FunctionType type) {
        return switch (type) {
            case VALUE -> "ValueType";
            case LOGICAL -> "LogicalType";
            case NODES -> "NodesType";
        };
    }

    /// The grammar does not permit the character at this position.
    ///
    /// @param position the position
    /// @param codePoint the character
    /// @param expected a description of what the grammar permits
    record UnexpectedCharacter(int position, int codePoint, String expected) implements ParseError {}

    /// The query ends before it is complete.
    ///
    /// @param position the position of the end
    /// @param expected a description of what the grammar requires
    record UnexpectedEnd(int position, String expected) implements ParseError {}

    /// The bytes are not well-formed UTF-8 (Section 2.1).
    ///
    /// @param position the index of the first byte that is not valid
    record InvalidUtf8(int position) implements ParseError {}

    /// The query contains an unpaired surrogate, or a backslash-u escape for one
    /// (Sections 1.1 and 2.3.1.1).
    ///
    /// @param position the position of the surrogate or the escape
    record UnpairedSurrogate(int position) implements ParseError {}

    /// A string literal contains an unescaped control character, U+0000 to U+001F (Section 2.3.1.1).
    ///
    /// @param position the position
    /// @param codePoint the control character
    record ControlCharacter(int position, int codePoint) implements ParseError {}

    /// A string literal contains an escape that is not permitted (Section 2.3.1.1).
    ///
    /// @param position the position of the backslash
    /// @param sequence the escape
    record InvalidEscape(int position, String sequence) implements ParseError {}

    /// An integer has a leading zero, or is `-0` where only an int is permitted.
    ///
    /// @param position the position
    /// @param text the integer text
    record InvalidInteger(int position, String text) implements ParseError {}

    /// An index or a slice parameter is outside the I-JSON range (Section 2.1).
    ///
    /// @param position the position
    /// @param text the integer text
    record IntegerOutOfRange(int position, String text) implements ParseError {}

    /// A number literal has an exponent that is too large to represent.
    ///
    /// @param position the position
    /// @param text the number text
    record NumberOutOfRange(int position, String text) implements ParseError {}

    /// The query calls a function that is not registered (Section 2.4).
    ///
    /// @param position the position of the function name
    /// @param name the function name
    record UnknownFunction(int position, String name) implements ParseError {}

    /// A function call has the wrong number of arguments (Section 2.4.3).
    ///
    /// @param position the position of the function name
    /// @param function the function name
    /// @param expected the number of parameters
    /// @param actual the number of arguments
    record WrongArgumentCount(int position, String function, int expected, int actual) implements ParseError {}

    /// An argument does not agree with the declared type of its parameter (Section 2.4.3).
    ///
    /// @param position the position of the argument
    /// @param function the function name
    /// @param argument the number of the argument, starting at 1
    /// @param expected the declared type of the parameter
    record ArgumentTypeMismatch(int position, String function, int argument, FunctionType expected)
            implements ParseError {}

    /// A query that is not singular is a side of a comparison, or a ValueType argument
    /// (Sections 2.3.5.1 and 2.4.3).
    ///
    /// @param position the position of the query
    record NonSingularQuery(int position) implements ParseError {}

    /// A function with the result type LogicalType or NodesType is a side of a comparison
    /// (Section 2.4.3).
    ///
    /// @param position the position of the function name
    /// @param function the function name
    /// @param type the declared result type
    record NotComparable(int position, String function, FunctionType type) implements ParseError {}

    /// A function with the result type ValueType is a test expression (Section 2.4.3).
    ///
    /// @param position the position of the function name
    /// @param function the function name
    record ValueTypeInTest(int position, String function) implements ParseError {}

    /// A literal is a test expression. A literal can only be a side of a comparison
    /// (Section 2.3.5.1).
    ///
    /// @param position the position of the literal
    record LiteralWithoutComparison(int position) implements ParseError {}

    /// The query has too many nested levels (Section 4.1).
    ///
    /// @param position the position where the limit is passed
    /// @param limit the maximum number of levels
    record NestingTooDeep(int position, int limit) implements ParseError {}
}
