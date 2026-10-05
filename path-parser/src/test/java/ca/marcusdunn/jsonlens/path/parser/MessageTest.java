package ca.marcusdunn.jsonlens.path.parser;

import static ca.marcusdunn.jsonlens.path.parser.Queries.reject;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.path.core.function.FunctionType;
import org.junit.jupiter.api.Test;

/** The descriptions of parse errors and registration errors. */
class MessageTest {

    @Test
    void parseErrorMessages() {
        assertEquals("Expected ']', but found 'x'. Position: 3.",
                new ParseError.UnexpectedCharacter(3, 'x', "']'").message());
        assertEquals("Expected ']', but found U+0007. Position: 3.",
                new ParseError.UnexpectedCharacter(3, 7, "']'").message());
        assertEquals("Expected ']', but found U+007F. Position: 3.",
                new ParseError.UnexpectedCharacter(3, 0x7F, "']'").message());
        assertEquals("Expected ']', but found ' '. Position: 3.",
                new ParseError.UnexpectedCharacter(3, ' ', "']'").message());
        assertEquals("Expected ']', but found U+001F. Position: 3.",
                new ParseError.UnexpectedCharacter(3, 0x1F, "']'").message());
        assertEquals("Expected ']', but the query ends. Position: 2.", new ParseError.UnexpectedEnd(2, "']'").message());
        assertEquals("The bytes are not well-formed UTF-8. Position: 4.", new ParseError.InvalidUtf8(4).message());
        assertEquals("The query contains an unpaired surrogate. It is not a Unicode scalar value. Position: 1.",
                new ParseError.UnpairedSurrogate(1).message());
        assertEquals("A string cannot contain the control character U+0009. Use an escape. Position: 3.",
                new ParseError.ControlCharacter(3, 9).message());
        assertEquals("The escape '\\x' is not valid. Position: 3.", new ParseError.InvalidEscape(3, "\\x").message());
        assertEquals("The integer '01' is not valid. An integer must not have a leading zero or be '-0'. Position: 2.",
                new ParseError.InvalidInteger(2, "01").message());
        assertEquals("The integer '9007199254740992' is outside the range [-(2^53)+1, (2^53)-1]. Position: 2.",
                new ParseError.IntegerOutOfRange(2, "9007199254740992").message());
        assertEquals("The number '1e99999999999' is too large to represent. Position: 8.",
                new ParseError.NumberOutOfRange(8, "1e99999999999").message());
        assertEquals("There is no function with the name 'foo'. Position: 3.",
                new ParseError.UnknownFunction(3, "foo").message());
        assertEquals("The function 'length' takes 1 arguments, but the query gives 2. Position: 3.",
                new ParseError.WrongArgumentCount(3, "length", 1, 2).message());
        assertEquals("Argument 1 of the function 'count' must be of the type NodesType. Position: 9.",
                new ParseError.ArgumentTypeMismatch(9, "count", 1, FunctionType.NODES).message());
        assertEquals("Argument 2 of the function 'f' must be of the type ValueType. Position: 9.",
                new ParseError.ArgumentTypeMismatch(9, "f", 2, FunctionType.VALUE).message());
        assertEquals("The query can select more than one node. Only a singular query can supply a value. Position: 3.",
                new ParseError.NonSingularQuery(3).message());
        assertEquals("The function 'match' has the result type LogicalType. Only ValueType can be compared. Position: 3.",
                new ParseError.NotComparable(3, "match", FunctionType.LOGICAL).message());
        assertEquals("The function 'length' has the result type ValueType. It cannot be a test. Position: 3.",
                new ParseError.ValueTypeInTest(3, "length").message());
        assertEquals("A literal can only be a side of a comparison. Position: 3.",
                new ParseError.LiteralWithoutComparison(3).message());
        assertEquals("The query has more than 256 levels of nested filters, parentheses, and functions. Position: 9.",
                new ParseError.NestingTooDeep(9, 256).message());
    }

    @Test
    void parsedErrorsHaveMessages() {
        assertEquals("Expected the root identifier '$', but found 'a'. Position: 0.", reject("a").message());
    }

    @Test
    void registrationErrorMessages() {
        assertEquals(
                "The function name 'A' is not valid. It must start with a lowercase letter, followed by lowercase "
                        + "letters, digits, and '_'.",
                new FunctionRegistrationError.InvalidName("A").message());
        assertEquals("More than one function has the name 'f'.", new FunctionRegistrationError.DuplicateName("f").message());
    }
}
