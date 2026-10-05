package ca.marcusdunn.jsonlens.fuzz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.evaluator.EvaluationError;
import ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator;
import ca.marcusdunn.jsonlens.path.evaluator.Node;
import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.Property;
import ca.marcusdunn.jsonlens.kotlinx.KotlinxJsonModel;
import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.mapped.MappedJsonError;
import ca.marcusdunn.jsonlens.mapped.MappedNode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import ca.marcusdunn.jsonlens.path.parser.ParseError;
import com.code_intelligence.jazzer.junit.DictionaryEntries;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.util.List;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElement;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evaluates a query on a JSON value. The data is the query, a NUL byte, and the JSON text.
 *
 * <p>The properties:
 *
 * <ul>
 *   <li>The evaluator does not throw, and it keeps its promises to the model.
 *   <li>Jackson, kotlinx, and memory-mapped trees give the same result (except with duplicate names).
 *   <li>Each Normalized Path in the result selects exactly that node (RFC 9535, Section 2.7).
 *   <li>The text of the query, from toString(), gives the same result.
 * </ul>
 */
class EvaluatorFuzzTest {

    private static final JsonPathParser PARSER = JsonPathParser.standard();
    private static final JsonPathEvaluator EVALUATOR =
            JsonPathEvaluator.standard().withLimits(new JsonPathEvaluator.Limits(10_000, 1_000));
    private static final JsonMapper MAPPER =
            JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private static final StrictModel<JsonNode> JACKSON = new StrictModel<>(JacksonJsonModel.INSTANCE);
    private static final StrictModel<JsonElement> KOTLINX = new StrictModel<>(KotlinxJsonModel.INSTANCE);
    private static final StrictModel<MappedNode> MAPPED = new StrictModel<>(mapped("null").model());

    @FuzzTest(maxDuration = "5m")
    @DictionaryEntries({
        "$", "@", "..", "[?", "]", "*", ",", ":", "'", "==", "!=", "<", "<=", "&&", "||", "!", "(", ")",
        "-1", "length(", "count(", "match(", "search(", "value(", "true", "null", "\u0000",
        "{", "}", "[", "\"a\"", "\"b\"", ":", "1.0", "-0", "1e2", "\"\\u00e9\""
    })
    void evaluate(byte[] data) {
        String[] parts = Inputs.split(data, 200, 2_000);
        if (parts == null) {
            return;
        }
        if (!(PARSER.parse(parts[0]) instanceof Result.Ok<JsonPathQuery, ParseError>(JsonPathQuery query))) {
            return;
        }
        JsonNode jackson;
        JsonElement kotlinx;
        try {
            jackson = MAPPER.readTree(parts[1]);
            kotlinx = Json.Default.parseToJsonElement(parts[1]);
        } catch (JacksonException | IllegalArgumentException e) {
            return;
        }
        if (jackson == null || jackson.isMissingNode()) {
            return;
        }

        // The memory-mapped model must accept each JSON text that Jackson and kotlinx accept.
        MappedJson mapped = mapped(parts[1]);
        Result<List<Node<JsonNode>>, EvaluationError> fromJackson = EVALUATOR.evaluate(query, jackson, JACKSON);
        Result<List<Node<JsonElement>>, EvaluationError> fromKotlinx = EVALUATOR.evaluate(query, kotlinx, KOTLINX);
        switch (fromJackson) {
            case Result.Err<List<Node<JsonNode>>, EvaluationError>(EvaluationError error) -> {
                assertEquals(Result.err(error), fromKotlinx.map(nodes -> paths(nodes)), "kotlinx: " + parts[0]);
                return;
            }
            case Result.Ok<List<Node<JsonNode>>, EvaluationError>(List<Node<JsonNode>> nodes) -> {
                assertEquals(Result.ok(paths(nodes)), fromKotlinx.map(EvaluatorFuzzTest::paths),
                        "Jackson and kotlinx differ for " + parts[0] + " on " + parts[1]);
                if (!hasDuplicateNames(mapped.root())) {
                    assertEquals(Result.ok(paths(nodes)), EVALUATOR.evaluate(query, mapped.root(), MAPPED).map(EvaluatorFuzzTest::paths),
                            "Jackson and the mapped model differ for " + parts[0] + " on " + parts[1]);
                }
                checkNormalizedPaths(nodes, jackson);
                assertEquals(fromJackson,
                        EVALUATOR.evaluate(parse(query.toString()), jackson, JACKSON),
                        "toString() of " + parts[0] + " is " + query);
            }
        }
    }

    /** Each Normalized Path, as a query, selects exactly its node (RFC 9535, Section 2.7). */
    private static void checkNormalizedPaths(List<Node<JsonNode>> nodes, JsonNode root) {
        for (Node<JsonNode> node : nodes) {
            String path = node.path().toString();
            List<Node<JsonNode>> selected = switch (EVALUATOR.evaluate(parse(path), root, JACKSON)) {
                case Result.Ok<List<Node<JsonNode>>, EvaluationError>(List<Node<JsonNode>> value) -> value;
                case Result.Err<List<Node<JsonNode>>, EvaluationError>(EvaluationError error) ->
                        fail(path + ": " + error.message());
            };
            assertEquals(1, selected.size(), path);
            assertSame(node.value(), selected.getFirst().value(), path);
            assertEquals(node.path(), selected.getFirst().path(), path);
        }
    }

    private static MappedJson mapped(String json) {
        return switch (MappedJson.of(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)))) {
            case Result.Ok<MappedJson, MappedJsonError>(MappedJson value) -> value;
            case Result.Err<MappedJson, MappedJsonError>(MappedJsonError error) ->
                    fail("the mapped model rejects valid JSON " + json + ": " + error.message());
        };
    }

    /** True if an object has a member name more than one time. Then the models may choose differently. */
    private static boolean hasDuplicateNames(MappedNode root) {
        JsonModel<MappedNode> model = MAPPED;
        Deque<MappedNode> stack = new ArrayDeque<>(List.of(root));
        while (!stack.isEmpty()) {
            MappedNode node = stack.pop();
            switch (model.kind(node)) {
                case OBJECT -> {
                    List<Property<MappedNode>> members = model.members(node).toList();
                    if (members.stream().map(property -> JsonString.copyOf(property.name())).distinct().count() < members.size()) {
                        return true;
                    }
                    members.forEach(member -> stack.push(member.value()));
                }
                case ARRAY -> {
                    for (int i = 0; i < model.arrayLength(node); i++) {
                        if (model.element(node, i) instanceof Maybe.Some<MappedNode>(MappedNode element)) {
                            stack.push(element);
                        }
                    }
                }
                default -> {}
            }
        }
        return false;
    }

    private static JsonPathQuery parse(String text) {
        return switch (PARSER.parse(text)) {
            case Result.Ok<JsonPathQuery, ParseError>(JsonPathQuery query) -> query;
            case Result.Err<JsonPathQuery, ParseError>(ParseError error) -> fail(text + ": " + error.message());
        };
    }

    private static <N> List<String> paths(List<Node<N>> nodes) {
        return nodes.stream().map(node -> node.path().toString()).toList();
    }
}
