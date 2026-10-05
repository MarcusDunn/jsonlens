package ca.marcusdunn.jsonlens.pointer;

import static ca.marcusdunn.jsonlens.pointer.SyntaxTest.pointer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class EvaluationTest {

    /** The example document of RFC 6901, Section 5. */
    static final String EXAMPLE = """
            {
               "foo": ["bar", "baz"],
               "": 0,
               "a/b": 1,
               "c%d": 2,
               "e^f": 3,
               "g|h": 4,
               "i\\\\j": 5,
               "k\\"l": 6,
               " ": 7,
               "m~n": 8
            }""";

    /** The pointers of Section 5, as JSON-decoded strings, and the JSON text of their values. */
    static final Map<String, String> SECTION_5 = new LinkedHashMap<>();

    static {
        SECTION_5.put("", EXAMPLE);
        SECTION_5.put("/foo", "[\"bar\", \"baz\"]");
        SECTION_5.put("/foo/0", "\"bar\"");
        SECTION_5.put("/", "0");
        SECTION_5.put("/a~1b", "1");
        SECTION_5.put("/c%d", "2");
        SECTION_5.put("/e^f", "3");
        SECTION_5.put("/g|h", "4");
        SECTION_5.put("/i\\j", "5");
        SECTION_5.put("/k\"l", "6");
        SECTION_5.put("/ ", "7");
        SECTION_5.put("/m~0n", "8");
    }

    static <N> void assertResolves(Documents.Document<N> document, JsonPointer pointer, String expected) {
        N value = switch (document.resolve(pointer)) {
            case Result.Ok<N, PointerError>(N found) -> found;
            case Result.Err<N, PointerError>(PointerError error) -> fail(document + " " + pointer + ": " + error.message());
        };
        // Compare in one model: copy the value into Jackson.
        JsonNode actual = JacksonJsonModel.INSTANCE.copyOf(document.model(), value)
                .orElse(JacksonJsonModel.INSTANCE.nullValue());
        assertTrue(JacksonJsonModel.INSTANCE.equal(Documents.jackson(expected), actual), document + " " + pointer);
    }

    private static <N> void assertResolves(Documents.Document<N> document, String pointer, String expected) {
        assertResolves(document, pointer(pointer), expected);
    }

    private static <N> PointerError error(Documents.Document<N> document, String pointer) {
        return switch (document.resolve(pointer(pointer))) {
            case Result.Ok<N, PointerError>(N value) -> fail(document + " " + pointer + " resolved");
            case Result.Err<N, PointerError>(PointerError error) -> error;
        };
    }

    @Test
    @Requirement("rfc6901-5/json-string")
    @Requirement("lib/pointer-model-agnostic")
    void section5ExamplesSelectTheDocumentedValues() {
        for (Documents.Document<?> document : Documents.all(EXAMPLE)) {
            SECTION_5.forEach((pointer, expected) -> assertResolves(document, pointer, expected));
        }
    }

    @Test
    @Requirement("lib/pointer-model-agnostic")
    void theResultIsTheCallersOwnNode() {
        JsonNode root = Documents.jackson("{\"a\": [{\"b\": 1}]}");
        JsonNode inner = root.get("a").get(0);
        assertSame(inner, pointer("/a/0").resolve(root, JacksonJsonModel.INSTANCE).orElse(root));
        assertSame(root, pointer("").resolve(root, JacksonJsonModel.INSTANCE).orElse(inner));
    }

    @Test
    @Requirement("rfc6901-4/object-member")
    void membersMatchCodePointsWithoutNormalization() {
        for (Documents.Document<?> document : Documents.all("{\"é\": 1, \"é\": 2, \"A\": 3}")) {
            assertResolves(document, "/é", "1");
            assertResolves(document, "/é", "2");
            assertEquals(new PointerError.MemberNotFound(JsonPointer.root(), "a"), error(document, "/a"));
            assertEquals(new PointerError.MemberNotFound(JsonPointer.root(), "0"), error(document, "/0"));
        }
    }

    @Test
    @Requirement("rfc6901-3/characters")
    void namesWithNulResolve() {
        for (Documents.Document<?> document : Documents.all("{\"a\\u0000b\": 1, \"a\": 2}")) {
            assertResolves(document, "/a\u0000b", "1");
        }
    }

    @Test
    @Requirement("rfc6901-4/array-index")
    void arrayTokensAreIndexes() {
        for (Documents.Document<?> document : Documents.all("{\"a\": [10, 11, [12]]}")) {
            assertResolves(document, "/a/0", "10");
            assertResolves(document, "/a/2/0", "12");
            JsonPointer array = pointer("/a");
            assertEquals(new PointerError.InvalidIndex(array, "01"), error(document, "/a/01"));
            assertEquals(new PointerError.InvalidIndex(array, "x"), error(document, "/a/x"));
            assertEquals(new PointerError.InvalidIndex(array, ""), error(document, "/a/"));
            assertEquals(new PointerError.InvalidIndex(array, "-1"), error(document, "/a/-1"));
        }
    }

    @Test
    @Requirement("rfc6901-4/dash")
    void dashReferencesNoElement() {
        for (Documents.Document<?> document : Documents.all("[1, 2]")) {
            assertEquals(new PointerError.InvalidIndex(JsonPointer.root(), "-"), error(document, "/-"));
        }
    }

    @Test
    @Requirement("rfc6901-4/unresolved")
    void tokensThatDoNotResolveAreErrors() {
        for (Documents.Document<?> document : Documents.all("{\"a\": [1, {\"b\": null}], \"s\": \"x\"}")) {
            assertEquals(new PointerError.IndexOutOfRange(pointer("/a"), "2"), error(document, "/a/2"));
            assertEquals(new PointerError.IndexOutOfRange(pointer("/a"), "2147483647"), error(document, "/a/2147483647"));
            assertEquals(new PointerError.NotAContainer(pointer("/a/0")), error(document, "/a/0/x"));
            assertEquals(new PointerError.NotAContainer(pointer("/s")), error(document, "/s/0"));
            assertEquals(new PointerError.NotAContainer(pointer("/a/1/b")), error(document, "/a/1/b/c"));
            assertEquals(new PointerError.MemberNotFound(pointer("/a/1"), "c"), error(document, "/a/1/c"));
            assertResolves(document, "/a/1/b", "null");
        }
    }

    @Test
    @Requirement("rfc6901-4/duplicate-names")
    void aNameThatIsNotUniqueFails() {
        MappedJson json = Documents.mapped("{\"a\": 1, \"b\": {\"c\": 2, \"c\": 3, \"d\": 4}, \"a\": 5}");
        assertEquals(Result.err(new PointerError.DuplicateName(JsonPointer.root(), "a")),
                pointer("/a").resolve(json.root(), json.model()));
        assertEquals(Result.err(new PointerError.DuplicateName(pointer("/b"), "c")),
                pointer("/b/c").resolve(json.root(), json.model()));
        assertTrue(pointer("/b/d").resolve(json.root(), json.model()).isOk());
    }
    @Test
    @Requirement("rfc6901-4/unresolved")
    void resolvePathGivesEachValueOnThePath() {
        JsonNode root = Documents.jackson("{\"a\": [{\"b\": 1}]}");
        assertEquals(Result.ok(List.of(root, root.get("a"), root.get("a").get(0), root.get("a").get(0).get("b"))),
                pointer("/a/0/b").resolvePath(root, JacksonJsonModel.INSTANCE));
        assertEquals(Result.ok(List.of(root)), JsonPointer.root().resolvePath(root, JacksonJsonModel.INSTANCE));
        assertEquals(Result.err(new PointerError.MemberNotFound(pointer("/a/0"), "c")),
                pointer("/a/0/c").resolvePath(root, JacksonJsonModel.INSTANCE));
    }
}
