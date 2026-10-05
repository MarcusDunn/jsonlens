package ca.marcusdunn.jsonlens.path.parser;

import static ca.marcusdunn.jsonlens.path.parser.Queries.STANDARD;
import static ca.marcusdunn.jsonlens.path.parser.Queries.parse;
import static ca.marcusdunn.jsonlens.path.parser.Queries.reject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Sections 1.1 and 2.1: the encoding of a query. */
class EncodingTest {

    private static ParseError rejectBytes(int... bytes) {
        byte[] utf8 = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            utf8[i] = (byte) bytes[i];
        }
        return switch (STANDARD.parse(utf8)) {
            case Result.Ok<JsonPathQuery, ParseError> ok -> fail("expected a parse error, but got " + ok);
            case Result.Err<JsonPathQuery, ParseError>(ParseError error) -> error;
        };
    }

    @Test
    @Requirement("2.1/utf8-encoding")
    void parsesWellFormedUtf8() {
        String query = "$['é中😀']";
        assertEquals(Result.ok(parse(query)), STANDARD.parse(query.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @Requirement("2.1/utf8-encoding")
    void rejectsBytesThatAreNotUtf8() {
        // "$['" followed by a bad sequence.
        assertEquals(new ParseError.InvalidUtf8(3), rejectBytes('$', '[', '\'', 0xC3, 0x28, '\'', ']'));
        assertEquals(new ParseError.InvalidUtf8(3), rejectBytes('$', '[', '\'', 0xC0, 0xAF, '\'', ']'));
        assertEquals(new ParseError.InvalidUtf8(3), rejectBytes('$', '[', '\'', 0xED, 0xA0, 0x80, '\'', ']'));
        assertEquals(new ParseError.InvalidUtf8(3), rejectBytes('$', '[', '\'', 0xF8, 0x88, 0x80, 0x80, 0x80));
        assertEquals(new ParseError.InvalidUtf8(1), rejectBytes('$', 0xFF));
        assertEquals(new ParseError.InvalidUtf8(1), rejectBytes('$', 0xE2, 0x82));
    }

    @Test
    @Requirement("1.1/unicode-scalar-values")
    void rejectsUnpairedSurrogates() {
        assertEquals(new ParseError.UnpairedSurrogate(3), reject("$['\ud800']"));
        assertEquals(new ParseError.UnpairedSurrogate(3), reject("$['\udc00']"));
        assertEquals(new ParseError.UnpairedSurrogate(3), reject("$.a\udc00"));
        assertEquals(new ParseError.UnpairedSurrogate(3), reject("$['\ud83d😀']"));
        assertEquals(new ParseError.UnpairedSurrogate(1), reject("$\ud800"));
    }
}
