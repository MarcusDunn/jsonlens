package ca.marcusdunn.jsonlens.path.core.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import org.junit.jupiter.api.Test;

class NormalizedPathTest {

    private static final NormalizedPath ROOT = NormalizedPath.root();

    @Test
    @Requirement("2.7/bracket-notation")
    void usesBracketNotation() {
        assertEquals("$", ROOT.toString());
        assertEquals("$['store']['book'][0]['title']",
                ROOT.member("store").member("book").element(0).member("title").toString());
        assertEquals("$[10]", ROOT.element(10).toString());
        assertEquals("$['']", ROOT.member("").toString());
    }

    @Test
    @Requirement("2.7/escaping")
    void escapesOnlyTheSpecifiedCharacters() {
        assertEquals("$['\\b\\f\\n\\r\\t']", ROOT.member("\b\f\n\r\t").toString());
        assertEquals("$['\\'\\\\']", ROOT.member("'\\").toString());
        assertEquals("$['\\u0000\\u001f\\u000b\\u001a']", ROOT.member("\u0000\u001f\u000b\u001a").toString());
        assertEquals("$[' ']", ROOT.member(" ").toString());
        // Not escaped: the double quote, the slash, DEL, and characters outside ASCII.
        assertEquals("$['\"/\u007fé 🁁']",
                ROOT.member("\"/\u007fé 🁁").toString());
    }

    @Test
    @Requirement("1.3/non-scalar-strings")
    void writesUnpairedSurrogatesWithoutChange() {
        assertEquals("$['a\uD800b']", ROOT.member("a\uD800b").toString());
    }

    @Test
    @Requirement("2.7/identity")
    void equalPathsIdentifyTheSameLocation() {
        assertEquals(ROOT.member("a").element(1), ROOT.member("a").element(1));
        assertEquals(ROOT.member("a").element(1).hashCode(), ROOT.member("a").element(1).hashCode());
        assertEquals(NormalizedPath.root(), new NormalizedPath.Root());
        assertNotEquals(ROOT.member("a").element(1), ROOT.member("a").element(2));
        assertNotEquals(ROOT.member("a").element(1), ROOT.member("b").element(1));
        assertNotEquals(ROOT.member("0"), ROOT.element(0));
        assertNotEquals(ROOT.member("a"), ROOT.member("a").member("a"));
        assertNotEquals(ROOT.member("a"), ROOT);
        assertNotEquals(ROOT.element(0), ROOT.member("0"));
        assertEquals(ROOT.member("a"), ROOT.member("a"));
        assertEquals(ROOT.member("a"), new NormalizedPath.Member(new NormalizedPath.Root(), JsonString.of("a")));
        assertEquals(ROOT.element(1), new NormalizedPath.Element(new NormalizedPath.Root(), 1));
        // The hash is 31 * hash + segment hash, from the last segment to the root. It starts at 1.
        assertEquals(31 * (31 + "a".hashCode()) + ~2, ROOT.element(2).member("a").hashCode());
        assertEquals(31 * (31 + ~2) + "a".hashCode(), ROOT.member("a").element(2).hashCode());
        assertNotEquals(ROOT.element(0), ROOT);
        assertEquals(ROOT.element(3).hashCode(), ROOT.element(3).hashCode());
        assertNotEquals(ROOT.element(3).hashCode(), ROOT.element(4).hashCode());
        assertEquals(ROOT.member("a").hashCode(), ROOT.member("a").hashCode());
    }

    @Test
    @Requirement("2.7.1/examples")
    void table18() {
        // Table 18 gives these Normalized Paths. The queries that select them are evaluator tests.
        assertEquals("$['a']", ROOT.member("a").toString());
        assertEquals("$[1]", ROOT.element(1).toString());
        assertEquals("$[2]", ROOT.element(2).toString());
        assertEquals("$['a']['b'][1]", ROOT.member("a").member("b").element(1).toString());
        assertEquals("$['\\u000b']", ROOT.member("\u000b").toString());
        assertEquals("$['a']", ROOT.member("a").toString());
    }

    @Test
    @Requirement("lib/core-exception-free")
    void deepPathsDoNotOverflowTheStack() {
        NormalizedPath a = ROOT;
        NormalizedPath b = ROOT;
        for (int i = 0; i < 1_000_000; i++) {
            a = a.element(i);
            b = b.element(i);
        }
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.toString(), b.toString());
    }
    /** A name that is not a TextString: it gives the scalar values of a Java string. */
    private static JsonString custom(String value) {
        return () -> value.codePoints().iterator();
    }

    @Test
    @Requirement("lib/model-member-names")
    void namesInDifferentRepresentationsGiveEqualPaths() {
        NormalizedPath text = ROOT.member("a😀").element(0).member("b");
        NormalizedPath other = ROOT.member(custom("a😀")).element(0).member(custom("b"));
        assertEquals(text, other);
        assertEquals(other, text);
        assertEquals(text.hashCode(), other.hashCode());
        assertEquals(text.toString(), other.toString());
        assertNotEquals(text, ROOT.member(custom("a😁")).element(0).member("b"));
    }

    @Test
    @Requirement("2.7/escaping")
    void escapesNamesInEachRepresentation() {
        assertEquals("$['\\n\\'\\\\\\u0001😀']", ROOT.member(custom("\n'\\\u0001😀")).toString());
    }
}
