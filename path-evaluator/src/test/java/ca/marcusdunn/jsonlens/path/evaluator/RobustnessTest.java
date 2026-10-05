package ca.marcusdunn.jsonlens.path.evaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.query.FilterQuery;
import ca.marcusdunn.jsonlens.path.core.query.FunctionArgument;
import ca.marcusdunn.jsonlens.path.core.query.FunctionCall;
import ca.marcusdunn.jsonlens.path.core.query.Identifier;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.core.query.Literal;
import ca.marcusdunn.jsonlens.path.core.query.LogicalExpression;
import ca.marcusdunn.jsonlens.path.core.query.Segment;
import ca.marcusdunn.jsonlens.path.core.query.Selector;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import ca.marcusdunn.jsonlens.path.parser.ParseError;
import ca.marcusdunn.jsonlens.testsupport.ModuleDescriptors;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.lang.module.ModuleDescriptor;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Sections 2.1 and 4.1, and the exception-free promise. */
class RobustnessTest {

    private static final ModuleDescriptor MODULE = ModuleDescriptors.named("ca.marcusdunn.jsonlens.path.evaluator");

    private static JsonPathQuery filter(LogicalExpression expression) {
        return new JsonPathQuery(List.of(new Segment.Child(List.of(new Selector.Filter(expression)))));
    }

    private static EvaluationError error(JsonPathEvaluator evaluator, JsonPathQuery query, String json) {
        return Eval.error(evaluator, query, Eval.jackson(json), Eval.JACKSON);
    }

    @Test
    @Requirement("lib/evaluator-dependencies")
    void requiresOnlyTheCoreModuleAtRuntime() {
        assertEquals(Set.of("ca.marcusdunn.jsonlens.path.core"), ModuleDescriptors.runtimeModules(MODULE));
        assertEquals(Set.of("org.jspecify"), ModuleDescriptors.compileOnlyModules(MODULE));
    }

    @Test
    @Requirement("lib/evaluator-null-marked")
    void allExportedPackagesAreNullMarked() {
        assertEquals(Set.of(), ModuleDescriptors.exportedPackagesWithoutNullMarked(MODULE));
        assertEquals(
                Set.of(
                        "ca.marcusdunn.jsonlens.path.evaluator.BuildingEvaluator",
                        "ca.marcusdunn.jsonlens.path.evaluator.BuildingFunctionExtension",
                        "ca.marcusdunn.jsonlens.path.evaluator.EvaluationError",
                        "ca.marcusdunn.jsonlens.path.evaluator.EvaluationError$ExtensionResultMismatch",
                        "ca.marcusdunn.jsonlens.path.evaluator.EvaluationError$IntegerOutOfRange",
                        "ca.marcusdunn.jsonlens.path.evaluator.EvaluationError$NodelistTooLarge",
                        "ca.marcusdunn.jsonlens.path.evaluator.EvaluationError$QueryTooDeep",
                        "ca.marcusdunn.jsonlens.path.evaluator.EvaluationError$RegexTooComplex",
                        "ca.marcusdunn.jsonlens.path.evaluator.EvaluationError$SignatureMismatch",
                        "ca.marcusdunn.jsonlens.path.evaluator.EvaluationError$UnknownFunction",
                        "ca.marcusdunn.jsonlens.path.evaluator.ExtensionError",
                        "ca.marcusdunn.jsonlens.path.evaluator.ExtensionError$DuplicateName",
                        "ca.marcusdunn.jsonlens.path.evaluator.ExtensionError$InvalidName",
                        "ca.marcusdunn.jsonlens.path.evaluator.ExtensionError$ValueParameter",
                        "ca.marcusdunn.jsonlens.path.evaluator.FunctionExtension",
                        "ca.marcusdunn.jsonlens.path.evaluator.Instance",
                        "ca.marcusdunn.jsonlens.path.evaluator.Instance$LogicalInstance",
                        "ca.marcusdunn.jsonlens.path.evaluator.Instance$NodesInstance",
                        "ca.marcusdunn.jsonlens.path.evaluator.Instance$ValueInstance",
                        "ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator",
                        "ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator$Limits",
                        "ca.marcusdunn.jsonlens.path.evaluator.Node"),
                ModuleDescriptors.publicExportedTypes(JsonPathEvaluator.class));
    }

    @Test
    @Requirement("2.1/no-validity-errors-at-evaluation")
    void parsedQueriesNeverGiveValidityErrors() {
        // Validity errors occur only for queries that a caller made directly.
        Eval.paths("[1, [2]]", "$[?length(@) == 1 && count(@.*) >= 0 && value(@[0]) != 3][-9007199254740991:]");
        LogicalExpression unknown = new FunctionCall.Logical("nope", List.of());
        assertEquals(new EvaluationError.UnknownFunction("nope"), error(Eval.STANDARD, filter(unknown), "[1]"));
        LogicalExpression wrongType = new FunctionCall.Logical("length", List.of(
                new FunctionArgument.Value(new Literal.NullLiteral())));
        assertEquals(new EvaluationError.SignatureMismatch("length"), error(Eval.STANDARD, filter(wrongType), "[1]"));
        LogicalExpression wrongArgument = new LogicalExpression.Comparison(
                new FunctionCall.Value("length", List.of(new FunctionArgument.Nodes(new FilterQuery(Identifier.CURRENT, List.of())))),
                ca.marcusdunn.jsonlens.path.core.query.ComparisonOperator.EQUAL,
                new Literal.NullLiteral());
        assertEquals(new EvaluationError.SignatureMismatch("length"), error(Eval.STANDARD, filter(wrongArgument), "[1]"));
        JsonPathQuery range = new JsonPathQuery(List.of(new Segment.Child(List.of(
                new Selector.Slice(Maybe.none(), Maybe.none(), Maybe.some(Long.MIN_VALUE))))));
        assertEquals(new EvaluationError.IntegerOutOfRange(Long.MIN_VALUE), error(Eval.STANDARD, range, "[1]"));
        JsonPathQuery index = new JsonPathQuery(List.of(new Segment.Child(List.of(new Selector.Index(1L << 53)))));
        assertEquals(new EvaluationError.IntegerOutOfRange(1L << 53), error(Eval.STANDARD, index, "[1]"));
    }

    @Test
    @Requirement("2.1/overflow-indication")
    void overflowIsIndicated() {
        JsonPathEvaluator small = Eval.STANDARD.withLimits(new JsonPathEvaluator.Limits(5, 50));
        assertEquals(new EvaluationError.NodelistTooLarge(5), error(small, Eval.query("$[*]"), "[1, 2, 3, 4, 5, 6]"));
        assertEquals(new EvaluationError.NodelistTooLarge(5), error(small, Eval.query("$..*"), "[[1, 2], [3, 4]]"));
        assertEquals(new EvaluationError.NodelistTooLarge(5), error(small, Eval.query("$[?count(@.*) > 1]"), "[[1, 2, 3, 4, 5, 6]]"));
        assertInstanceOf(EvaluationError.RegexTooComplex.class,
                error(small, Eval.query("$[?match(@, 'a{100}')]"), "[\"a\"]"));
        assertInstanceOf(EvaluationError.RegexTooComplex.class,
                error(Eval.STANDARD, Eval.query("$[?search(@, $.r)]"), "{\"r\": \"(a{1,1000}){1,1000}\", \"s\": \"a\"}"));
        assertEquals(List.of("$[0]", "$[1]", "$[2]", "$[3]", "$[4]"),
                Eval.paths(Eval.nodes(small, Eval.query("$[*]"), Eval.jackson("[1, 2, 3, 4, 5]"), Eval.JACKSON)));
    }

    @Test
    @Requirement("4.1/evaluator-resources")
    void deepValuesDoNotOverflowTheStack() {
        int depth = 100_000;
        JsonNode deepest = tools.jackson.databind.node.JsonNodeFactory.instance.numberNode(1);
        for (int i = 0; i < depth; i++) {
            deepest = tools.jackson.databind.node.JsonNodeFactory.instance.arrayNode().add(deepest);
        }
        JsonNode jackson = deepest;
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            assertEquals(depth, Eval.nodes(Eval.STANDARD, Eval.query("$..*"), jackson, Eval.JACKSON).size());
            assertEquals(1, Eval.nodes(Eval.STANDARD, Eval.query("$[?@ == $[0]]"), jackson, Eval.JACKSON).size());
            assertEquals(1, Eval.nodes(Eval.STANDARD, Eval.query("$..[?@ == 1]"), jackson, Eval.JACKSON).size());
        });
        LogicalExpression nested = new FilterQuery(Identifier.CURRENT, List.of());
        for (int i = 0; i < 100_000; i++) {
            nested = new LogicalExpression.Not(nested);
        }
        assertEquals(new EvaluationError.QueryTooDeep(JsonPathEvaluator.MAX_QUERY_DEPTH),
                error(Eval.STANDARD, filter(nested), "[1]"));
        // 250 negations cancel out, so the element is selected.
        String deepQuery = "$[?" + "!(".repeat(250) + "@" + ")".repeat(250) + "]";
        assertEquals(List.of("$[0]"), Eval.paths("[1]", deepQuery));
    }

    @Test
    @Requirement("lib/evaluator-exception-free")
    void randomQueriesOnRandomValuesNeverThrow() {
        Random random = new Random(9535);
        List<JsonPathQuery> queries = new ArrayList<>();
        String alphabet = "$@.[]()'?*:,!=<>&|-0123abclnu ";
        while (queries.size() < 3_000) {
            StringBuilder text = new StringBuilder("$");
            int length = 1 + random.nextInt(20);
            for (int i = 0; i < length; i++) {
                text.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            if (JsonPathParser.standard().parse(text.toString())
                    instanceof Result.Ok<JsonPathQuery, ParseError>(JsonPathQuery query)) {
                queries.add(query);
            }
        }
        queries.addAll(List.of(
                Eval.query("$..[?length(@) > count(@.*) || match(@, '[a-c]+') && search(@.a, @.b)]"),
                Eval.query("$..*[?value(@..*) == @ || @[-1] != $[0] && @[-1:0:-1]]"),
                Eval.query("$[?@.a < @.b && 'a' <= @ && true > null]")));
        List<String> documents = List.of(
                "null", "1", "\"abc\"", "[]", "{}", "[1, \"a\", null, true, [2, [3]], {\"a\": \"b\", \"b\": \"a.*\"}]",
                "{\"a\": {\"a\": {\"a\": [1, 2.5e300, -0, \"\\u00e9\", {\"b\": \"(\"}]}}, \"b\": [[], [[]], {}]}");
        for (String document : documents) {
            JsonNode root = Eval.jackson(document);
            for (JsonPathQuery query : queries) {
                Eval.STANDARD.evaluate(query, root, Eval.JACKSON);
            }
        }
    }

    @Test
    @Requirement("2.1.2/result-representation")
    void numberLiteralsKeepTheirValue() {
        assertEquals(List.of("$[0]"), Eval.paths("[1.50, 2]", "$[?@ == 1.5]"));
        assertEquals(0, new BigDecimal("1.5").compareTo(new BigDecimal("1.50")));
    }
}
