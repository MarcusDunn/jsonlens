package ca.marcusdunn.jsonlens.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.pointer.JsonPointer;
import ca.marcusdunn.jsonlens.pointer.PointerError;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class OperationsTest {

    private static final JacksonJsonModel MODEL = JacksonJsonModel.INSTANCE;

    static JsonPatch<JsonNode> patch(String json) {
        return switch (JsonPatch.parse(Documents.jackson(json), MODEL)) {
            case Result.Ok<JsonPatch<JsonNode>, PatchError>(JsonPatch<JsonNode> value) -> value;
            case Result.Err<JsonPatch<JsonNode>, PatchError>(PatchError error) -> fail(error.message());
        };
    }

    static JsonPointer pointer(String text) {
        return JsonPointer.parse(text).orElse(JsonPointer.root());
    }

    /// Applies a patch in place and to a copy, and checks both results. The copy leaves the
    /// document unchanged.
    static JsonNode assertPatched(String document, String patch, String expected) {
        JsonNode original = Documents.jackson(document);
        JsonNode copy = patch(patch).applyToCopy(original, MODEL).fold(root -> root, error -> fail(error.message()));
        assertTrue(MODEL.equal(Documents.jackson(expected), copy), () -> "copy: expected " + expected + " but was " + copy);
        assertTrue(MODEL.equal(Documents.jackson(document), original), () -> "applyToCopy changed the document: " + original);
        JsonNode target = Documents.jackson(document);
        JsonNode result = patch(patch).apply(target, MODEL).fold(root -> root, error -> fail(error.message()));
        assertTrue(MODEL.equal(Documents.jackson(expected), result), () -> "expected " + expected + " but was " + result);
        return result;
    }

    /// Applies a patch that must fail, in place and to a copy, and checks that the document has its
    /// original value.
    static void assertFails(String document, String patch, PatchError expected) {
        JsonNode original = Documents.jackson(document);
        assertEquals(Result.err(expected), patch(patch).applyToCopy(original, MODEL));
        assertTrue(MODEL.equal(Documents.jackson(document), original), () -> "applyToCopy changed the document: " + original);
        JsonNode target = Documents.jackson(document);
        assertEquals(Result.err(expected), patch(patch).apply(target, MODEL));
        assertTrue(MODEL.equal(Documents.jackson(document), target), () -> "the failed patch changed the document: " + target);
    }

    @Test
    @Requirement("rfc6902-3/sequential")
    void operationsApplyInOrderToTheResultOfThePreviousOne() {
        assertPatched("{}", """
                [{"op": "add", "path": "/a", "value": []},
                 {"op": "add", "path": "/a/-", "value": 1},
                 {"op": "copy", "from": "/a", "path": "/b"},
                 {"op": "test", "path": "/b", "value": [1]}]""", "{\"a\": [1], \"b\": [1]}");
        assertPatched("[1]", "[]", "[1]");
    }

    @Test
    @Requirement("rfc6902-4.1/add")
    void addAddsMembersAndInsertsElements() {
        assertPatched("{\"a\": 1}", "[{\"op\": \"add\", \"path\": \"/b\", \"value\": {\"c\": [2]}}]",
                "{\"a\": 1, \"b\": {\"c\": [2]}}");
        assertPatched("{\"a\": 1}", "[{\"op\": \"add\", \"path\": \"/a\", \"value\": 2}]", "{\"a\": 2}");
        assertPatched("[1, 2]", "[{\"op\": \"add\", \"path\": \"/0\", \"value\": 0}]", "[0, 1, 2]");
        assertPatched("[1, 2]", "[{\"op\": \"add\", \"path\": \"/1\", \"value\": 9}]", "[1, 9, 2]");
        assertPatched("[1, 2]", "[{\"op\": \"add\", \"path\": \"/2\", \"value\": 3}]", "[1, 2, 3]");
        assertPatched("[1, 2]", "[{\"op\": \"add\", \"path\": \"/-\", \"value\": 3}]", "[1, 2, 3]");
        assertPatched("{\"a\": [[]]}", "[{\"op\": \"add\", \"path\": \"/a/0/-\", \"value\": 1}]", "{\"a\": [[1]]}");
        assertPatched("{\"-\": 1}", "[{\"op\": \"add\", \"path\": \"/-\", \"value\": 2}]", "{\"-\": 2}");
        assertPatched("{\"\": 1}", "[{\"op\": \"add\", \"path\": \"/\", \"value\": 2}]", "{\"\": 2}");
    }

    @Test
    @Requirement("rfc6902-4.1/add")
    void addAtTheRootReplacesTheDocument() {
        assertPatched("{\"a\": 1}", "[{\"op\": \"add\", \"path\": \"\", \"value\": [1]}]", "[1]");
        assertPatched("1", "[{\"op\": \"add\", \"path\": \"\", \"value\": \"x\"}]", "\"x\"");
    }

    @Test
    @Requirement("rfc6902-4.1/add-errors")
    void addNeedsAnExistingContainerAndAValidIndex() {
        String add = "[{\"op\": \"add\", \"path\": \"%s\", \"value\": 1}]";
        assertFails("{\"q\": {}}", add.formatted("/a/b"),
                new PatchError.PathNotFound(0, new PointerError.MemberNotFound(JsonPointer.root(), "a")));
        assertFails("[1, 2]", add.formatted("/3"),
                new PatchError.PathNotFound(0, new PointerError.IndexOutOfRange(JsonPointer.root(), "3")));
        assertFails("[1, 2]", add.formatted("/-1"),
                new PatchError.PathNotFound(0, new PointerError.InvalidIndex(JsonPointer.root(), "-1")));
        assertFails("[1, 2]", add.formatted("/01"),
                new PatchError.PathNotFound(0, new PointerError.InvalidIndex(JsonPointer.root(), "01")));
        assertFails("[1, 2]", add.formatted("/a"),
                new PatchError.PathNotFound(0, new PointerError.InvalidIndex(JsonPointer.root(), "a")));
        assertFails("{\"s\": \"x\"}", add.formatted("/s/0"),
                new PatchError.PathNotFound(0, new PointerError.NotAContainer(pointer("/s"))));
        assertFails("[[1]]", add.formatted("/-/0"),
                new PatchError.PathNotFound(0, new PointerError.InvalidIndex(JsonPointer.root(), "-")));
        // A valid index after the largest int is outside each array.
        for (String index : List.of("2147483648", "99999999999")) {
            PatchError outside = new PatchError.PathNotFound(0, new PointerError.IndexOutOfRange(JsonPointer.root(), index));
            assertFails("[1]", add.formatted("/" + index), outside);
            assertFails("[1]", "[{\"op\": \"remove\", \"path\": \"/" + index + "\"}]", outside);
            assertFails("[1]", "[{\"op\": \"replace\", \"path\": \"/" + index + "\", \"value\": 0}]", outside);
        }
    }

    @Test
    @Requirement("rfc6902-4.2/remove")
    void removeRemovesMembersAndElements() {
        assertPatched("{\"a\": 1, \"b\": 2}", "[{\"op\": \"remove\", \"path\": \"/a\"}]", "{\"b\": 2}");
        assertPatched("[1, 2, 3]", "[{\"op\": \"remove\", \"path\": \"/0\"}]", "[2, 3]");
        assertPatched("[1, 2, 3]", "[{\"op\": \"remove\", \"path\": \"/2\"}]", "[1, 2]");
        assertPatched("{\"a\": null}", "[{\"op\": \"remove\", \"path\": \"/a\"}]", "{}");
        String remove = "[{\"op\": \"remove\", \"path\": \"%s\"}]";
        assertFails("{\"a\": 1}", remove.formatted("/b"),
                new PatchError.PathNotFound(0, new PointerError.MemberNotFound(JsonPointer.root(), "b")));
        assertFails("[1, 2]", remove.formatted("/2"),
                new PatchError.PathNotFound(0, new PointerError.IndexOutOfRange(JsonPointer.root(), "2")));
        assertFails("[1, 2]", remove.formatted("/-"),
                new PatchError.PathNotFound(0, new PointerError.InvalidIndex(JsonPointer.root(), "-")));
        assertFails("{}", remove.formatted("/a/b"),
                new PatchError.PathNotFound(0, new PointerError.MemberNotFound(JsonPointer.root(), "a")));
    }

    @Test
    @Requirement("rfc6902-4.2/remove-root")
    void removeOfTheWholeDocumentFails() {
        assertFails("{\"a\": 1}", "[{\"op\": \"remove\", \"path\": \"\"}]", new PatchError.RootRemoved(0));
    }

    @Test
    @Requirement("rfc6902-4.3/replace")
    void replaceReplacesAnExistingValue() {
        assertPatched("{\"a\": 1}", "[{\"op\": \"replace\", \"path\": \"/a\", \"value\": [2]}]", "{\"a\": [2]}");
        assertPatched("[1, 2]", "[{\"op\": \"replace\", \"path\": \"/1\", \"value\": 3}]", "[1, 3]");
        assertPatched("{\"a\": 1}", "[{\"op\": \"replace\", \"path\": \"\", \"value\": 5}]", "5");
        String replace = "[{\"op\": \"replace\", \"path\": \"%s\", \"value\": 0}]";
        assertFails("{\"a\": 1}", replace.formatted("/b"),
                new PatchError.PathNotFound(0, new PointerError.MemberNotFound(JsonPointer.root(), "b")));
        assertFails("[1]", replace.formatted("/1"),
                new PatchError.PathNotFound(0, new PointerError.IndexOutOfRange(JsonPointer.root(), "1")));
        assertFails("[1]", replace.formatted("/-"),
                new PatchError.PathNotFound(0, new PointerError.InvalidIndex(JsonPointer.root(), "-")));
    }

    @Test
    @Requirement("rfc6902-4.4/move")
    void moveRemovesAndAddsTheSameNode() {
        JsonNode target = Documents.jackson("{\"a\": {\"b\": [1]}, \"c\": []}");
        JsonNode moved = target.get("a").get("b");
        JsonNode result = patch("[{\"op\": \"move\", \"from\": \"/a/b\", \"path\": \"/c/0\"}]").apply(target, MODEL)
                .fold(root -> root, error -> fail(error.message()));
        // The node moves without a copy.
        assertSame(moved, result.get("c").get(0));
        assertTrue(MODEL.equal(Documents.jackson("{\"a\": {}, \"c\": [[1]]}"), result));
        // The path is evaluated after the removal.
        assertPatched("[1, 2, 3]", "[{\"op\": \"move\", \"from\": \"/0\", \"path\": \"/2\"}]", "[2, 3, 1]");
        assertPatched("[1, 2, 3]", "[{\"op\": \"move\", \"from\": \"/0\", \"path\": \"/-\"}]", "[2, 3, 1]");
        assertPatched("{\"a\": 1}", "[{\"op\": \"move\", \"from\": \"/a\", \"path\": \"\"}]", "1");
    }

    @Test
    @Requirement("rfc6902-4.4/move")
    void moveToTheSameLocationHasNoEffect() {
        assertPatched("{\"a\": [1]}", "[{\"op\": \"move\", \"from\": \"/a\", \"path\": \"/a\"}]", "{\"a\": [1]}");
        assertPatched("{\"a\": [1]}", "[{\"op\": \"move\", \"from\": \"\", \"path\": \"\"}]", "{\"a\": [1]}");
        // The from location must still exist.
        assertFails("{}", "[{\"op\": \"move\", \"from\": \"/a\", \"path\": \"/a\"}]",
                new PatchError.FromNotFound(0, new PointerError.MemberNotFound(JsonPointer.root(), "a")));
    }

    @Test
    @Requirement("rfc6902-4.4/move-errors")
    void moveNeedsAnExistingFromThatIsNotAPrefixOfPath() {
        assertFails("{\"a\": {\"b\": 1}}", "[{\"op\": \"move\", \"from\": \"/a\", \"path\": \"/a/c\"}]",
                new PatchError.MoveIntoChild(0));
        assertFails("{\"a\": 1}", "[{\"op\": \"move\", \"from\": \"\", \"path\": \"/b\"}]", new PatchError.MoveIntoChild(0));
        assertFails("{\"a\": 1}", "[{\"op\": \"move\", \"from\": \"/b\", \"path\": \"/c\"}]",
                new PatchError.FromNotFound(0, new PointerError.MemberNotFound(JsonPointer.root(), "b")));
        assertFails("{\"a\": 1}", "[{\"op\": \"move\", \"from\": \"/a\", \"path\": \"/x/y\"}]",
                new PatchError.PathNotFound(0, new PointerError.MemberNotFound(JsonPointer.root(), "x")));
        // "/ab" is not a child of "/a".
        assertPatched("{\"a\": 1}", "[{\"op\": \"move\", \"from\": \"/a\", \"path\": \"/ab\"}]", "{\"ab\": 1}");
    }

    @Test
    @Requirement("rfc6902-4.5/copy")
    void copyAddsADeepCopy() {
        JsonNode result = assertPatched("{\"a\": {\"b\": [1]}}", "[{\"op\": \"copy\", \"from\": \"/a\", \"path\": \"/c\"}]",
                "{\"a\": {\"b\": [1]}, \"c\": {\"b\": [1]}}");
        // The two locations do not share a node.
        assertNotSame(result.get("a"), result.get("c"));
        assertNotSame(result.get("a").get("b"), result.get("c").get("b"));
        assertPatched("[1, [2]]", "[{\"op\": \"copy\", \"from\": \"/1\", \"path\": \"/0\"}]", "[[2], 1, [2]]");
        assertPatched("{\"a\": 1}", "[{\"op\": \"copy\", \"from\": \"\", \"path\": \"/b\"}]", "{\"a\": 1, \"b\": {\"a\": 1}}");
        assertFails("{\"a\": 1}", "[{\"op\": \"copy\", \"from\": \"/b\", \"path\": \"/c\"}]",
                new PatchError.FromNotFound(0, new PointerError.MemberNotFound(JsonPointer.root(), "b")));
        assertFails("{\"a\": 1}", "[{\"op\": \"copy\", \"from\": \"/a\", \"path\": \"/x/y\"}]",
                new PatchError.PathNotFound(0, new PointerError.MemberNotFound(JsonPointer.root(), "x")));
    }

    @Test
    @Requirement("rfc6902-4.6/test")
    void testComparesJsonValues() {
        String document = "{\"s\": \"aé\", \"n\": 10, \"b\": 9007199254740993, \"t\": true, \"f\": false, \"z\": null,"
                + " \"a\": [1, {\"x\": 2, \"y\": 3}], \"o\": {\"p\": 1, \"q\": [2]}, \"~1\": 1}";
        for (String[] passes : new String[][] {
                {"/s", "\"a\\u00e9\""}, {"/n", "10"}, {"/n", "10.0"}, {"/n", "1e1"}, {"/b", "9007199254740993"},
                {"/t", "true"}, {"/f", "false"}, {"/z", "null"}, {"/a", "[1.0, {\"y\": 3, \"x\": 2}]"},
                {"/o", "{\"q\": [2], \"p\": 1}"}, {"/~01", "1"}, {"", document}}) {
            assertPatched(document, "[{\"op\": \"test\", \"path\": \"%s\", \"value\": %s}]".formatted(passes[0], passes[1]), document);
        }
        for (String[] fails : new String[][] {
                {"/s", "\"aé\""}, {"/n", "\"10\""}, {"/b", "9007199254740992"}, {"/t", "false"},
                {"/z", "false"}, {"/a", "[{\"x\": 2, \"y\": 3}, 1]"}, {"/a", "[1]"}, {"/o", "{\"p\": 1}"},
                {"/o", "{\"p\": 1, \"q\": [2], \"r\": 3}"}, {"/o", "{\"p\": 1, \"r\": [2]}"}}) {
            assertFails(document, "[{\"op\": \"test\", \"path\": \"%s\", \"value\": %s}]".formatted(fails[0], fails[1]),
                    new PatchError.TestFailed(0));
        }
        assertFails(document, "[{\"op\": \"test\", \"path\": \"/missing\", \"value\": 1}]",
                new PatchError.PathNotFound(0, new PointerError.MemberNotFound(JsonPointer.root(), "missing")));
    }

    @Test
    void patchesKeepTheirOperations() {
        JsonPatch<JsonNode> patch = patch("[{\"op\": \"remove\", \"path\": \"/a\"}]");
        assertEquals(List.of(new Operation.Remove<JsonNode>(pointer("/a"))), patch.operations());
        assertSame(MODEL, patch.model());
        JsonPatch<JsonNode> built = JsonPatch.of(List.of(new Operation.Add<>(pointer("/b"), Documents.jackson("1"))), MODEL);
        assertEquals(pointer("/b"), built.operations().getFirst().path());
        assertTrue(built.apply(Documents.jackson("{}"), MODEL).isOk());
    }
}
