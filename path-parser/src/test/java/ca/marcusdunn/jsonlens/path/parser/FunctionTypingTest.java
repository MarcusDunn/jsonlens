package ca.marcusdunn.jsonlens.path.parser;

import static ca.marcusdunn.jsonlens.path.parser.Queries.accepts;
import static ca.marcusdunn.jsonlens.path.parser.Queries.filter;
import static ca.marcusdunn.jsonlens.path.parser.Queries.parse;
import static ca.marcusdunn.jsonlens.path.parser.Queries.reject;
import static ca.marcusdunn.jsonlens.path.parser.Queries.rejects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.function.FunctionSignature;
import ca.marcusdunn.jsonlens.path.core.function.FunctionType;
import ca.marcusdunn.jsonlens.path.core.query.FilterQuery;
import ca.marcusdunn.jsonlens.path.core.query.FunctionArgument;
import ca.marcusdunn.jsonlens.path.core.query.FunctionCall;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.core.query.Literal;
import ca.marcusdunn.jsonlens.path.core.query.LogicalExpression;
import ca.marcusdunn.jsonlens.path.core.query.Selector;
import ca.marcusdunn.jsonlens.path.core.query.SingularQuery;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Section 2.4: function expressions and well-typedness. */
class FunctionTypingTest {

    private static final FunctionType V = FunctionType.VALUE;
    private static final FunctionType L = FunctionType.LOGICAL;
    private static final FunctionType N = FunctionType.NODES;

    /** A parser with the standard functions and one function for each pair of types. */
    private static final JsonPathParser EXTENDED = extended(List.of(
            new FunctionSignature("foo", N, List.of(N)),
            new FunctionSignature("nodes", N, List.of(N)),
            new FunctionSignature("bar", L, List.of(V)),
            new FunctionSignature("barl", L, List.of(L)),
            new FunctionSignature("barn", L, List.of(N)),
            new FunctionSignature("bnl", L, List.of(N)),
            new FunctionSignature("blt", L, List.of(L)),
            new FunctionSignature("bal", L, List.of(V)),
            new FunctionSignature("val", V, List.of(V)),
            new FunctionSignature("none", V, List.of()),
            new FunctionSignature("three", V, List.of(V, L, N))));

    private static JsonPathParser extended(List<FunctionSignature> extensions) {
        return switch (JsonPathParser.withFunctions(extensions)) {
            case Result.Ok<JsonPathParser, FunctionRegistrationError>(JsonPathParser parser) -> parser;
            case Result.Err<JsonPathParser, FunctionRegistrationError>(FunctionRegistrationError error) ->
                    fail(error.message());
        };
    }

    private static FunctionCall call(JsonPathParser parser, String query) {
        JsonPathQuery parsed = parse(parser, query);
        LogicalExpression expression =
                assertInstanceOf(Selector.Filter.class, parsed.segments().getFirst().selectors().getFirst()).expression();
        return switch (expression) {
            case FunctionCall.Logical logical -> logical;
            case FunctionCall.Nodes nodes -> nodes;
            case LogicalExpression.Comparison comparison -> assertInstanceOf(FunctionCall.Value.class, comparison.left());
            default -> fail("not a function call: " + expression);
        };
    }

    @Test
    @Requirement("2.4/function-name")
    void functionNameSyntax() {
        for (String name : List.of("a", "a_1", "abc9", "z__")) {
            assertEquals(Result.ok(true), JsonPathParser.withFunctions(
                    List.of(new FunctionSignature(name, L, List.of()))).map(p -> true));
        }
        for (String name : List.of("", "A", "aB", "1a", "_a", "a-b", "a b", "é")) {
            assertEquals(
                    Result.err(new FunctionRegistrationError.InvalidName(name)),
                    JsonPathParser.withFunctions(List.of(new FunctionSignature(name, L, List.of()))));
        }
        rejects("$[?Length(@) == 1]", "$[?_count(@.*) == 1]", "$[?1count(@.*) == 1]");
    }

    @Test
    @Requirement("2.4/function-expr")
    void functionExpressionSyntax() {
        accepts("$[?length( @ ) == 1]", "$[?match(@.a,'x')]", "$[?match( @.a , 'x' )]");
        accepts(EXTENDED, "$[?none() == 1]", "$[?none( ) == 1]");
        reject("$[?length (@) == 1]", ParseError.UnexpectedCharacter.class);
        rejects("$[?length(@,) == 1]", "$[?length(,@) == 1]", "$[?length(@ == 1]", "$[?length@ == 1]");
    }

    @Test
    @Requirement("2.4/function-arguments")
    void fourKindsOfArgument() {
        assertEquals(
                List.of(new FunctionArgument.Value(new Literal.NumberLiteral(java.math.BigDecimal.ONE))),
                call(EXTENDED, "$[?bal(1)]").arguments());
        assertInstanceOf(FilterQuery.class,
                assertInstanceOf(FunctionArgument.Nodes.class, call(EXTENDED, "$[?barn(@.*)]").arguments().getFirst())
                        .expression());
        assertInstanceOf(LogicalExpression.Comparison.class,
                assertInstanceOf(FunctionArgument.Logical.class, call(EXTENDED, "$[?blt(@.a == 1)]").arguments().getFirst())
                        .expression());
        assertInstanceOf(FunctionCall.Value.class,
                assertInstanceOf(FunctionArgument.Value.class, call(EXTENDED, "$[?bal(length(@))]").arguments().getFirst())
                        .expression());
        assertEquals(
                List.of(V, L, N),
                call(EXTENDED, "$[?three(@.a, @.b, @.c) == 1]").arguments().stream().map(FunctionArgument::type).toList());
    }

    @Test
    @Requirement("2.4/unknown-function")
    void unknownFunctionsAreRejected() {
        assertEquals(new ParseError.UnknownFunction(3, "foo"), reject("$[?foo(@)]"));
        assertEquals(new ParseError.UnknownFunction(10, "eval"), reject("$[?@.a == eval('1')]"));
    }

    @Test
    @Requirement("2.4/extension-signatures")
    void extensionSignatures() {
        accepts(EXTENDED, "$[?bar(@.a)]", "$[?count(foo(@.*)) == 1]");
        rejects(Queries.STANDARD, "$[?bar(@.a)]");
        assertEquals(
                Result.err(new FunctionRegistrationError.DuplicateName("length")),
                JsonPathParser.withFunctions(List.of(new FunctionSignature("length", V, List.of(V)))));
        assertEquals(
                Result.err(new FunctionRegistrationError.DuplicateName("twice")),
                JsonPathParser.withFunctions(List.of(
                        new FunctionSignature("twice", V, List.of()), new FunctionSignature("twice", L, List.of()))));
        assertEquals(16, EXTENDED.functions().size());
    }

    @Test
    @Requirement("2.4.1/declared-types")
    void callsCarryTheirDeclaredResultType() {
        assertEquals(V, call(Queries.STANDARD, "$[?length(@) == 1]").resultType());
        assertEquals(L, call(Queries.STANDARD, "$[?match(@, 'a')]").resultType());
        assertEquals(N, call(EXTENDED, "$[?nodes(@.*)]").resultType());
        assertEquals(List.of(V, V), FunctionSignature.MATCH.parameterTypes());
    }

    @Test
    @Requirement("2.4.1/logical-not-boolean")
    void logicalTypeIsNotComparable() {
        assertEquals(
                new ParseError.NotComparable(3, "match", L),
                reject("$[?match(@.a, 'x') == true]"));
        reject("$[?true == search(@.a, 'x')]", ParseError.NotComparable.class);
    }

    @Test
    @Requirement("2.4.2/no-nodes-to-value")
    void nodesTypeIsNotValueType() {
        reject(EXTENDED, "$[?nodes(@.*) == 1]", ParseError.NotComparable.class);
        reject(EXTENDED, "$[?length(nodes(@.*)) == 1]", ParseError.ArgumentTypeMismatch.class);
        reject(EXTENDED, "$[?bal(nodes(@.*))]", ParseError.ArgumentTypeMismatch.class);
    }

    @Test
    @Requirement("2.4.3/test-expr-context")
    void testExpressionContext() {
        accepts(EXTENDED, "$[?match(@.a, 'x')]", "$[?nodes(@.*)]", "$[?!nodes(@.*)]", "$[?bar(1) && nodes(@)]");
        assertEquals(new ParseError.ValueTypeInTest(3, "length"), reject("$[?length(@.a)]"));
        reject("$[?count(@.*)]", ParseError.ValueTypeInTest.class);
        reject("$[?!value(@.*)]", ParseError.ValueTypeInTest.class);
        reject("$[?@.a && length(@)]", ParseError.ValueTypeInTest.class);
    }

    @Test
    @Requirement("2.4.3/comparable-context")
    void comparableContext() {
        accepts("$[?count(@.*) == 1]", "$[?1 < length(@)]", "$[?value(@..a) == value(@..b)]");
        reject("$[?match(@.a, 'b') == 1]", ParseError.NotComparable.class);
        reject(EXTENDED, "$[?1 == nodes(@)]", ParseError.NotComparable.class);
    }

    @Test
    @Requirement("2.4.3/argument-context")
    void argumentContext() {
        // ValueType parameter: literal, singular query, or ValueType function.
        accepts(EXTENDED, "$[?bar(1)]", "$[?bar('a')]", "$[?bar(null)]", "$[?bar(@.a[0])]", "$[?bar($)]", "$[?bar(val(1))]");
        reject(EXTENDED, "$[?bar(@.*)]", ParseError.NonSingularQuery.class);
        reject(EXTENDED, "$[?bar(@..a)]", ParseError.NonSingularQuery.class);
        reject(EXTENDED, "$[?bar(@.a == 1)]", ParseError.ArgumentTypeMismatch.class);
        reject(EXTENDED, "$[?bar(!@.a)]", ParseError.ArgumentTypeMismatch.class);
        reject(EXTENDED, "$[?bar(match(@, 'a'))]", ParseError.ArgumentTypeMismatch.class);
        // LogicalType parameter: logical expression, query, LogicalType or NodesType function.
        accepts(EXTENDED, "$[?barl(@.a)]", "$[?barl(@.*)]", "$[?barl(@.a == 1)]", "$[?barl(!@.a)]",
                "$[?barl((@.a))]", "$[?barl(@.a && @.b)]", "$[?barl(match(@, 'a'))]", "$[?barl(nodes(@.*))]");
        reject(EXTENDED, "$[?barl(1)]", ParseError.ArgumentTypeMismatch.class);
        reject(EXTENDED, "$[?barl(length(@))]", ParseError.ArgumentTypeMismatch.class);
        // NodesType parameter: query or NodesType function.
        accepts(EXTENDED, "$[?barn(@)]", "$[?barn(@.*)]", "$[?barn($..a)]", "$[?barn(nodes(@))]");
        reject(EXTENDED, "$[?barn(1)]", ParseError.ArgumentTypeMismatch.class);
        reject(EXTENDED, "$[?barn(@.a == 1)]", ParseError.ArgumentTypeMismatch.class);
        reject(EXTENDED, "$[?barn(match(@, 'a'))]", ParseError.ArgumentTypeMismatch.class);
        reject(EXTENDED, "$[?barn(length(@))]", ParseError.ArgumentTypeMismatch.class);
        // A logical expression in an argument that is not complete.
        reject(EXTENDED, "$[?barl(@.a && )]", ParseError.UnexpectedCharacter.class);
        reject(EXTENDED, "$[?barl(@.a || @.b && )]", ParseError.UnexpectedCharacter.class);
        reject(EXTENDED, "$[?barl(@.a == )]", ParseError.UnexpectedCharacter.class);
        reject(EXTENDED, "$[?barl(@.* == 1)]", ParseError.NonSingularQuery.class);
        assertEquals(new ParseError.ArgumentTypeMismatch(17, "three", 3, N), reject(EXTENDED, "$[?three(1, @.a, 1) == 1]"));
        assertEquals(new ParseError.ArgumentTypeMismatch(12, "three", 2, L), reject(EXTENDED, "$[?three(1, 1, 1) == 1]"));
    }

    @Test
    @Requirement("2.4.3/arity")
    void argumentCount() {
        assertEquals(new ParseError.WrongArgumentCount(3, "length", 1, 0), reject("$[?length() == 1]"));
        assertEquals(new ParseError.WrongArgumentCount(3, "length", 1, 2), reject("$[?length(@.a, @.b) == 1]"));
        reject("$[?match(@.a)]", ParseError.WrongArgumentCount.class);
        reject("$[?search(@.a, 'a', 'b')]", ParseError.WrongArgumentCount.class);
        reject(EXTENDED, "$[?none(1) == 1]", ParseError.WrongArgumentCount.class);
    }

    @Test
    @Requirement("2.4.4/signature")
    void lengthSignature() {
        assertEquals(new FunctionSignature("length", V, List.of(V)), FunctionSignature.LENGTH);
        assertInstanceOf(SingularQuery.class,
                ((FunctionArgument.Value) call(Queries.STANDARD, "$[?length(@.a) == 1]").arguments().getFirst()).expression());
    }

    @Test
    @Requirement("2.4.5/signature")
    void countSignature() {
        assertEquals(new FunctionSignature("count", V, List.of(N)), FunctionSignature.COUNT);
        assertInstanceOf(FunctionArgument.Nodes.class, call(Queries.STANDARD, "$[?count(@.a) == 1]").arguments().getFirst());
    }

    @Test
    @Requirement("2.4.6/signature")
    void matchSignature() {
        assertEquals(new FunctionSignature("match", L, List.of(V, V)), FunctionSignature.MATCH);
        assertInstanceOf(FunctionCall.Logical.class, filter("$[?match(@.a, 'x')]"));
    }

    @Test
    @Requirement("2.4.7/signature")
    void searchSignature() {
        assertEquals(new FunctionSignature("search", L, List.of(V, V)), FunctionSignature.SEARCH);
        assertInstanceOf(FunctionCall.Logical.class, filter("$[?search(@.a, 'x')]"));
    }

    @Test
    @Requirement("2.4.8/signature")
    void valueSignature() {
        assertEquals(new FunctionSignature("value", V, List.of(N)), FunctionSignature.VALUE);
        assertInstanceOf(FunctionArgument.Nodes.class, call(Queries.STANDARD, "$[?value(@..a) == 1]").arguments().getFirst());
    }

    @Test
    @Requirement("2.4.9/examples")
    @Requirement("2.1/well-typed")
    void table14() {
        accepts("$[?length(@) < 3]");
        reject("$[?length(@.*) < 3]", ParseError.NonSingularQuery.class);
        accepts("$[?count(@.*) == 1]");
        reject("$[?count(1) == 1]", ParseError.ArgumentTypeMismatch.class);
        accepts(EXTENDED, "$[?count(foo(@.*)) == 1]");
        accepts("$[?match(@.timezone, 'Europe/.*')]");
        reject("$[?match(@.timezone, 'Europe/.*') == true]", ParseError.NotComparable.class);
        accepts("$[?value(@..color) == \"red\"]");
        reject("$[?value(@..color)]", ParseError.ValueTypeInTest.class);
        // bar(): a parameter of any declared type and result type LogicalType.
        accepts(EXTENDED, "$[?bar(@.a)]", "$[?barl(@.a)]", "$[?barn(@.a)]");
        // bnl(): a parameter of declared type NodesType or LogicalType and result type LogicalType.
        accepts(EXTENDED, "$[?bnl(@.*)]", "$[?barl(@.*)]");
        accepts(EXTENDED, "$[?blt(1==1)]");
        reject(EXTENDED, "$[?blt(1)]", ParseError.ArgumentTypeMismatch.class);
        accepts(EXTENDED, "$[?bal(1)]");
    }

    @Test
    @Requirement("3.2/registry")
    void standardFunctionsAreRegistered() {
        assertEquals(
                List.of("length", "count", "match", "search", "value"),
                Queries.STANDARD.functions().stream().map(FunctionSignature::name).toList());
        assertEquals(FunctionSignature.STANDARD, Queries.STANDARD.functions());
        parse("$[?length(@) == count(@) && match(@, 'a') && search(@, 'a') && value(@) == 1]");
    }
}
