package ca.marcusdunn.jsonlens.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.evaluator.EvaluationError;
import ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator;
import ca.marcusdunn.jsonlens.path.evaluator.Node;
import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import ca.marcusdunn.jsonlens.path.parser.ParseError;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The quick start of the API documentation. */
class QuickStartSnippets {

    static final String BOOKSTORE = """
            {"store": {
              "book": [
                {"category": "reference", "author": "Nigel Rees", "title": "Sayings of the Century", "price": 8.95},
                {"category": "fiction", "author": "Evelyn Waugh", "title": "Sword of Honour", "price": 12.99},
                {"category": "fiction", "author": "Herman Melville", "title": "Moby Dick", "isbn": "0-553-21311-3", "price": 8.99},
                {"category": "fiction", "author": "J. R. R. Tolkien", "title": "The Lord of the Rings", "isbn": "0-395-19395-8", "price": 22.99}
              ],
              "bicycle": {"color": "red", "price": 399}
            }}""";

    @Test
    void quickStart() {
        List<String> lines = new ArrayList<>();
        // @start region="quick-start"
        // 1. Read the JSON value with your own JSON library. Keep numbers exact.
        JsonMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .build();
        JsonNode root = mapper.readTree(BOOKSTORE);

        // 2. Parse the query. A query that is not valid gives an error value.
        Result<JsonPathQuery, ParseError> parsed =
                JsonPathParser.standard().parse("$.store.book[?@.price < 10].title"); // @highlight substring="parse"

        // 3. Apply the query to the value through a JsonModel.
        switch (parsed) {
            case Result.Ok(JsonPathQuery query) -> {
                Result<List<Node<JsonNode>>, EvaluationError> result =
                        JsonPathEvaluator.standard().evaluate(query, root, JacksonJsonModel.INSTANCE); // @highlight substring="evaluate"
                switch (result) {
                    case Result.Ok(List<Node<JsonNode>> nodes) -> {
                        for (Node<JsonNode> node : nodes) {
                            lines.add(node.path() + " = " + node.value());
                        }
                    }
                    case Result.Err(EvaluationError error) -> lines.add("Overflow: " + error.message());
                }
            }
            case Result.Err(ParseError error) -> lines.add("Bad query: " + error.message());
        }
        // lines:
        //   $['store']['book'][0]['title'] = "Sayings of the Century"
        //   $['store']['book'][2]['title'] = "Moby Dick"
        // @end region="quick-start"
        assertEquals(
                List.of(
                        "$['store']['book'][0]['title'] = \"Sayings of the Century\"",
                        "$['store']['book'][2]['title'] = \"Moby Dick\""),
                lines);
    }

    @Test
    void chained() {
        JsonNode root = JsonMapper.builder().build().readTree(BOOKSTORE);
        // @start region="chained"
        // Result.flatMap chains the parse and the evaluation. The error type is a String here.
        Result<List<Node<JsonNode>>, String> authors = JsonPathParser.standard()
                .parse("$..author")
                .mapErr(ParseError::message)
                .flatMap(query -> JsonPathEvaluator.standard()
                        .evaluate(query, root, JacksonJsonModel.INSTANCE)
                        .mapErr(EvaluationError::message));

        int count = authors.fold(List::size, error -> 0); // 4
        // @end region="chained"
        assertEquals(4, count);
    }
}
