package ca.marcusdunn.jsonlens.path.evaluator;

import static ca.marcusdunn.jsonlens.path.evaluator.Eval.paths;
import static ca.marcusdunn.jsonlens.path.evaluator.Eval.jsonValues;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.function.FunctionSignature;
import ca.marcusdunn.jsonlens.path.core.function.FunctionType;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.path.parser.FunctionRegistrationError;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Section 2.4: function extensions and the standard functions. */
class FunctionSemanticsTest {

    /** upper(ValueType): ValueType. The upper case of a string, else Nothing. It builds a new string. */
    private static final BuildingFunctionExtension UPPER = new BuildingFunctionExtension() {
        @Override
        public FunctionSignature signature() {
            return new FunctionSignature("upper", FunctionType.VALUE, List.of(FunctionType.VALUE));
        }

        @Override
        public <N, M extends JsonModel<N> & JsonFactory<N>> Instance<N> apply(List<Instance<N>> arguments, M model) {
            if (arguments.getFirst() instanceof Instance.ValueInstance<N>(Maybe.Some<N>(N node))
                    && model.kind(node) == JsonKind.STRING) {
                String upper = JsonString.copyOf(model.stringValue(node)).toUpperCase(Locale.ROOT);
                return new Instance.ValueInstance<>(Maybe.some(model.string(JsonString.of(upper))));
            }
            return new Instance.ValueInstance<>(Maybe.none());
        }
    };

    /** ident(ValueType): ValueType. The argument, as the evaluator gives it. */
    private static final BuildingFunctionExtension IDENT = new BuildingFunctionExtension() {
        @Override
        public FunctionSignature signature() {
            return new FunctionSignature("ident", FunctionType.VALUE, List.of(FunctionType.VALUE));
        }

        @Override
        public <N, M extends JsonModel<N> & JsonFactory<N>> Instance<N> apply(List<Instance<N>> arguments, M model) {
            return arguments.getFirst();
        }
    };

    /** same(NodesType): NodesType. The argument nodelist. */
    private static final FunctionExtension SAME = new FunctionExtension() {
        @Override
        public FunctionSignature signature() {
            return new FunctionSignature("same", FunctionType.NODES, List.of(FunctionType.NODES));
        }

        @Override
        public <N> Instance<N> apply(List<Instance<N>> arguments, JsonModel<N> model) {
            return arguments.getFirst();
        }
    };

    /** first(NodesType): ValueType. The first node, or Nothing. A read-only function with a ValueType result. */
    private static final FunctionExtension FIRST = new FunctionExtension() {
        @Override
        public FunctionSignature signature() {
            return new FunctionSignature("first", FunctionType.VALUE, List.of(FunctionType.NODES));
        }

        @Override
        public <N> Instance<N> apply(List<Instance<N>> arguments, JsonModel<N> model) {
            List<Node<N>> nodes = ((Instance.NodesInstance<N>) arguments.getFirst()).nodes();
            return new Instance.ValueInstance<>(nodes.isEmpty() ? Maybe.none() : Maybe.some(nodes.getFirst().value()));
        }
    };

    /** wrong(): ValueType, but it returns a LogicalType instance. */
    private static final FunctionExtension WRONG = new FunctionExtension() {
        @Override
        public FunctionSignature signature() {
            return new FunctionSignature("wrong", FunctionType.VALUE, List.of());
        }

        @Override
        public <N> Instance<N> apply(List<Instance<N>> arguments, JsonModel<N> model) {
            return new Instance.LogicalInstance<>(true);
        }
    };

    private static final List<FunctionExtension> READ_ONLY = List.of(SAME, FIRST, WRONG);

    private static final BuildingEvaluator EVALUATOR =
            switch (JsonPathEvaluator.withFunctions(READ_ONLY, List.of(UPPER, IDENT))) {
                case Result.Ok<BuildingEvaluator, ExtensionError>(BuildingEvaluator evaluator) -> evaluator;
                case Result.Err<BuildingEvaluator, ExtensionError>(ExtensionError error) -> fail(error.message());
            };

    private static final JsonPathParser PARSER = switch (JsonPathParser.withFunctions(List.of(
            UPPER.signature(), IDENT.signature(), SAME.signature(), FIRST.signature(), WRONG.signature()))) {
                case Result.Ok<JsonPathParser, FunctionRegistrationError>(JsonPathParser parser) -> parser;
                case Result.Err<JsonPathParser, FunctionRegistrationError>(FunctionRegistrationError error) ->
                        fail(error.message());
            };

    private static List<String> extended(String json, String query) {
        return paths(EVALUATOR, PARSER, json, query);
    }

    @Test
    @Requirement("2.4/extension-implementations")
    void extensionsAreApplied() {
        String json = "[\"abc\", \"ABC\", 1, \"x\"]";
        assertEquals(List.of("$[0]", "$[1]"), extended(json, "$[?upper(@) == 'ABC']"));
        assertEquals(List.of("$[2]"), extended(json, "$[?upper(@) == upper(@.missing) && @ == 1]"));
        // A literal argument reaches a building extension as a node that the factory built.
        assertEquals(List.of("$[1]"), extended(json, "$[?@ == upper('abc')]"));
        assertEquals(List.of("$[0]"), extended("[[3, 4], []]", "$[?first(@.*) == 3]"));
        assertEquals(List.of("$[1]"), extended("[[3, 4], []]", "$[?first(@.*) == @.x]"));
        assertEquals(
                Result.err(new ExtensionError.DuplicateName("count")),
                JsonPathEvaluator.withFunctions(List.of(new FunctionExtension() {
                    @Override
                    public FunctionSignature signature() {
                        return FunctionSignature.COUNT;
                    }

                    @Override
                    public <N> Instance<N> apply(List<Instance<N>> arguments, JsonModel<N> model) {
                        return arguments.getFirst();
                    }
                })));
        assertEquals(
                Result.err(new ExtensionError.DuplicateName("upper")),
                JsonPathEvaluator.withFunctions(List.of(), List.of(UPPER, UPPER)));
        assertEquals(
                Result.err(new ExtensionError.DuplicateName("same")),
                JsonPathEvaluator.withFunctions(List.of(SAME, SAME)));
        assertEquals(
                new EvaluationError.ExtensionResultMismatch("wrong", FunctionType.VALUE),
                Eval.error(EVALUATOR.evaluate(Eval.query(PARSER, "$[?wrong() == 1]"), Eval.jackson("[1]"), Eval.JACKSON)));
        assertEquals(10, EVALUATOR.functions().size());
    }

    @Test
    @Requirement("2.4/extension-implementations")
    void buildingExtensionsReceiveLiteralsAsBuiltNodes() {
        // Each literal reaches upper() as a node that the factory built. A node that is not a string gives Nothing.
        String json = "[1]";
        for (String literal : List.of("1", "1.5", "true", "false", "null")) {
            assertEquals(List.of("$[0]"), extended(json, "$[?upper(" + literal + ") == @.missing]"), literal);
        }
        assertEquals(List.of("$[0]"), extended(json, "$[?upper('a') == 'A']"));
        // A number literal reaches an extension as a built number node with its value.
        assertEquals(List.of("$[0]"), extended(json, "$[?ident(1.5) == 1.5]"));
        assertEquals(List.of("$[0]"), extended(json, "$[?ident(length('abc')) == 3]"));
        assertEquals(List.of("$[0]"), extended(json, "$[?upper(length('abc')) == @.missing]"));
        BuildingEvaluator registered = JsonPathEvaluator.withFunctions(READ_ONLY, List.of(UPPER)).orElse(EVALUATOR.withLimits(JsonPathEvaluator.Limits.DEFAULT));
        assertEquals(List.of("$[0]"), Eval.paths(Eval.result(registered.evaluate(Eval.query(PARSER, "$[?upper(@) == 'A']"),
                Eval.jackson("[\"a\"]"), Eval.JACKSON), "upper")));
        BuildingEvaluator limited = EVALUATOR.withLimits(new JsonPathEvaluator.Limits(1, 100));
        assertEquals(new JsonPathEvaluator.Limits(1, 100), limited.limits());
        assertEquals(new EvaluationError.NodelistTooLarge(1),
                Eval.error(limited.evaluate(Eval.query(PARSER, "$[*]"), Eval.jackson("[1, 2]"), Eval.JACKSON)));
    }

    @Test
    @Requirement("2.4/extension-implementations")
    void readOnlyExtensionsHaveNoValueParameters() {
        FunctionExtension valueParameter = new FunctionExtension() {
            @Override
            public FunctionSignature signature() {
                return new FunctionSignature("lower", FunctionType.VALUE, List.of(FunctionType.NODES, FunctionType.VALUE));
            }

            @Override
            public <N> Instance<N> apply(List<Instance<N>> arguments, JsonModel<N> model) {
                return arguments.getFirst();
            }
        };
        ExtensionError error = new ExtensionError.ValueParameter("lower");
        assertEquals(Result.err(error), JsonPathEvaluator.withFunctions(List.of(valueParameter)));
        assertEquals(Result.err(error), JsonPathEvaluator.withFunctions(List.of(valueParameter), List.of(UPPER)));
        assertEquals("The read-only function 'lower' has a ValueType parameter. Use a BuildingFunctionExtension.",
                error.message());
        assertEquals(Result.err(new ExtensionError.InvalidName("Upper")), JsonPathEvaluator.withFunctions(List.of(),
                List.of(new BuildingFunctionExtension() {
                    @Override
                    public FunctionSignature signature() {
                        return new FunctionSignature("Upper", FunctionType.VALUE, List.of());
                    }

                    @Override
                    public <N, M extends JsonModel<N> & JsonFactory<N>> Instance<N> apply(
                            List<Instance<N>> arguments, M model) {
                        return new Instance.ValueInstance<>(Maybe.none());
                    }
                })));
    }

    @Test
    @Requirement("2.4/side-effect-free")
    void sameQueryAndValueGiveTheSameResult() {
        String json = "{\"a\": [\"x\", \"yy\", {\"b\": 1}], \"r\": \"y+\"}";
        String query = "$..[?length(@) > 1 || count(@.*) == 1 || match(@, $.r) || search(@, 'x') || value(@.b) == 1]";
        List<String> first = paths(json, query);
        for (int i = 0; i < 10; i++) {
            assertEquals(first, paths(json, query));
        }
    }

    @Test
    @Requirement("2.4.1/nothing")
    void nothingIsNotNull() {
        String json = "[{\"a\": null}, {}]";
        assertEquals(List.of("$[0]"), paths(json, "$[?@.a == null]"));
        assertEquals(List.of("$[1]"), paths(json, "$[?value(@.a) != null && value(@.a) == @.b]"));
        assertEquals(List.of(), paths(json, "$[?length(@.a) == null]"));
    }

    @Test
    @Requirement("2.4.2/nodes-to-logical")
    void nodesResultAsTest() {
        String json = "[[1], [], {\"a\": 1}, {}, 3]";
        assertEquals(List.of("$[0]", "$[2]"), extended(json, "$[?same(@.*)]"));
        assertEquals(List.of("$[1]", "$[3]", "$[4]"), extended(json, "$[?!same(@.*)]"));
    }

    @Test
    @Requirement("2.4.4/string")
    void lengthOfAString() {
        assertEquals(List.of("$[0]"), paths("[\"abc\", \"ab\"]", "$[?length(@) == 3]"));
        // One scalar value outside the BMP is two UTF-16 code units.
        assertEquals(List.of("$[0]"), paths("[\"😀\", \"ab\"]", "$[?length(@) == 1]"));
        assertEquals(List.of("$[0]"), paths("[\"\"]", "$[?length(@) == 0]"));
    }

    @Test
    @Requirement("2.4.4/array")
    void lengthOfAnArray() {
        assertEquals(List.of("$[1]"), paths("[[1], [1, 2, 3], []]", "$[?length(@) == 3]"));
    }

    @Test
    @Requirement("2.4.4/object")
    void lengthOfAnObject() {
        assertEquals(List.of("$[0]"), paths("[{\"a\": 1, \"b\": 2}, {\"a\": 1}]", "$[?length(@) == 2]"));
    }

    @Test
    @Requirement("2.4.4/other")
    void lengthOfOtherValues() {
        String json = "[1, true, false, null, {\"a\": 1}]";
        assertEquals(List.of("$[0]", "$[1]", "$[2]", "$[3]"), paths(json, "$[?length(@) == @.missing]"));
        assertEquals(List.of("$[4]"), paths(json, "$[?@.a && length(@.a) == length(@.b)]"));
    }

    @Test
    @Requirement("2.4.5/count")
    void countCountsNodesWithDuplicates() {
        String json = "[[1, 2, 3], [], {\"a\": [1, 2]}]";
        assertEquals(List.of("$[0]"), paths(json, "$[?count(@.*) == 3]"));
        assertEquals(List.of("$[1]"), paths(json, "$[?count(@.*) == 0]"));
        assertEquals(List.of("$[0]", "$[2]"), paths(json, "$[?count(@..*) == 3]"));
        assertEquals(List.of("$[0]"), paths(json, "$[?count(@[0, 0, 0, 1]) == 4]"));
        assertEquals(List.of("$[0]", "$[1]", "$[2]"), paths(json, "$[?count(@) == 1]"));
    }

    @Test
    @Requirement("2.4.6/full-match")
    void matchNeedsTheCompleteString() {
        String json = "[\"1974-05-01\", \"1974-05-011\", \"x1974-05-01\"]";
        assertEquals(List.of("$[0]"), paths(json, "$[?match(@, '1974-05-..')]"));
        assertEquals(List.of("$[0]", "$[1]", "$[2]"), paths(json, "$[?match(@, '.*1974.*')]"));
    }

    @Test
    @Requirement("2.4.6/non-string")
    void matchOfOtherValuesIsFalse() {
        String json = "[1, true, null, [\"a\"], {\"a\": \"a\"}, \"a\"]";
        assertEquals(List.of("$[5]"), paths(json, "$[?match(@, 'a')]"));
        assertEquals(List.of(), paths(json, "$[?match(@, 1)]"));
        assertEquals(List.of(), paths(json, "$[?match(@.missing, 'a')]"));
        assertEquals(List.of(), paths(json, "$[?match('a', @.missing)]"));
    }

    @Test
    @Requirement("2.4.6/invalid-iregexp")
    void matchWithAnInvalidRegexpIsFalse() {
        String json = "[\"a\", \"(\"]";
        for (String regexp : List.of("(", "[a", "a{2,1}", "\\\\d", "(?i)a", "a**", "[^]", "\\\\p{Xx}")) {
            assertEquals(List.of(), paths(json, "$[?match(@, '" + regexp + "')]"), regexp);
            assertEquals(List.of("$[0]", "$[1]"), paths(json, "$[?!match(@, '" + regexp + "')]"), regexp);
        }
    }

    @Test
    @Requirement("2.4.6/iregexp-semantics")
    void matchUsesIRegexpSemantics() {
        String json = "[\"a\\nb\", \"a\\rb\", \"axb\", \"a\\u2028b\", \"a\\u0085b\"]";
        // "." matches all characters except \n and \r, unlike java.util.regex.
        assertEquals(List.of("$[2]", "$[3]", "$[4]"), paths(json, "$[?match(@, 'a.b')]"));
    }

    @Test
    @Requirement("2.4.7/substring")
    void searchFindsASubstring() {
        String json = "[\"Bob\", \"Rob Roy\", \"bob\", \"\"]";
        assertEquals(List.of("$[0]", "$[1]"), paths(json, "$[?search(@, '[BR]ob')]"));
        assertEquals(List.of("$[0]", "$[1]", "$[2]", "$[3]"), paths(json, "$[?search(@, '')]"));
        assertEquals(List.of("$[0]", "$[1]", "$[2]", "$[3]"), paths(json, "$[?search(@, 'x*')]"));
    }

    @Test
    @Requirement("4.1/regex-resources")
    void patternsFromTheDocumentCanChangeForEachNode() {
        // The evaluation keeps only the last pattern of each call: each change compiles
        // the new pattern.
        String json = "[{\"s\": \"a\", \"re\": \"a\"}, {\"s\": \"b\", \"re\": \"b\"}, {\"s\": \"a\", \"re\": \"b\"},"
                + " {\"s\": \"b\", \"re\": \"b\"}, {\"s\": \"a\", \"re\": \"a\"}, {\"s\": \"(\", \"re\": \"(\"}]";
        assertEquals(List.of("$[0]", "$[1]", "$[3]", "$[4]"), paths(json, "$[?match(@.s, @.re)]"));
        assertEquals(List.of("$[0]", "$[4]"), paths(json, "$[?match(@.s, @.re) && match(@.s, 'a')]"));
        assertEquals(List.of("$[1]", "$[3]"), paths(json, "$[?search(@.s, @.re) && search(@.re, 'b')]"));
    }

    @Test
    @Requirement("2.4.7/non-string")
    void searchOfOtherValuesIsFalse() {
        String json = "[1, [\"a\"], \"a\"]";
        assertEquals(List.of("$[2]"), paths(json, "$[?search(@, 'a')]"));
        assertEquals(List.of(), paths(json, "$[?search(@, true)]"));
    }

    @Test
    @Requirement("2.4.7/invalid-iregexp")
    void searchWithAnInvalidRegexpIsFalse() {
        String json = "[\"a(\"]";
        for (String regexp : List.of("(", "a)", "\\\\w", "a{,2}", "[z-a]")) {
            assertEquals(List.of(), paths(json, "$[?search(@, '" + regexp + "')]"), regexp);
        }
    }

    @Test
    @Requirement("2.4.8/single-node")
    void valueOfOneNode() {
        String json = "[{\"a\": {\"color\": \"red\"}}, {\"b\": [{\"color\": \"red\"}]}, {\"color\": \"blue\"}]";
        assertEquals(List.of("$[0]", "$[1]"), paths(json, "$[?value(@..color) == 'red']"));
        assertEquals(List.of("{\"a\":{\"color\":\"red\"}}"), jsonValues(json, "$[?value(@.*) == $[0].a]"));
    }

    @Test
    @Requirement("2.4.8/not-single")
    void valueOfOtherNodelistsIsNothing() {
        String json = "[{\"a\": 1, \"b\": 1}, {}, {\"a\": 1}]";
        assertEquals(List.of("$[0]", "$[1]"), paths(json, "$[?value(@.*) == @.missing]"));
        assertEquals(List.of("$[2]"), paths(json, "$[?value(@.*) == 1]"));
    }
}
