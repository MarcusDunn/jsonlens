package ca.marcusdunn.jsonlens.pointer;

import static ca.marcusdunn.jsonlens.pointer.SyntaxTest.pointer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

@Requirement("rfc6901-6/uri-fragment")
class FragmentTest {

    /** The fragment identifiers of RFC 6901, Section 6, and the pointers of Section 5 that they encode. */
    private static final Map<String, String> SECTION_6 = new LinkedHashMap<>();

    static {
        SECTION_6.put("#", "");
        SECTION_6.put("#/foo", "/foo");
        SECTION_6.put("#/foo/0", "/foo/0");
        SECTION_6.put("#/", "/");
        SECTION_6.put("#/a~1b", "/a~1b");
        SECTION_6.put("#/c%25d", "/c%d");
        SECTION_6.put("#/e%5Ef", "/e^f");
        SECTION_6.put("#/g%7Ch", "/g|h");
        SECTION_6.put("#/i%5Cj", "/i\\j");
        SECTION_6.put("#/k%22l", "/k\"l");
        SECTION_6.put("#/%20", "/ ");
        SECTION_6.put("#/m~0n", "/m~0n");
    }

    private static JsonPointer fragment(String text) {
        return switch (JsonPointer.parseFragment(text)) {
            case Result.Ok<JsonPointer, PointerError>(JsonPointer value) -> value;
            case Result.Err<JsonPointer, PointerError>(PointerError error) -> fail(text + ": " + error.message());
        };
    }

    @Test
    void section6FragmentsSelectTheDocumentedValues() {
        for (Documents.Document<?> document : Documents.all(EvaluationTest.EXAMPLE)) {
            SECTION_6.forEach((fragment, pointer) ->
                    EvaluationTest.assertResolves(document, fragment(fragment), Objects.requireNonNull(EvaluationTest.SECTION_5.get(pointer))));
        }
    }

    @Test
    void pointersWriteTheSection6Fragments() {
        SECTION_6.forEach((fragment, pointer) -> assertEquals(Maybe.some(fragment), pointer(pointer).toFragment(), pointer));
    }

    @Test
    void fragmentsEncodeUtf8Bytes() {
        JsonPointer pointer = JsonPointer.of(List.of("é", "中", "😀", "\u0000", "#", "%", "[]"));
        String fragment = "#/%C3%A9/%E4%B8%AD/%F0%9F%98%80/%00/%23/%25/%5B%5D";
        assertEquals(Maybe.some(fragment), pointer.toFragment());
        assertEquals(pointer, fragment(fragment));
        assertEquals(pointer, fragment("#/%c3%a9/%e4%b8%ad/%f0%9f%98%80/%00/%23/%25/%5b%5d"));
    }

    @Test
    void aTokenWithAnUnpairedSurrogateHasNoFragment() {
        // UTF-8 encodes only Unicode scalar values, so the bytes of Section 6 do not exist.
        assertEquals(Maybe.none(), JsonPointer.of(List.of("a\uD800")).toFragment());
        assertEquals(Maybe.none(), JsonPointer.of(List.of("ok", "\uDC00b")).toFragment());
        assertEquals(Maybe.some("#/%F0%9F%98%80"), JsonPointer.of(List.of("\uD83D\uDE00")).toFragment());
    }

    @Test
    void fragmentsKeepThePermittedCharacters() {
        String permitted = "ABCZabcz0189-._~!$&'()*+,;=:@?";
        assertEquals(Maybe.some("#/" + permitted.replace("~", "~0")), JsonPointer.of(List.of(permitted)).toFragment());
        assertEquals(List.of("A", "?"), fragment("#/A/?").tokens());
    }

    @Test
    @Requirement("rfc6901-7/errors")
    void invalidFragmentsAreErrors() {
        assertEquals(Result.err(new PointerError.InvalidFragment(0)), JsonPointer.parseFragment(""));
        assertEquals(Result.err(new PointerError.InvalidFragment(0)), JsonPointer.parseFragment("/a"));
        assertEquals(Result.err(new PointerError.InvalidFragment(2)), JsonPointer.parseFragment("#/ "));
        assertEquals(Result.err(new PointerError.InvalidFragment(3)), JsonPointer.parseFragment("#/a#"));
        assertEquals(Result.err(new PointerError.InvalidFragment(2)), JsonPointer.parseFragment("#/é"));
        assertEquals(Result.err(new PointerError.InvalidFragment(2)), JsonPointer.parseFragment("#/%"));
        assertEquals(Result.err(new PointerError.InvalidFragment(2)), JsonPointer.parseFragment("#/%2"));
        assertEquals(Result.err(new PointerError.InvalidFragment(2)), JsonPointer.parseFragment("#/%G0"));
        assertEquals(Result.err(new PointerError.InvalidFragment(2)), JsonPointer.parseFragment("#/%0G"));
        // The bytes must be UTF-8.
        assertEquals(Result.err(new PointerError.InvalidFragment(3)), JsonPointer.parseFragment("#/a%FF"));
        assertEquals(Result.err(new PointerError.InvalidFragment(2)), JsonPointer.parseFragment("#/%C3"));
        assertEquals(Result.err(new PointerError.InvalidFragment(2)), JsonPointer.parseFragment("#/%C3%28"));
        // The decoded pointer must be valid too.
        assertEquals(Result.err(new PointerError.MissingSlash()), JsonPointer.parseFragment("#a"));
        assertEquals(Result.err(new PointerError.InvalidEscape(1)), JsonPointer.parseFragment("#/~2"));
    }
}
