package ca.marcusdunn.jsonlens.path.parser;

import static ca.marcusdunn.jsonlens.path.parser.Queries.accepts;
import static ca.marcusdunn.jsonlens.path.parser.Queries.parse;
import static ca.marcusdunn.jsonlens.path.parser.Queries.reject;
import static ca.marcusdunn.jsonlens.path.parser.Queries.rejects;
import static ca.marcusdunn.jsonlens.path.parser.Queries.selector;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.path.core.query.Selector;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import org.junit.jupiter.api.Test;

/** Section 2.3.1: name selectors and string literals. */
class NameSelectorTest {

    private static String name(String query) {
        return ((Selector.Name) selector(query)).name();
    }

    @Test
    @Requirement("2.3.1.1/quotes")
    void singleOrDoubleQuotes() {
        assertEquals(parse("$['a']"), parse("$[\"a\"]"));
        assertEquals("", name("$['']"));
        assertEquals("", name("$[\"\"]"));
        rejects("$['a\"]", "$[\"a']", "$[a]", "$[`a`]");
    }

    @Test
    @Requirement("2.3.1.1/unescaped")
    void unescapedCharacters() {
        String allowed = " !#$%&()*+,-./09:;<=>?@AZ[]^_`az{|}~\u007f\u0080\ud7ff\ue000\uffff\ud83c\udc41";
        assertEquals(allowed, name("$['" + allowed + "']"));
        assertEquals(allowed, name("$[\"" + allowed + "\"]"));
        for (char c = 0; c < 0x20; c++) {
            assertEquals(new ParseError.ControlCharacter(3, c), reject("$['" + c + "']"));
        }
    }

    @Test
    @Requirement("2.3.1.1/own-quote-escaped")
    void quoteEscapes() {
        assertEquals("a'b", name("$['a\\'b']"));
        assertEquals("a\"b", name("$[\"a\\\"b\"]"));
        assertEquals("a'b", name("$[\"a'b\"]"));
        assertEquals("a\"b", name("$['a\"b']"));
        rejects("$['a'b']", "$[\"a\"b\"]");
        assertEquals(new ParseError.InvalidEscape(4, "\\'"), reject("$[\"a\\'b\"]"));
        assertEquals(new ParseError.InvalidEscape(4, "\\\""), reject("$['a\\\"b']"));
    }

    @Test
    @Requirement("2.3.1.1/escapes")
    void permittedEscapes() {
        accepts("$['\\b\\f\\n\\r\\t\\/\\\\\\u0041']");
        for (String bad : new String[] {"\\a", "\\v", "\\0", "\\x41", "\\e", "\\ ", "\\U0041", "\\u41", "\\u004", "\\u00G1"}) {
            reject("$['" + bad + "']", ParseError.InvalidEscape.class);
        }
        reject("$['\\", ParseError.UnexpectedEnd.class);
    }

    @Test
    @Requirement("2.3.1.1/surrogate-pairs")
    void surrogatePairEscapes() {
        assertEquals("\ud83c\udc41", name("$['\\uD83C\\uDC41']"));
        assertEquals("\ud83c\udc41", name("$[\"\\ud83c\\udc41\"]"));
        assertEquals(new ParseError.UnpairedSurrogate(3), reject("$['\\uD83C']"));
        assertEquals(new ParseError.UnpairedSurrogate(3), reject("$['\\uDC41']"));
        assertEquals(new ParseError.UnpairedSurrogate(3), reject("$['\\uD83Cx']"));
        assertEquals(new ParseError.UnpairedSurrogate(3), reject("$['\\uD83C\\u0041']"));
        assertEquals(new ParseError.UnpairedSurrogate(3), reject("$['\\uD83C\\uD83C']"));
    }

    @Test
    @Requirement("2.3.1.1/hex-case")
    void hexDigitsInAnyCase() {
        assertEquals("\u00e9\u00e9\u00e9", name("$['\\u00e9\\u00E9\\u00e9']"));
        assertEquals("\uabcd\uabcd", name("$['\\uABCD\\uabcd']"));
        reject("$['\\U00e9']", ParseError.InvalidEscape.class);
        reject("$['\\u\uff10\uff10\uff14\uff11']", ParseError.InvalidEscape.class); // full-width digits
    }

    @Test
    @Requirement("2.3.1.2/escape-replacement")
    void escapesAreReplacedAsInTable4() {
        assertEquals("\b", name("$['\\b']"));
        assertEquals("\t", name("$['\\t']"));
        assertEquals("\n", name("$['\\n']"));
        assertEquals("\f", name("$['\\f']"));
        assertEquals("\r", name("$['\\r']"));
        assertEquals("\"", name("$[\"\\\"\"]"));
        assertEquals("'", name("$['\\'']"));
        assertEquals("/", name("$['\\/']"));
        assertEquals("\\", name("$['\\\\']"));
        assertEquals("a", name("$['\\u0061']"));
        assertEquals("\u000b", name("$[\"\\u000B\"]"));
    }
}
