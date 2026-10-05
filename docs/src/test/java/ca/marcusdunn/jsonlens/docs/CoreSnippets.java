package ca.marcusdunn.jsonlens.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.path.NormalizedPath;
import ca.marcusdunn.jsonlens.path.core.query.ComparisonOperator;
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
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Samples for the core module. */
class CoreSnippets {

    private record Problem(String reason) {}

    @Test
    void result() {
        // @start region="result"
        Result<Integer, Problem> port = parsePort("8080");

        // Examine a result with a switch. The compiler checks that all cases are present.
        String text = switch (port) {
            case Result.Ok(Integer value) -> "port " + value;
            case Result.Err(Problem problem) -> "no port: " + problem.reason();
        };

        // Or change it with map, flatMap, and fold.
        Result<String, Problem> url = port.map(value -> "http://localhost:" + value);
        int value = port.orElse(80);
        // @end region="result"
        assertEquals("port 8080", text);
        assertEquals(Result.ok("http://localhost:8080"), url);
        assertEquals(8080, value);
    }

    // @start region="result-producer"
    static Result<Integer, Problem> parsePort(String text) {
        if (!text.matches("[0-9]{1,5}")) {
            return Result.err(new Problem("not a number: " + text));
        }
        int port = Integer.parseInt(text);
        return port <= 65_535 ? Result.ok(port) : Result.err(new Problem("too large: " + port));
    }
    // @end region="result-producer"

    @Test
    void maybe() {
        // @start region="maybe"
        Maybe<String> present = Maybe.some("value");
        Maybe<String> absent = Maybe.none();

        String text = switch (absent) {
            case Maybe.Some(String value) -> value;
            case Maybe.None() -> "default";
        };
        int length = present.map(String::length).orElse(0);                        // 5
        Result<String, String> required = absent.toResult(() -> "value is missing"); // Err
        // @end region="maybe"
        assertEquals("default", text);
        assertEquals(5, length);
        assertEquals(Result.err("value is missing"), required);
    }

    @Test
    void normalizedPath() {
        // @start region="normalized-path"
        NormalizedPath path = NormalizedPath.root()
                .member("store")
                .member("book")
                .element(0)
                .member("it's");
        String text = path.toString(); // $['store']['book'][0]['it\'s']

        // Paths are records: match them with patterns.
        if (path instanceof NormalizedPath.Member(NormalizedPath parent, JsonString name)) {
            System.out.println(JsonString.copyOf(name) + " is in " + parent); // it's is in $['store']['book'][0]
        }
        // @end region="normalized-path"
        assertEquals("$['store']['book'][0]['it\\'s']", text);
    }

    @Test
    void buildQuery() {
        // @start region="build-query"
        // $.store.book[?length(@.title) > 10]['title'], made without the parser.
        LogicalExpression longTitle = new LogicalExpression.Comparison(
                new FunctionCall.Value("length", List.of(new FunctionArgument.Value(
                        new SingularQuery(Identifier.CURRENT, List.of(new SingularSegment.Name("title")))))),
                ComparisonOperator.GREATER,
                new Literal.NumberLiteral(BigDecimal.TEN));
        JsonPathQuery query = new JsonPathQuery(List.of(
                new Segment.Child(List.of(new Selector.Name("store"))),
                new Segment.Child(List.of(new Selector.Name("book"))),
                new Segment.Child(List.of(new Selector.Filter(longTitle))),
                new Segment.Child(List.of(new Selector.Name("title")))));

        String text = query.toString(); // $['store']['book'][?length(@['title']) > 10]['title']
        // @end region="build-query"
        assertEquals("$['store']['book'][?length(@['title']) > 10]['title']", text);
    }

    @Test
    void escapeNames() {
        // @start region="escape-names"
        // A name from user input cannot change the structure of the query.
        String input = "x'] || $..*[?@ == '";
        JsonPathQuery query = new JsonPathQuery(List.of(new Segment.Child(List.of(new Selector.Name(input)))));
        String text = query.toString(); // $['x\'] || $..*[?@ == \'']
        // @end region="escape-names"
        assertEquals("$['x\\'] || $..*[?@ == \\'']", text);
    }
}
