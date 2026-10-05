package ca.marcusdunn.jsonlens.path.evaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.function.FunctionSignature;
import ca.marcusdunn.jsonlens.path.core.function.FunctionType;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.parser.FunctionRegistrationError;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import kotlinx.serialization.json.JsonElement;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Adversarial conformance findings for the evaluator and the adapter models. Each test asserts the
 * behavior that RFC 9535 or the library documentation requires. These tests fail against the
 * current code.
 */
class AdversarialConformanceTest {

    /** blt(LogicalType): LogicalType, as in RFC 9535, Table 14. It gives its argument. */
    private static final FunctionExtension BLT = new FunctionExtension() {
        @Override
        public FunctionSignature signature() {
            return new FunctionSignature("blt", FunctionType.LOGICAL, List.of(FunctionType.LOGICAL));
        }

        @Override
        public <N> Instance<N> apply(List<Instance<N>> arguments, JsonModel<N> model) {
            return arguments.getFirst();
        }
    };

    /**
     * RFC 9535, Section 2.1: "No further errors relating to the well-formedness and the validity of
     * a JSONPath query can be raised during application of the query to a value."
     *
     * <p>The library also promises this. {@link JsonPathEvaluator}: "A query from the parser is
     * valid, so for such a query only the overflow indications of [Limits] can occur." And
     * {@link JsonPathEvaluator#MAX_QUERY_DEPTH}: "A query from the parser is always below this
     * limit." The table in {@link EvaluationError} says that QueryTooDeep does not occur for a
     * parsed query.
     *
     * <p>Cause: the parser counts one level for each function call (MAX_NESTING_DEPTH = 256). The
     * QueryValidator counts five levels for each repetition of {@code @.x || @ && !blt(...)}: Or,
     * And, Not, the logical node of the call, and the call itself. Thus 205 repetitions (206 parser
     * levels) give 1025 validator levels, which is more than MAX_QUERY_DEPTH = 1024.
     *
     * <p>Query: {@code $[?@.x || @ && !blt(@.x || @ && !blt(... @ ...))]} with 205 calls, on
     * {@code [1]}. Expected: {@code ["$[0]"]} (the parser accepts the query). Actual:
     * {@code Err(QueryTooDeep[limit=1024])}.
     */
    @Disabled("ISSUE-3: the evaluator can reject a parsed query as too deep")
    @Test
    @Requirement("2.1/no-validity-errors-at-evaluation")
    void aParsedQueryWithLogicalExtensionsDoesNotGiveQueryTooDeep() {
        JsonPathParser parser = switch (JsonPathParser.withFunctions(List.of(BLT.signature()))) {
            case Result.Ok<JsonPathParser, FunctionRegistrationError>(JsonPathParser value) -> value;
            case Result.Err<JsonPathParser, FunctionRegistrationError>(FunctionRegistrationError e) ->
                    fail(e.toString());
        };
        JsonPathEvaluator evaluator = switch (JsonPathEvaluator.withFunctions(List.of(BLT))) {
            case Result.Ok<JsonPathEvaluator, ExtensionError>(JsonPathEvaluator value) -> value;
            case Result.Err<JsonPathEvaluator, ExtensionError>(ExtensionError e) -> fail(e.toString());
        };
        String expression = "@";
        for (int i = 0; i < 205; i++) {
            expression = "@.x || @ && !blt(" + expression + ")";
        }
        // Eval.query fails the test if the parser rejects the query. It accepts it.
        JsonPathQuery query = Eval.query(parser, "$[?" + expression + "]");
        assertEquals(List.of("$[0]"), Eval.paths(Eval.nodes(evaluator, query, Eval.jackson("[1]"), Eval.JACKSON)));
    }

    /**
     * RFC 8259, Section 6: {@code exp = e [ minus / plus ] 1*DIGIT}. The JSON text
     * {@code [1e99999999999]} is valid JSON, and its element is a number, not a string.
     *
     * <p>RFC 9535, Section 2.1: "the implementation MUST NOT silently malfunction. Specifically, if a
     * valid JSONPath query is evaluated against a structured value whose size is too large to
     * process the query correctly (for instance, requiring the processing of numbers that fall
     * outside the range of exact values), the implementation MUST provide an indication of
     * overflow."
     *
     * <p>RFC 9535, Section 2.3.5.2.2: == is true only between numbers, "equal primitive values that
     * are not numbers", equal arrays, or equal objects. A number is never equal to a string. Section
     * 2.4.4: length() of a number is Nothing. Section 2.4.6: match() is LogicalFalse if "the first
     * argument is not a string".
     *
     * <p>Expected: each query gives an empty nodelist. KotlinxJsonModel gives the kind NUMBER for each
     * JSON number, and the exact value of a number has no limit on the exponent.
     */
    @Test
    @Requirement("2.1/overflow-indication")
    void kotlinxDoesNotSilentlyTreatALargeJsonNumberAsAString() {
        JsonElement root = Eval.kotlinx("[1e99999999999]");
        for (String query : List.of(
                "$[?@ == '1e99999999999']",
                "$[?length(@) == 13]",
                "$[?match(@, '1e9+')]")) {
            Result<List<Node<JsonElement>>, EvaluationError> result =
                    Eval.STANDARD.evaluate(Eval.query(query), root, Eval.KOTLINX);
            if (result instanceof Result.Ok<List<Node<JsonElement>>, EvaluationError>(List<Node<JsonElement>> nodes)) {
                assertNotEquals(List.of("$[0]"), Eval.paths(nodes), query + ": a JSON number is not a string");
            }
        }
    }
}
