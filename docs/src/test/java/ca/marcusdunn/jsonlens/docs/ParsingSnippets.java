package ca.marcusdunn.jsonlens.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.core.query.Segment;
import ca.marcusdunn.jsonlens.path.core.query.Selector;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import ca.marcusdunn.jsonlens.path.parser.ParseError;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Samples for the parser module. */
class ParsingSnippets {

    @Test
    void parse() {
        // @start region="parse"
        JsonPathParser parser = JsonPathParser.standard(); // immutable and thread-safe

        Result<JsonPathQuery, ParseError> result = parser.parse("$.store.book[0].title");
        String text = result.map(JsonPathQuery::toString).orElse("not valid");
        // text: "$['store']['book'][0]['title']" (shorthand forms become bracket notation)
        // @end region="parse"
        assertEquals("$['store']['book'][0]['title']", text);
    }

    @Test
    void errors() {
        // @start region="errors"
        String describe = switch (JsonPathParser.standard().parse("$[?length(@.*) > 3]")) {
            case Result.Ok(JsonPathQuery query) -> "valid: " + query;
            case Result.Err(ParseError.NonSingularQuery(int position)) ->
                    "@.* can select more than one node, at position " + position;
            case Result.Err(ParseError.UnknownFunction(int position, String name)) ->
                    "no function " + name;
            case Result.Err(ParseError error) -> error.message(); // all other errors
        };
        // describe: "@.* can select more than one node, at position 10"
        // @end region="errors"
        assertEquals("@.* can select more than one node, at position 10", describe);
    }

    @Test
    void messages() {
        // @start region="messages"
        ParseError error = JsonPathParser.standard().parse("$.a[01]").fold(query -> null, e -> e);
        String message = error.message();
        // "The integer '01' is not valid. An integer must not have a leading zero or be '-0'. Position: 4."
        // @end region="messages"
        assertEquals(
                "The integer '01' is not valid. An integer must not have a leading zero or be '-0'. Position: 4.",
                message);
    }

    @Test
    void utf8() {
        // @start region="utf8"
        byte[] utf8 = "$['café']".getBytes(StandardCharsets.UTF_8);
        Result<JsonPathQuery, ParseError> fromBytes = JsonPathParser.standard().parse(utf8);

        byte[] broken = {'$', '[', '\'', (byte) 0xC3, '(', '\'', ']'};
        Result<JsonPathQuery, ParseError> rejected = JsonPathParser.standard().parse(broken);
        // rejected: Err(InvalidUtf8[position=3])
        // @end region="utf8"
        assertTrue(fromBytes.isOk());
        assertEquals(Result.err(new ParseError.InvalidUtf8(3)), rejected);
    }

    @Test
    void tree() {
        // @start region="tree"
        JsonPathQuery query = JsonPathParser.standard().parse("$..book[0, 'x']").orElse(null);
        Segment segment = query.segments().getFirst();
        // segment: Descendant[selectors=[Name[name=book]]]
        List<Selector> selectors = query.segments().get(1).selectors();
        // selectors: [Index[index=0], Name[name=x]]
        // @end region="tree"
        assertTrue(segment instanceof Segment.Descendant);
        assertEquals(List.of(new Selector.Index(0), new Selector.Name("x")), selectors);
    }
}
