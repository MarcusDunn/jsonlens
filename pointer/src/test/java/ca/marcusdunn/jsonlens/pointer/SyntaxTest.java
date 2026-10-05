package ca.marcusdunn.jsonlens.pointer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyntaxTest {

    static JsonPointer pointer(String text) {
        return switch (JsonPointer.parse(text)) {
            case Result.Ok<JsonPointer, PointerError>(JsonPointer value) -> value;
            case Result.Err<JsonPointer, PointerError>(PointerError error) -> fail(text + ": " + error.message());
        };
    }

    private static List<String> tokens(String text) {
        return pointer(text).tokens();
    }

    private static PointerError error(String text) {
        return switch (JsonPointer.parse(text)) {
            case Result.Ok<JsonPointer, PointerError>(JsonPointer value) -> fail(text + " is not an error");
            case Result.Err<JsonPointer, PointerError>(PointerError error) -> error;
        };
    }

    @Test
    @Requirement("rfc6901-3/syntax")
    void pointersAreEmptyOrSlashSeparatedTokens() {
        assertSame(JsonPointer.root(), pointer(""));
        assertEquals(List.of(), tokens(""));
        assertEquals(List.of(""), tokens("/"));
        assertEquals(List.of("", ""), tokens("//"));
        assertEquals(List.of("foo", "0"), tokens("/foo/0"));
        assertEquals(List.of("a", ""), tokens("/a/"));
        assertEquals(new PointerError.MissingSlash(), error("foo"));
        assertEquals(new PointerError.MissingSlash(), error("#/foo"));
        assertEquals(new PointerError.MissingSlash(), error(" /foo"));
    }

    @Test
    @Requirement("rfc6901-3/escaping")
    void tildeEscapesTildeAndSlash() {
        assertEquals(List.of("~", "/", "a~/b"), tokens("/~0/~1/a~0~1b"));
        assertEquals(new PointerError.InvalidEscape(2), error("/a~"));
        assertEquals(new PointerError.InvalidEscape(2), error("/a~2"));
        assertEquals(new PointerError.InvalidEscape(1), error("/~/"));
        assertEquals(new PointerError.InvalidEscape(4), error("/~0/~"));
        assertEquals(new PointerError.InvalidEscape(2), error("/😀~x"));
        assertEquals("/~0/~1/a~0~1b", JsonPointer.of(List.of("~", "/", "a~/b")).toString());
        assertEquals("", JsonPointer.root().toString());
        assertEquals("//", JsonPointer.of(List.of("", "")).toString());
    }

    @Test
    @Requirement("rfc6901-3/characters")
    void tokensHoldAnyOtherCharacter() {
        assertEquals(List.of("\u0000", "a\u0000b"), tokens("/\u0000/a\u0000b"));
        assertEquals(List.of("é中😀", "%", "\"\\"), tokens("/é中😀/%/\"\\"));
        assertEquals(List.of("\uD800"), tokens("/\uD800"));
        String text = "/\u0000/é/m~0n/a~1b";
        assertEquals(text, pointer(text).toString());
    }

    @Test
    @Requirement("rfc6901-4/decode-order")
    void tildeZeroOneDecodesToTildeOne() {
        assertEquals(List.of("~1"), tokens("/~01"));
        assertEquals(List.of("~0"), tokens("/~00"));
        assertEquals(List.of("/0"), tokens("/~10"));
        assertEquals("/~01", JsonPointer.of(List.of("~1")).toString());
    }

    @Test
    @Requirement("rfc6901-3/syntax")
    void pointersParseFromStringsOfAModel() {
        JsonString text = () -> "/a~1b/~01".codePoints().iterator();
        assertEquals(Result.ok(JsonPointer.of(List.of("a/b", "~1"))), JsonPointer.parse(text));
    }

    @Test
    @Requirement("rfc6901-4/array-index")
    void arrayIndexesHaveNoLeadingZero() {
        assertEquals(Maybe.some(0), JsonPointer.arrayIndex("0"));
        assertEquals(Maybe.some(7), JsonPointer.arrayIndex("7"));
        assertEquals(Maybe.some(10), JsonPointer.arrayIndex("10"));
        assertEquals(Maybe.some(9), JsonPointer.arrayIndex("9"));
        assertEquals(Maybe.some(190), JsonPointer.arrayIndex("190"));
        assertEquals(Maybe.some(1_000_000_000), JsonPointer.arrayIndex("1000000000"));
        assertEquals(Maybe.some(Integer.MAX_VALUE), JsonPointer.arrayIndex("2147483647"));
        for (String token : List.of("", "-", "01", "00", "-1", "+1", "1.0", "1e2", " 1", "a", "/", ":", "1/", "1:",
                "2147483648", "9999999999", "10000000000", "１")) {
            assertEquals(Maybe.none(), JsonPointer.arrayIndex(token), token);
        }
    }

    @Test
    void pointersHaveStructure() {
        JsonPointer pointer = pointer("/a/b/c");
        assertFalse(pointer.isRoot());
        assertTrue(JsonPointer.root().isRoot());
        assertEquals(Maybe.some(pointer("/a/b")), pointer.parent());
        assertEquals(Maybe.some(JsonPointer.root()), pointer("/a").parent());
        assertEquals(Maybe.none(), JsonPointer.root().parent());
        assertEquals(Maybe.some("c"), pointer.lastToken());
        assertEquals(Maybe.none(), JsonPointer.root().lastToken());
        assertEquals(pointer("/a/b/c/~1"), pointer.append("/"));
        assertEquals(pointer("/x"), JsonPointer.root().append("x"));
        assertEquals(List.of("a", "b", "c"), pointer.tokens());
    }

    @Test
    void properPrefixesContainOtherLocations() {
        assertTrue(pointer("/a").isProperPrefixOf(pointer("/a/b")));
        assertTrue(JsonPointer.root().isProperPrefixOf(pointer("/a")));
        assertTrue(pointer("/a").isProperPrefixOf(pointer("/a/b/c")));
        assertFalse(pointer("/a").isProperPrefixOf(pointer("/a")));
        assertFalse(pointer("/a").isProperPrefixOf(pointer("/ab")));
        assertFalse(pointer("/a/b").isProperPrefixOf(pointer("/a")));
        assertFalse(pointer("/b").isProperPrefixOf(pointer("/a/b")));
        assertFalse(JsonPointer.root().isProperPrefixOf(JsonPointer.root()));
    }

    @Test
    void pointersWithTheSameTokensAreEqual() {
        assertEquals(pointer("/a/0"), JsonPointer.of(List.of("a", "0")));
        assertEquals(pointer("/a/0").hashCode(), JsonPointer.of(List.of("a", "0")).hashCode());
        assertNotEquals(pointer("/a/0"), pointer("/a/1"));
        assertNotEquals(pointer("/a"), (Object) "/a");
        assertNotEquals(0, pointer("/a").hashCode());
    }

    @Test
    @Requirement("rfc6901-7/errors")
    void errorsDescribeTheirCause() {
        JsonPointer parent = pointer("/a");
        assertEquals("A JSON Pointer that is not empty must start with '/'.", new PointerError.MissingSlash().message());
        assertEquals("At position 3, '~' is not followed by '0' or '1'.", new PointerError.InvalidEscape(3).message());
        assertEquals("At position 4, the URI fragment identifier is not a valid JSON Pointer.",
                new PointerError.InvalidFragment(4).message());
        assertEquals("The object at '/a' has no member 'b'.", new PointerError.MemberNotFound(parent, "b").message());
        assertEquals("The object at '/a' has more than one member 'b'.", new PointerError.DuplicateName(parent, "b").message());
        assertEquals("'-' is not an index of the array at '/a'.", new PointerError.InvalidIndex(parent, "-").message());
        assertEquals("The array at '/a' has no element 5.", new PointerError.IndexOutOfRange(parent, "5").message());
        assertEquals("The value at '/a' is not an object or an array.", new PointerError.NotAContainer(parent).message());
    }
}
