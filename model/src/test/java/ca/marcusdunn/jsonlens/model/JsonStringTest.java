package ca.marcusdunn.jsonlens.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.PrimitiveIterator;
import org.junit.jupiter.api.Test;

@Requirement("lib/model-string-representation")
class JsonStringTest {

    /** A string that is not a TextString: it gives the scalar values of a Java string. */
    private static JsonString custom(String value) {
        return () -> value.codePoints().iterator();
    }

    @Test
    void ofDoesNotCopy() {
        String value = "abc";
        assertSame(value, JsonString.copyOf(JsonString.of(value)));
    }

    @Test
    void copyOfReadsScalarValues() {
        assertEquals("aé😀", JsonString.copyOf(custom("aé😀")));
        assertEquals("", JsonString.copyOf(custom("")));
    }

    @Test
    void scalarValuesStartAgainEachTime() {
        JsonString string = JsonString.of("x😀");
        for (int i = 0; i < 2; i++) {
            PrimitiveIterator.OfInt values = string.scalarValues();
            assertEquals('x', values.nextInt());
            assertEquals(0x1F600, values.nextInt());
            assertFalse(values.hasNext());
        }
    }

    @Test
    @Requirement("2.3.5.2.2/string-ordering")
    void comparesScalarValues() {
        assertEquals(0, JsonString.compare(JsonString.of("abc"), custom("abc")));
        assertTrue(JsonString.compare(JsonString.of("ab"), JsonString.of("abc")) < 0);
        assertTrue(JsonString.compare(custom("abc"), JsonString.of("ab")) > 0);
        assertTrue(JsonString.compare(JsonString.of("abd"), custom("abc")) > 0);
        assertTrue(JsonString.compare(JsonString.of(""), JsonString.of("a")) < 0);
        // U+FFFF is less than U+1F600, but its UTF-16 code unit is greater than a high surrogate.
        assertTrue(JsonString.compare(JsonString.of("￿"), JsonString.of("😀")) < 0);
    }

    @Test
    void equality() {
        assertTrue(JsonString.equal(JsonString.of("aé"), JsonString.of("aé")));
        assertTrue(JsonString.equal(JsonString.of("aé"), custom("aé")));
        assertTrue(JsonString.equal(custom("x"), custom("x")));
        assertFalse(JsonString.equal(JsonString.of("a"), JsonString.of("b")));
        assertFalse(JsonString.equal(custom("a"), JsonString.of("ab")));
        assertFalse(JsonString.equal(JsonString.of("é"), JsonString.of("é")));
    }

    @Test
    @Requirement("2.4.4/string")
    void lengthCountsScalarValues() {
        assertEquals(0, JsonString.length(JsonString.of("")));
        assertEquals(3, JsonString.length(JsonString.of("aé😀")));
        assertEquals(3, JsonString.length(custom("aé😀")));
        assertEquals(0, JsonString.length(custom("")));
    }
    @Test
    @Requirement("lib/model-member-names")
    void hashIsTheHashOfTheString() {
        for (String value : new String[] {"", "a", "aé", "😀", "a😀b", "\uD800"}) {
            assertEquals(value.hashCode(), JsonString.hash(JsonString.of(value)), value);
            assertEquals(value.hashCode(), JsonString.hash(custom(value)), value);
        }
    }
    @Test
    void scalarValuesOfAStringEndWithAnException() {
        PrimitiveIterator.OfInt values = JsonString.of("a😀\uD800").scalarValues();
        assertEquals('a', values.nextInt());
        assertEquals(0x1F600, values.nextInt());
        assertEquals(0xD800, values.nextInt());
        assertFalse(values.hasNext());
        org.junit.jupiter.api.Assertions.assertThrows(java.util.NoSuchElementException.class, values::nextInt);
    }
}
