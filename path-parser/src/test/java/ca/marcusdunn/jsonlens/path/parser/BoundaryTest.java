package ca.marcusdunn.jsonlens.path.parser;

import static ca.marcusdunn.jsonlens.path.parser.Queries.accepts;
import static ca.marcusdunn.jsonlens.path.parser.Queries.parse;
import static ca.marcusdunn.jsonlens.path.parser.Queries.reject;
import static ca.marcusdunn.jsonlens.path.parser.Queries.rejects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.function.FunctionSignature;
import ca.marcusdunn.jsonlens.path.core.function.FunctionType;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exact limits and exact error values, for the boundaries of the grammar. */
class BoundaryTest {

    private static final int LIMIT = JsonPathParser.MAX_NESTING_DEPTH;

    @Test
    @Requirement("4.1/parser-resources")
    void nestingLimitIsExact() {
        // The filter selector is one level, so 255 parentheses are at the limit.
        parse("$[?" + "(".repeat(LIMIT - 1) + "@" + ")".repeat(LIMIT - 1) + "]");
        assertEquals(new ParseError.NestingTooDeep(3 + LIMIT - 1, LIMIT),
                reject("$[?" + "(".repeat(LIMIT) + "@" + ")".repeat(LIMIT) + "]"));
    }

    @Test
    @Requirement("4.1/parser-resources")
    void nestingLevelsEndWithTheirExpression() {
        // Many expressions one after the other are not nested.
        int count = 2 * LIMIT;
        parse("$[?" + "(@) && ".repeat(count) + "(@)]");
        parse("$[?" + "length(@) == 1 && ".repeat(count) + "@]");
        parse("$" + "[?@]".repeat(count));
    }

    @Test
    @Requirement("2.1/well-formed")
    void exactErrors() {
        assertEquals(new ParseError.UnexpectedEnd(4, "an escape character"), reject("$['\\"));
        assertEquals(new ParseError.UnexpectedEnd(9, "the closing quote '"), reject("$['\\u0041"));
        assertEquals(new ParseError.UnexpectedEnd(3, "the closing quote \""), reject("$[\""));
        assertEquals(new ParseError.InvalidInteger(8, "01"), reject("$[?@ == 01]"));
        assertEquals(new ParseError.InvalidInteger(8, "-01"), reject("$[?@ == -01]"));
        assertEquals(new ParseError.InvalidInteger(2, "-0"), reject("$[-0]"));
        assertEquals(new ParseError.UnexpectedCharacter(1, 0, "a segment or the end of the query"), reject("$\u0000"));
        assertEquals(new ParseError.UnexpectedEnd(1, "the root identifier '$'").position(), reject("").position() + 1);
        assertEquals(new ParseError.UnexpectedCharacter(2, '.', "a selector"), reject("$[.]"));
        assertEquals(new ParseError.UnexpectedCharacter(3, '/', "'[', '*', or a member name after '..'"), reject("$../"));
        assertEquals(new ParseError.UnexpectedCharacter(2, '/', "'*' or a member name after '.'"), reject("$./"));
        assertEquals(new ParseError.UnexpectedCharacter(9, ' ', "a digit"), reject("$[?@ == - 1]"));
        assertEquals(new ParseError.UnexpectedCharacter(10, ']', "a digit after '.'"), reject("$[?@ == 1.]"));
        assertEquals(new ParseError.UnexpectedCharacter(11, ']', "a digit in the exponent"), reject("$[?@ == 1e+]"));
        assertEquals(new ParseError.UnexpectedCharacter(3, '#', "a query, a literal, or a function expression"),
                reject("$[?#]"));
        assertEquals(new ParseError.NumberOutOfRange(8, "1e99999999999"), reject("$[?@ == 1e99999999999]"));
        assertEquals(new ParseError.UnknownFunction(3, "nope"), reject("$[?nope()]"));
        assertEquals(new ParseError.WrongArgumentCount(3, "count", 1, 0), reject("$[?count() == 1]"));
    }

    @Test
    @Requirement("1.1/unicode-scalar-values")
    void surrogateAtTheStart() {
        assertEquals(new ParseError.UnpairedSurrogate(0), reject("\ud800$"));
        assertEquals(new ParseError.UnpairedSurrogate(0), reject("\udc00$"));
    }

    @Test
    @Requirement("2.3.1.1/surrogate-pairs")
    void lowSurrogateEscapeBoundaries() {
        assertEquals(new ParseError.UnpairedSurrogate(3), reject("$['\\uD83C\\u0000']"));
        assertEquals(new ParseError.UnpairedSurrogate(3), reject("$['\\uD83C\\uDBFF']"));
        assertEquals(new ParseError.UnpairedSurrogate(3), reject("$['\\uD83C\\uE000']"));
        parse("$['\\uDBFF\\uDFFF']");
        parse("$['\\uD800\\uDC00']");
    }

    @Test
    @Requirement("2.5.1.1/member-name-shorthand")
    void shorthandBoundaries() {
        accepts("$.A", "$.Z", "$.a", "$.z", "$.Az", "$.zA", "$.a0", "$.a9");
        rejects("$.@", "$.[", "$.`", "$.{", "$./", "$.:");
        assertEquals(List.of("a"), List.of(((ca.marcusdunn.jsonlens.path.core.query.Selector.Name) Queries.selector("$.a")).name()));
    }

    @Test
    @Requirement("2.4/function-name")
    void functionNameBoundaries() {
        List<FunctionSignature> names = List.of("a", "z", "az", "za", "a0", "a9", "a_").stream()
                .map(name -> new FunctionSignature(name, FunctionType.LOGICAL, List.of()))
                .toList();
        JsonPathParser parser = switch (JsonPathParser.withFunctions(names)) {
            case Result.Ok<JsonPathParser, FunctionRegistrationError>(JsonPathParser value) -> value;
            case Result.Err<JsonPathParser, FunctionRegistrationError>(FunctionRegistrationError error) ->
                    org.junit.jupiter.api.Assertions.fail(error.message());
        };
        accepts(parser, "$[?a()]", "$[?z()]", "$[?az()]", "$[?za()]", "$[?a0()]", "$[?a9()]", "$[?a_()]");
        rejects(parser, "$[?a`()]", "$[?a{()]", "$[?a/()]", "$[?a:()]", "$[?`()]", "$[?{()]");
    }

    @Test
    @Requirement("3.2/registry")
    void standardParserIsShared() {
        assertSame(JsonPathParser.standard(), JsonPathParser.standard());
        assertEquals(FunctionSignature.STANDARD, JsonPathParser.standard().functions());
    }
}
