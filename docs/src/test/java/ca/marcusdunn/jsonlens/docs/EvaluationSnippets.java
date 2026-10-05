package ca.marcusdunn.jsonlens.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.path.NormalizedPath;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.evaluator.EvaluationError;
import ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator;
import ca.marcusdunn.jsonlens.path.evaluator.Node;
import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Samples for the evaluator module. */
class EvaluationSnippets {

    private static final JsonMapper MAPPER =
            JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    private static JsonPathQuery query(String text) {
        return JsonPathParser.standard().parse(text).orElse(null);
    }

    @Test
    void evaluate() {
        JsonNode root = MAPPER.readTree(QuickStartSnippets.BOOKSTORE);
        // @start region="evaluate"
        JsonPathQuery query = query("$..book[?@.isbn].author");
        List<Node<JsonNode>> nodes = JsonPathEvaluator.standard()
                .evaluate(query, root, JacksonJsonModel.INSTANCE)
                .orElse(List.of());

        Node<JsonNode> first = nodes.getFirst();
        JsonNode value = first.value();          // the Jackson node "Herman Melville" of the tree
        NormalizedPath path = first.path();      // $['store']['book'][2]['author']
        // @end region="evaluate"
        assertSame(root.get("store").get("book").get(2).get("author"), value);
        assertEquals("$['store']['book'][2]['author']", path.toString());
        assertEquals(2, nodes.size());
    }

    @Test
    void filters() {
        JsonNode root = MAPPER.readTree(QuickStartSnippets.BOOKSTORE);
        JsonPathEvaluator evaluator = JsonPathEvaluator.standard();
        // @start region="filters"
        // Comparisons, logical operators, and the standard functions.
        List<String> queries = List.of(
                "$.store.book[?@.price < 10 && @.category == 'fiction'].title", // ["Moby Dick"]
                "$.store.book[?search(@.author, 'Re*s')].author",               // ["Nigel Rees"]
                "$.store.book[?match(@.title, '.*Ring.*')].price",              // [22.99]
                "$.store[?length(@.color) == 3].color",                         // ["red"]
                "$.store.book[?count(@.*) == 5].title",                         // the books with an isbn
                "$.store.book[-1:].title");                                     // ["The Lord of the Rings"]
        for (String text : queries) {
            List<Node<JsonNode>> nodes = evaluator.evaluate(query(text), root, JacksonJsonModel.INSTANCE).orElse(List.of());
            System.out.println(text + " -> " + nodes.stream().map(Node::value).toList());
        }
        // @end region="filters"
        assertEquals("[\"Moby Dick\"]", values(evaluator, root, queries.get(0)));
        assertEquals("[\"Nigel Rees\"]", values(evaluator, root, queries.get(1)));
        assertEquals("[22.99]", values(evaluator, root, queries.get(2)));
        assertEquals("[\"red\"]", values(evaluator, root, queries.get(3)));
        assertEquals("[\"Moby Dick\", \"The Lord of the Rings\"]", values(evaluator, root, queries.get(4)));
        assertEquals("[\"The Lord of the Rings\"]", values(evaluator, root, queries.get(5)));
    }

    private static String values(JsonPathEvaluator evaluator, JsonNode root, String text) {
        return evaluator.evaluate(query(text), root, JacksonJsonModel.INSTANCE).orElse(List.of())
                .stream().map(Node::value).toList().toString();
    }

    @Test
    void limits() {
        JsonNode root = MAPPER.readTree("[1, 2, 3, 4, 5, 6]");
        // @start region="limits"
        JsonPathEvaluator strict = JsonPathEvaluator.standard()
                .withLimits(new JsonPathEvaluator.Limits(5, 1_000)); // at most 5 nodes in a nodelist

        String outcome = switch (strict.evaluate(query("$[*]"), root, JacksonJsonModel.INSTANCE)) {
            case Result.Ok(List<Node<JsonNode>> nodes) -> nodes.size() + " nodes";
            case Result.Err(EvaluationError.NodelistTooLarge(int limit)) -> "more than " + limit + " nodes";
            case Result.Err(EvaluationError.RegexTooComplex(String pattern, int limit)) -> "regexp too large: " + pattern;
            case Result.Err(EvaluationError error) -> error.message();
        };
        // outcome: "more than 5 nodes"
        // @end region="limits"
        assertEquals("more than 5 nodes", outcome);
    }

    @Test
    void handBuilt() {
        // @start region="hand-built-error"
        // A query that is made directly, not by the parser, can be invalid.
        JsonPathQuery bad = new JsonPathQuery(List.of(new ca.marcusdunn.jsonlens.path.core.query.Segment.Child(
                List.of(new ca.marcusdunn.jsonlens.path.core.query.Selector.Index(1L << 60)))));
        Result<List<Node<JsonNode>>, EvaluationError> result =
                JsonPathEvaluator.standard().evaluate(bad, MAPPER.readTree("[]"), JacksonJsonModel.INSTANCE);
        // result: Err(IntegerOutOfRange[value=1152921504606846976])
        // @end region="hand-built-error"
        assertEquals(Result.err(new EvaluationError.IntegerOutOfRange(1L << 60)), result);
    }
}
