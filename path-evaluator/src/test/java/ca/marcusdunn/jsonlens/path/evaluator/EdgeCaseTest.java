package ca.marcusdunn.jsonlens.path.evaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.function.FunctionSignature;
import ca.marcusdunn.jsonlens.path.core.function.FunctionType;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
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
import ca.marcusdunn.jsonlens.path.parser.FunctionRegistrationError;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import ca.marcusdunn.jsonlens.mapped.MappedJson;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Limits, error sequences, extension results, and models that are not well-formed. */
class EdgeCaseTest {

    private static FunctionExtension extension(
            String name, FunctionType result, List<FunctionType> parameters, Instance<?> output) {
        return new FunctionExtension() {
            @Override
            public FunctionSignature signature() {
                return new FunctionSignature(name, result, parameters);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <N> Instance<N> apply(List<Instance<N>> arguments, JsonModel<N> model) {
                if (output instanceof Instance.ValueInstance<?> && arguments.size() == 1) {
                    return arguments.getFirst();
                }
                return (Instance<N>) output;
            }
        };
    }

    private static final List<FunctionExtension> EXTENSIONS = List.of(
            // yes(LogicalType): LogicalType gives its argument; same(NodesType): NodesType gives its argument.
            new FunctionExtension() {
                @Override
                public FunctionSignature signature() {
                    return new FunctionSignature("yes", FunctionType.LOGICAL, List.of(FunctionType.LOGICAL));
                }

                @Override
                public <N> Instance<N> apply(List<Instance<N>> arguments, JsonModel<N> model) {
                    return arguments.getFirst();
                }
            },
            new FunctionExtension() {
                @Override
                public FunctionSignature signature() {
                    return new FunctionSignature("same", FunctionType.NODES, List.of(FunctionType.NODES));
                }

                @Override
                public <N> Instance<N> apply(List<Instance<N>> arguments, JsonModel<N> model) {
                    return arguments.getFirst();
                }
            },
            extension("badlogical", FunctionType.LOGICAL, List.of(), new Instance.NodesInstance<>(List.of())),
            extension("badnodes", FunctionType.NODES, List.of(), new Instance.LogicalInstance<>(true)),
            extension("badvalue", FunctionType.VALUE, List.of(), new Instance.LogicalInstance<>(true)));

    private static final JsonPathEvaluator EVALUATOR = switch (JsonPathEvaluator.withFunctions(EXTENSIONS)) {
        case Result.Ok<JsonPathEvaluator, ExtensionError>(JsonPathEvaluator value) -> value;
        case Result.Err<JsonPathEvaluator, ExtensionError>(ExtensionError error) -> fail(error.message());
    };

    private static final JsonPathParser PARSER = switch (JsonPathParser.withFunctions(
            EXTENSIONS.stream().map(FunctionExtension::signature).toList())) {
        case Result.Ok<JsonPathParser, FunctionRegistrationError>(JsonPathParser value) -> value;
        case Result.Err<JsonPathParser, FunctionRegistrationError>(FunctionRegistrationError error) ->
                fail(error.message());
    };

    private static EvaluationError error(JsonPathEvaluator evaluator, String query, String json) {
        return Eval.error(evaluator, Eval.query(PARSER, query), Eval.jackson(json), Eval.JACKSON);
    }

    private static EvaluationError error(JsonPathQuery query) {
        return Eval.error(EVALUATOR, query, Eval.jackson("[1]"), Eval.JACKSON);
    }

    private static JsonPathQuery filter(LogicalExpression expression) {
        return new JsonPathQuery(List.of(new Segment.Child(List.of(new Selector.Filter(expression)))));
    }

    @Test
    @Requirement("2.4/extension-implementations")
    void logicalAndNodesExtensions() {
        String json = "[[1], [], {\"a\": 1}]";
        assertEquals(List.of("$[0]", "$[2]"), Eval.paths(EVALUATOR, PARSER, json, "$[?yes(@.*)]"));
        assertEquals(List.of("$[0]", "$[2]"), Eval.paths(EVALUATOR, PARSER, json, "$[?yes(same(@.*))]"));
        assertEquals(List.of("$[0]"), Eval.paths(EVALUATOR, PARSER, json, "$[?yes(@[0] == 1 && true == true)]"));
        assertEquals(List.of("$[0]", "$[2]"), Eval.paths(EVALUATOR, PARSER, json, "$[?count(same(same(@.*))) == 1]"));
        assertEquals(List.of("$[0]", "$[2]"), Eval.paths(EVALUATOR, PARSER, json, "$[?same(@.*)]"));
    }

    @Test
    @Requirement("2.4/extension-implementations")
    void extensionsWithTheWrongResultType() {
        assertEquals(new EvaluationError.ExtensionResultMismatch("badlogical", FunctionType.LOGICAL),
                error(EVALUATOR, "$[?badlogical()]", "[1]"));
        assertEquals(new EvaluationError.ExtensionResultMismatch("badnodes", FunctionType.NODES),
                error(EVALUATOR, "$[?badnodes()]", "[1]"));
        // The first error stays when a second error occurs.
        assertEquals(new EvaluationError.ExtensionResultMismatch("badvalue", FunctionType.VALUE),
                error(EVALUATOR, "$[?badvalue() == badnodes()[0]]".replace("badnodes()[0]", "badvalue()"), "[1]"));
    }

    @Test
    @Requirement("2.1/overflow-indication")
    void limitsInFiltersAndSlices() {
        JsonPathEvaluator one = EVALUATOR.withLimits(new JsonPathEvaluator.Limits(1, 20));
        assertEquals(new JsonPathEvaluator.Limits(1, 20), one.limits());
        assertEquals(new EvaluationError.NodelistTooLarge(1), error(one, "$[?@]", "[1, 2]"));
        assertEquals(new EvaluationError.NodelistTooLarge(1), error(one, "$[0:2]", "[1, 2]"));
        assertEquals(new EvaluationError.NodelistTooLarge(1), error(one, "$[::-1]", "[1, 2]"));
        assertEquals(new EvaluationError.NodelistTooLarge(1), error(one, "$[0, 1]", "[1, 2]"));
        // After an overflow in the first operand, the evaluation stops.
        assertEquals(new EvaluationError.RegexTooComplex("a{100}", 20),
                error(one, "$[?match(@, 'a{100}') || @ == 1]", "[\"a\"]"));
    }

    @Test
    @Requirement("2.1/no-validity-errors-at-evaluation")
    void validationOfHandMadeQueries() {
        // A wrong number of arguments, with the correct result type.
        LogicalExpression arity = new LogicalExpression.Comparison(
                new FunctionCall.Value("length", List.of()),
                ca.marcusdunn.jsonlens.path.core.query.ComparisonOperator.EQUAL,
                new Literal.NullLiteral());
        assertEquals(new EvaluationError.SignatureMismatch("length"), error(filter(arity)));
        // After the first error, the validator stops.
        JsonPathQuery twoBad = new JsonPathQuery(List.of(new Segment.Child(List.of(
                new Selector.Index(1L << 60), new Selector.Index(1L << 61)))));
        assertEquals(new EvaluationError.IntegerOutOfRange(1L << 60), error(twoBad));
        JsonPathQuery slice = new JsonPathQuery(List.of(new Segment.Child(List.of(new Selector.Slice(
                Maybe.some(1L << 60), Maybe.some(1L << 61), Maybe.none())))));
        assertEquals(new EvaluationError.IntegerOutOfRange(1L << 60), error(slice));
        LogicalExpression twoUnknown = new LogicalExpression.Or(List.of(
                new FunctionCall.Logical("nope", List.of()), new FunctionCall.Logical("nada", List.of())));
        assertEquals(new EvaluationError.UnknownFunction("nope"), error(filter(twoUnknown)));
        LogicalExpression singular = new LogicalExpression.Comparison(
                new SingularQuery(Identifier.CURRENT, List.of(new SingularSegment.Index(-(1L << 53)))),
                ca.marcusdunn.jsonlens.path.core.query.ComparisonOperator.EQUAL,
                new Literal.NullLiteral());
        assertEquals(new EvaluationError.IntegerOutOfRange(-(1L << 53)), error(filter(singular)));
        // Nested calls deeper than the limit.
        FunctionCall.Nodes deep = new FunctionCall.Nodes("same", List.of(
                new FunctionArgument.Nodes(new FilterQuery(Identifier.CURRENT, List.of()))));
        for (int i = 0; i < JsonPathEvaluator.MAX_QUERY_DEPTH; i++) {
            deep = new FunctionCall.Nodes("same", List.of(new FunctionArgument.Nodes(deep)));
        }
        assertEquals(new EvaluationError.QueryTooDeep(JsonPathEvaluator.MAX_QUERY_DEPTH), error(filter(deep)));
        // A logical argument with a problem inside it.
        FunctionCall.Logical yes = new FunctionCall.Logical("yes", List.of(
                new FunctionArgument.Logical(new FunctionCall.Logical("nope", List.of()))));
        assertEquals(new EvaluationError.UnknownFunction("nope"), error(filter(yes)));
    }

    @Test
    @Requirement("lib/evaluator-exception-free")
    void modelThatIsNotWellFormed() {
        // A missing element does not stop the evaluation of the other nodes.
        assertEquals(List.of("$[0][0]"), Eval.paths(evaluate("$[*][0]", new HalfModel())));
        // The model says that each array has two elements, but it gives no elements.
        JsonModel<Object> model = new EmptyArrayModel();
        for (String query : List.of("$[0]", "$[*]", "$[0:2]", "$..*", "$[?@]")) {
            assertEquals(List.of(), evaluate(query, model), query);
        }
        // Deep equality of arrays whose elements are missing is false.
        assertEquals(List.of("$[0]"), Eval.paths(evaluate("$[?@ == @]", new HalfModel())));
        assertEquals(List.of(), evaluate("$[?$[0] == $[1]]", new HalfModel()));
        assertEquals(List.of(), evaluate("$[?$[1] == $[0]]", new HalfModel()));
    }

    @Test
    @Requirement("2.3.5.2.2/non-interoperable-numbers")
    void numbersWithAnyExponentCompareExactly() {
        String json = "[1e99999999999, 1, -1e99999999999, 1e-99999999999, 10e99999999998]";
        Map<String, List<String>> expected = Map.of(
                "$[?@ > 1]", List.of("$[0]", "$[4]"),
                "$[?@ < 0]", List.of("$[2]"),
                "$[?@ == $[0]]", List.of("$[0]", "$[4]"),
                "$[?@ > 0 && @ < 1]", List.of("$[3]"),
                "$[?@ == 'x']", List.of());
        expected.forEach((query, paths) -> {
            assertEquals(paths, Eval.paths(Eval.nodes(Eval.STANDARD, Eval.query(query), Eval.kotlinx(json), Eval.KOTLINX)), query);
            MappedJson mapped = Eval.mapped(json);
            assertEquals(paths, Eval.paths(Eval.nodes(Eval.STANDARD, Eval.query(query), mapped.root(), Eval.MAPPED)), query);
        });
    }

    @Test
    @Requirement("2.3.5.2.2/string-ordering")
    void longerStringIsGreater() {
        assertEquals(List.of("$[0]"), Eval.paths("[[1, 2]]", "$[?@[-1] == 2]"));
        assertEquals(List.of(), Eval.paths("[[1, 2]]", "$[?@[-3] == 2]"));
        assertEquals(List.of(), Eval.paths("[1]", "$[?'ab' < 'a']"));
        assertEquals(List.of("$[0]"), Eval.paths("[1]", "$[?'a' < 'ab']"));
        assertEquals(List.of(), Eval.paths("[1]", "$[?'b' < 'ab']"));
    }

    @Test
    @Requirement("2.3.5.2.2/array-equality")
    void arraysWithDifferentStrings() {
        assertEquals(List.of(), Eval.paths("{\"a\": [\"x\"], \"b\": [\"y\"]}", "$[?$.a == $.b]"));
        assertEquals(List.of(), Eval.paths("{\"a\": [true], \"b\": [false]}", "$[?$.a == $.b]"));
        assertEquals(List.of(), Eval.paths("{\"a\": [1], \"b\": [\"1\"]}", "$[?$.a == $.b]"));
    }

    @Test
    void messages() {
        assertEquals("The evaluator has no function with the name 'f'.", new EvaluationError.UnknownFunction("f").message());
        assertEquals("The call of the function 'f' does not agree with the signature of the function.",
                new EvaluationError.SignatureMismatch("f").message());
        assertEquals("The integer 9 is outside the range [-(2^53)+1, (2^53)-1].",
                new EvaluationError.IntegerOutOfRange(9).message());
        assertEquals("The query has more than 3 nested expressions.", new EvaluationError.QueryTooDeep(3).message());
        assertEquals("Overflow: a nodelist has more than 3 nodes.", new EvaluationError.NodelistTooLarge(3).message());
        assertEquals("Overflow: the regular expression 'a' needs more than 3 instructions or nested groups.",
                new EvaluationError.RegexTooComplex("a", 3).message());
        assertEquals("The function extension 'f' did not return an instance of its result type NODES.",
                new EvaluationError.ExtensionResultMismatch("f", FunctionType.NODES).message());
        assertEquals("The function name 'A' is not valid. It must start with a lowercase letter, followed by "
                + "lowercase letters, digits, and '_'.", new ExtensionError.InvalidName("A").message());
        assertEquals("More than one function has the name 'f'.", new ExtensionError.DuplicateName("f").message());
    }

    @Test
    void registration() {
        assertInstanceOf(Result.Ok.class, JsonPathEvaluator.withFunctions(EXTENSIONS));
        assertEquals(Result.err(new ExtensionError.DuplicateName("yes")),
                JsonPathEvaluator.withFunctions(List.of(EXTENSIONS.get(0), EXTENSIONS.get(0))));
        // The five standard functions and the five extensions.
        assertEquals(10, ((JsonPathEvaluator) ((Result.Ok<?, ?>) JsonPathEvaluator.withFunctions(EXTENSIONS)).value())
                .functions().size());
        assertEquals(Result.err(new ExtensionError.InvalidName("Bad")), JsonPathEvaluator.withFunctions(
                List.of(extension("Bad", FunctionType.VALUE, List.of(), new Instance.LogicalInstance<>(true)))));
        assertSame(JsonPathEvaluator.standard(), JsonPathEvaluator.standard());
        assertEquals(JsonPathEvaluator.Limits.DEFAULT, JsonPathEvaluator.standard().limits());
        assertEquals(FunctionSignature.STANDARD, JsonPathEvaluator.standard().functions());
    }

    private static List<Node<Object>> evaluate(String query, JsonModel<Object> model) {
        return switch (Eval.STANDARD.evaluate(Eval.query(query), EmptyArrayModel.ROOT, model)) {
            case Result.Ok<List<Node<Object>>, EvaluationError>(List<Node<Object>> nodes) -> nodes;
            case Result.Err<List<Node<Object>>, EvaluationError>(EvaluationError error) -> fail(error.message());
        };
    }

    /** A model of one array that says it has two elements, but gives none. */
    private static class EmptyArrayModel implements JsonModel<Object> {
        static final Object ROOT = new Object();

        @Override
        public JsonKind kind(Object node) {
            return JsonKind.ARRAY;
        }

        @Override
        public int arrayLength(Object array) {
            return 2;
        }

        @Override
        public Maybe<Object> element(Object array, int index) {
            return Maybe.none();
        }

        @Override
        public MemberCursor<Object> memberCursor(Object object) {
            return MemberCursor.of(List.of());
        }

        @Override
        public JsonString stringValue(Object string) {
            return JsonString.of("");
        }

        @Override
        public JsonNumber numberValue(Object number) {
            return JsonNumber.of(0);
        }

    }

    /**
     * A model of an array with two arrays of length 1. The first gives its element, a number. The
     * second gives no element.
     */
    private static final class HalfModel extends EmptyArrayModel {
        static final Object FIRST = new Object();
        static final Object SECOND = new Object();
        static final Object NUMBER = new Object();

        @Override
        public JsonKind kind(Object node) {
            return node.equals(NUMBER) ? JsonKind.NUMBER : JsonKind.ARRAY;
        }

        @Override
        public int arrayLength(Object array) {
            return array.equals(ROOT) ? 2 : 1;
        }

        @Override
        public Maybe<Object> element(Object array, int index) {
            if (array.equals(ROOT)) {
                return Maybe.some(index == 0 ? FIRST : SECOND);
            }
            return array.equals(FIRST) ? Maybe.some(NUMBER) : Maybe.none();
        }
    }
}
