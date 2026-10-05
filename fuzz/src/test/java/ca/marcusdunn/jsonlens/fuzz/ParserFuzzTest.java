package ca.marcusdunn.jsonlens.fuzz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import ca.marcusdunn.jsonlens.path.parser.ParseError;
import com.code_intelligence.jazzer.junit.DictionaryEntries;
import com.code_intelligence.jazzer.junit.FuzzTest;

/** The parser never throws, agrees for text and bytes, and reads its own output again. */
class ParserFuzzTest {

    private static final JsonPathParser PARSER = JsonPathParser.standard();

    @FuzzTest(maxDuration = "5m")
    @DictionaryEntries({
        "$", "@", ".", "..", "[", "]", "[?", "*", ",", ":", "'", "\"", "\\", "\\u", "\\uD83D\\uDE00",
        "==", "!=", "<", "<=", ">", ">=", "&&", "||", "!", "(", ")", "-", "0", "1", "e+", "E-", ".5",
        "true", "false", "null", "length(", "count(", "match(", "search(", "value(", " ", "\t"
    })
    void parse(byte[] data) {
        Result<JsonPathQuery, ParseError> fromBytes = PARSER.parse(data);
        String text = Inputs.utf8(data);
        if (text == null) {
            assertInstanceOf(ParseError.InvalidUtf8.class,
                    assertInstanceOf(Result.Err.class, fromBytes).error());
            return;
        }
        Result<JsonPathQuery, ParseError> fromText = PARSER.parse(text);
        assertEquals(fromText, fromBytes, text);
        if (fromText instanceof Result.Ok<JsonPathQuery, ParseError>(JsonPathQuery query)) {
            String written = query.toString();
            assertEquals(fromText, PARSER.parse(written), "text: " + text + " written: " + written);
            assertEquals(written, ((JsonPathQuery) ((Result.Ok<?, ?>) PARSER.parse(written)).value()).toString());
        }
    }
}
