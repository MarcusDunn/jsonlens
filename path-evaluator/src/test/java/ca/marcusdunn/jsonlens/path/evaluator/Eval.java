package ca.marcusdunn.jsonlens.path.evaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.kotlinx.KotlinxJsonModel;
import ca.marcusdunn.jsonlens.jackson.JacksonStream;
import ca.marcusdunn.jsonlens.jackson.JacksonStreamNode;
import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.mapped.MappedNode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import ca.marcusdunn.jsonlens.path.parser.ParseError;
import java.util.List;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElement;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Helpers for evaluator tests. Each query runs on a Jackson tree and on a kotlinx tree, through a
 * {@link CheckingModel}. The two runs must give the same Normalized Paths.
 */
final class Eval {

    static final JsonMapper MAPPER =
            JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    static final JsonPathEvaluator STANDARD = JsonPathEvaluator.standard();
    static final CheckingModel<JsonNode> JACKSON = new CheckingModel<>(JacksonJsonModel.INSTANCE);
    static final CheckingModel<JsonElement> KOTLINX = new CheckingModel<>(KotlinxJsonModel.INSTANCE);
    /** The read-only model over UTF-8 bytes. One model serves all documents. */
    static final CheckingModel<MappedNode> MAPPED = CheckingModel.readOnly(mapped("null").model());
    /** The read-only model over a Jackson streaming parser. One model serves all documents. */
    static final CheckingModel<JacksonStreamNode> STREAM = CheckingModel.readOnly(stream("null").model());

    private Eval() {}

    static JsonPathQuery query(JsonPathParser parser, String query) {
        return switch (parser.parse(query)) {
            case Result.Ok<JsonPathQuery, ParseError>(JsonPathQuery value) -> value;
            case Result.Err<JsonPathQuery, ParseError>(ParseError error) -> fail(query + ": " + error.message());
        };
    }

    static JsonPathQuery query(String query) {
        return query(JsonPathParser.standard(), query);
    }

    static JsonNode jackson(String json) {
        return MAPPER.readTree(json);
    }

    static JsonElement kotlinx(String json) {
        return Json.Default.parseToJsonElement(json);
    }

    static JacksonStream stream(String json) {
        return switch (JacksonStream.open(MAPPER.createParser(json))) {
            case Result.Ok<JacksonStream, ?>(JacksonStream value) -> value;
            case Result.Err<JacksonStream, ?>(Object error) -> fail(json + ": " + error);
        };
    }

    static MappedJson mapped(String json) {
        return switch (MappedJson.of(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)))) {
            case Result.Ok<MappedJson, ?>(MappedJson value) -> value;
            case Result.Err<MappedJson, ?>(Object error) -> fail(json + ": " + error);
        };
    }

    static <N> List<Node<N>> nodes(JsonPathEvaluator evaluator, JsonPathQuery query, N root, CheckingModel<N> model) {
        return switch (evaluator.evaluate(query, root, model)) {
            case Result.Ok<List<Node<N>>, EvaluationError>(List<Node<N>> nodes) -> nodes;
            case Result.Err<List<Node<N>>, EvaluationError>(EvaluationError error) ->
                    fail(query + ": " + error.message());
        };
    }

    /** The Normalized Paths of the result, the same for both models. */
    static List<String> paths(JsonPathEvaluator evaluator, JsonPathParser parser, String json, String query) {
        JsonPathQuery parsed = query(parser, query);
        List<String> jackson = paths(nodes(evaluator, parsed, jackson(json), JACKSON));
        List<String> kotlinx = paths(nodes(evaluator, parsed, kotlinx(json), KOTLINX));
        assertEquals(jackson, kotlinx, "Jackson and kotlinx give different results for " + query);
        return jackson;
    }

    /** The Normalized Paths of the result from a building evaluator, the same for both models. */
    static List<String> paths(BuildingEvaluator evaluator, JsonPathParser parser, String json, String query) {
        JsonPathQuery parsed = query(parser, query);
        List<String> jackson = paths(result(evaluator.evaluate(parsed, jackson(json), JACKSON), query));
        List<String> kotlinx = paths(result(evaluator.evaluate(parsed, kotlinx(json), KOTLINX), query));
        assertEquals(jackson, kotlinx, "Jackson and kotlinx give different results for " + query);
        return jackson;
    }

    static <N> List<Node<N>> result(Result<List<Node<N>>, EvaluationError> result, Object query) {
        return switch (result) {
            case Result.Ok<List<Node<N>>, EvaluationError>(List<Node<N>> nodes) -> nodes;
            case Result.Err<List<Node<N>>, EvaluationError>(EvaluationError error) -> fail(query + ": " + error.message());
        };
    }

    static <N> EvaluationError error(Result<List<Node<N>>, EvaluationError> result) {
        return switch (result) {
            case Result.Ok<List<Node<N>>, EvaluationError>(List<Node<N>> nodes) ->
                    fail("expected an error, but got " + paths(nodes));
            case Result.Err<List<Node<N>>, EvaluationError>(EvaluationError error) -> error;
        };
    }

    static List<String> paths(String json, String query) {
        return paths(STANDARD, JsonPathParser.standard(), json, query);
    }

    /** The values of the result as compact JSON text, from the Jackson tree. */
    static List<String> jsonValues(String json, String query) {
        paths(json, query);
        return nodes(STANDARD, query(query), jackson(json), JACKSON).stream()
                .map(node -> node.value().toString())
                .toList();
    }

    /** True if a filter expression is true for the root object's children (the expression ignores @). */
    static boolean holds(String json, String expression) {
        List<String> paths = paths(json, "$[?" + expression + "]");
        return !paths.isEmpty();
    }

    static <N> List<String> paths(List<Node<N>> nodes) {
        return nodes.stream().map(node -> node.path().toString()).toList();
    }

    static <N> EvaluationError error(JsonPathEvaluator evaluator, JsonPathQuery query, N root, CheckingModel<N> model) {
        return switch (evaluator.evaluate(query, root, model)) {
            case Result.Ok<List<Node<N>>, EvaluationError>(List<Node<N>> nodes) ->
                    fail("expected an error, but got " + paths(nodes));
            case Result.Err<List<Node<N>>, EvaluationError>(EvaluationError error) -> error;
        };
    }
}
