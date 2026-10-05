package ca.marcusdunn.jsonlens.path.parser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.core.query.LogicalExpression;
import ca.marcusdunn.jsonlens.path.core.query.Segment;
import ca.marcusdunn.jsonlens.path.core.query.Selector;
import java.util.Arrays;

/** Helpers for parser tests. */
final class Queries {

    static final JsonPathParser STANDARD = JsonPathParser.standard();

    private Queries() {}

    static JsonPathQuery parse(JsonPathParser parser, String query) {
        return switch (parser.parse(query)) {
            case Result.Ok<JsonPathQuery, ParseError>(JsonPathQuery value) -> value;
            case Result.Err<JsonPathQuery, ParseError>(ParseError error) ->
                    fail("expected '" + query + "' to parse, but: " + error.message());
        };
    }

    static JsonPathQuery parse(String query) {
        return parse(STANDARD, query);
    }

    static ParseError reject(JsonPathParser parser, String query) {
        return switch (parser.parse(query)) {
            case Result.Ok<JsonPathQuery, ParseError>(JsonPathQuery value) ->
                    fail("expected '" + query + "' to be rejected, but it parsed as " + value);
            case Result.Err<JsonPathQuery, ParseError>(ParseError error) -> error;
        };
    }

    static ParseError reject(String query) {
        return reject(STANDARD, query);
    }

    static <E extends ParseError> E reject(String query, Class<E> type) {
        return assertInstanceOf(type, reject(query), query);
    }

    static <E extends ParseError> E reject(JsonPathParser parser, String query, Class<E> type) {
        return assertInstanceOf(type, reject(parser, query), query);
    }

    /** Returns the canonical text of a query: bracket notation and single quotes. */
    static String canonical(String query) {
        return parse(query).toString();
    }

    static void accepts(String... queries) {
        assertAll(Arrays.stream(queries).map(q -> () -> parse(q)));
    }

    static void accepts(JsonPathParser parser, String... queries) {
        assertAll(Arrays.stream(queries).map(q -> () -> parse(parser, q)));
    }

    static void rejects(String... queries) {
        assertAll(Arrays.stream(queries).map(q -> () -> reject(q)));
    }

    static void rejects(JsonPathParser parser, String... queries) {
        assertAll(Arrays.stream(queries).map(q -> () -> reject(parser, q)));
    }

    /** Returns the only selector of the only segment. */
    static Selector selector(String query) {
        JsonPathQuery parsed = parse(query);
        if (parsed.segments().size() != 1 || parsed.segments().getFirst().selectors().size() != 1) {
            fail("expected one segment with one selector: " + parsed);
        }
        return parsed.segments().getFirst().selectors().getFirst();
    }

    /** Returns the expression of a query of the form {@code $[?<expression>]}. */
    static LogicalExpression filter(String query) {
        return assertInstanceOf(Selector.Filter.class, selector(query)).expression();
    }

    static Segment onlySegment(String query) {
        JsonPathQuery parsed = parse(query);
        if (parsed.segments().size() != 1) {
            fail("expected one segment: " + parsed);
        }
        return parsed.segments().getFirst();
    }
}
