package ca.marcusdunn.jsonlens.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.evaluator.EvaluationError;
import ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator;
import ca.marcusdunn.jsonlens.path.evaluator.Node;
import ca.marcusdunn.jsonlens.jackson.JacksonStream;
import ca.marcusdunn.jsonlens.jackson.JacksonStreamNode;
import ca.marcusdunn.jsonlens.jackson.StreamError;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.json.JsonMapper;

/** The samples of the documentation of JacksonStream. */
class JacksonStreamSnippets {

    @Test
    void stream() {
        // @start region="stream"
        JsonMapper mapper = JsonMapper.builder().build();
        JsonParser parser = mapper.createParser("""
                {"first": {"id": 1}, "rest": [{"id": 2}, {"id": 3}]}""");
        JacksonStream stream = JacksonStream.open(parser).orElse(null);

        // The query reads only up to the first member "first".
        JsonPathQuery query = JsonPathParser.standard().parse("$.first.id").orElse(null);
        Result<List<Node<JacksonStreamNode>>, EvaluationError> result =
                JsonPathEvaluator.standard().evaluate(query, stream.root(), stream.model());

        // After the query: an error of the parser ends the document early.
        Maybe<StreamError> failure = stream.failure(); // None
        // @end region="stream"
        assertEquals(List.of("$['first']['id']"), result.orElse(List.of()).stream().map(node -> node.path().toString()).toList());
        assertEquals(Maybe.none(), failure);
    }
}
