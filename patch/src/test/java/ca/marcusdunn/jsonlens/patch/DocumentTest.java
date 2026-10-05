package ca.marcusdunn.jsonlens.patch;

import static ca.marcusdunn.jsonlens.patch.OperationsTest.assertPatched;
import static ca.marcusdunn.jsonlens.patch.OperationsTest.pointer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.kotlinx.KotlinxJsonModel;
import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.pointer.PointerError;
import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class DocumentTest {

    private static PatchError error(String patch) {
        return JsonPatch.parse(Documents.jackson(patch), JacksonJsonModel.INSTANCE)
                .fold(value -> new PatchError.NotAnArray(), error -> error);
    }

    private static <P> JsonPatch<P> valid(Result<JsonPatch<P>, PatchError> result) {
        return result.fold(patch -> patch, error -> org.junit.jupiter.api.Assertions.fail(error.message()));
    }

    private static PatchError mappedError(String patch) {
        MappedJson json = Documents.mapped(patch);
        return JsonPatch.parse(json.root(), json.model()).fold(value -> new PatchError.NotAnArray(), error -> error);
    }

    @Test
    @Requirement("rfc6902-3/document")
    void aPatchIsAnArrayOfObjects() {
        assertEquals(new PatchError.NotAnArray(), error("{\"op\": \"remove\", \"path\": \"/a\"}"));
        assertEquals(new PatchError.NotAnArray(), error("\"[]\""));
        assertEquals(new PatchError.NotAnObject(1), error("[{\"op\": \"remove\", \"path\": \"/a\"}, [1]]"));
        assertEquals(new PatchError.NotAnObject(0), error("[\"remove\"]"));
        assertTrue(JsonPatch.parse(Documents.jackson("[]"), JacksonJsonModel.INSTANCE).isOk());
    }

    @Test
    @Requirement("rfc6902-4/op-member")
    void eachOperationHasExactlyOneKnownOp() {
        assertEquals(new PatchError.MissingMember(0, "op"), error("[{\"path\": \"/a\"}]"));
        assertEquals(new PatchError.NotAString(0, "op"), error("[{\"op\": 1, \"path\": \"/a\"}]"));
        assertEquals(new PatchError.NotAString(0, "op"), error("[{\"op\": null, \"path\": \"/a\"}]"));
        assertEquals(new PatchError.UnknownOperation(0, "spam"), error("[{\"op\": \"spam\", \"path\": \"/a\"}]"));
        assertEquals(new PatchError.UnknownOperation(0, "Add"), error("[{\"op\": \"Add\", \"path\": \"/a\", \"value\": 1}]"));
        // Appendix A.13: two "op" members. A reader that keeps duplicate names finds them.
        assertEquals(new PatchError.DuplicateMember(0, "op"),
                mappedError("[{\"op\": \"add\", \"path\": \"/baz\", \"value\": \"qux\", \"op\": \"remove\"}]"));
    }

    @Test
    @Requirement("rfc6902-4/path-member")
    void eachOperationHasExactlyOnePathThatIsAPointer() {
        assertEquals(new PatchError.MissingMember(0, "path"), error("[{\"op\": \"add\", \"value\": 1}]"));
        assertEquals(new PatchError.NotAString(0, "path"), error("[{\"op\": \"add\", \"path\": null, \"value\": 1}]"));
        assertEquals(new PatchError.InvalidPointer(0, "path", new PointerError.MissingSlash()),
                error("[{\"op\": \"add\", \"path\": \"foo\", \"value\": 1}]"));
        assertEquals(new PatchError.InvalidPointer(0, "path", new PointerError.InvalidEscape(2)),
                error("[{\"op\": \"remove\", \"path\": \"/a~2\"}]"));
        assertEquals(new PatchError.DuplicateMember(0, "path"),
                mappedError("[{\"op\": \"remove\", \"path\": \"/a\", \"path\": \"/b\"}]"));
    }

    @Test
    @Requirement("rfc6902-4/operation-members")
    void eachOperationHasTheMembersThatItNeeds() {
        for (String op : List.of("add", "replace", "test")) {
            assertEquals(new PatchError.MissingMember(0, "value"), error("[{\"op\": \"%s\", \"path\": \"/a\"}]".formatted(op)));
            assertEquals(new PatchError.DuplicateMember(0, "value"),
                    mappedError("[{\"op\": \"%s\", \"path\": \"/a\", \"value\": 1, \"value\": 2}]".formatted(op)));
        }
        for (String op : List.of("move", "copy")) {
            assertEquals(new PatchError.MissingMember(0, "from"), error("[{\"op\": \"%s\", \"path\": \"/a\"}]".formatted(op)));
            assertEquals(new PatchError.NotAString(0, "from"),
                    error("[{\"op\": \"%s\", \"path\": \"/a\", \"from\": 1}]".formatted(op)));
            assertEquals(new PatchError.InvalidPointer(0, "from", new PointerError.MissingSlash()),
                    error("[{\"op\": \"%s\", \"path\": \"/a\", \"from\": \"a\"}]".formatted(op)));
        }
        // A value can be null: null is a value, not a missing member.
        assertPatched("{}", "[{\"op\": \"add\", \"path\": \"/a\", \"value\": null}]", "{\"a\": null}");
    }

    @Test
    @Requirement("rfc6902-4/ignore-members")
    void membersThatAnOperationDoesNotDefineAreIgnored() {
        assertPatched("{\"a\": 1}", """
                [{"op": "remove", "path": "/a", "value": 9, "from": 7, "x": [1]},
                 {"op": "add", "path": "/b", "value": 2, "from": "not a pointer"},
                 {"op": "test", "path": "/b", "value": 2, "op2": "remove"}]""", "{\"b\": 2}");
        // Appendix A.11.
        assertPatched("{\"foo\": \"bar\"}", "[{\"op\": \"add\", \"path\": \"/baz\", \"value\": \"qux\", \"xyz\": 123}]",
                "{\"foo\": \"bar\", \"baz\": \"qux\"}");
        // Duplicates of a member that the operation does not use are ignored too.
        MappedJson json = Documents.mapped("[{\"op\": \"remove\", \"path\": \"/a\", \"value\": 1, \"value\": 2}]");
        assertTrue(JsonPatch.parse(json.root(), json.model()).isOk());
    }

    @Test
    @Requirement("lib/patch-model-agnostic")
    void patchDocumentsAndTargetsCanBeOfAnyModel() {
        String patch = "[{\"op\": \"add\", \"path\": \"/a/-\", \"value\": {\"b\": [1.5, \"x\"]}},"
                + " {\"op\": \"test\", \"path\": \"/a/0/b/0\", \"value\": 1.50}]";
        JsonNode expected = Documents.jackson("{\"a\": [{\"b\": [1.5, \"x\"]}]}");
        MappedJson mapped = Documents.mapped(patch);
        List<JsonPatch<?>> patches = List.of(
                valid(JsonPatch.parse(Documents.jackson(patch), JacksonJsonModel.INSTANCE)),
                valid(JsonPatch.parse(Documents.kotlinx(patch), KotlinxJsonModel.INSTANCE)),
                valid(JsonPatch.parse(mapped.root(), mapped.model())));
        for (JsonPatch<?> each : patches) {
            JsonNode jackson = Documents.jackson("{\"a\": []}");
            Result<JsonNode, PatchError> onJackson = each.apply(jackson, JacksonJsonModel.INSTANCE);
            assertTrue(JacksonJsonModel.INSTANCE.equal(expected, onJackson.orElse(jackson)));
            Object collections = JavaCollectionsModel.INSTANCE.copyOf(JacksonJsonModel.INSTANCE, Documents.jackson("{\"a\": []}"))
                    .orElse("none");
            Result<Object, PatchError> onCollections = each.apply(collections, JavaCollectionsModel.INSTANCE);
            assertTrue(ca.marcusdunn.jsonlens.model.JsonModel.equal(JavaCollectionsModel.INSTANCE, onCollections.orElse("none"),
                    JacksonJsonModel.INSTANCE, expected));
        }
        assertEquals(pointer("/a/-"), patches.getFirst().operations().getFirst().path());
    }

    @Test
    @Requirement("rfc6902-5/errors")
    void errorsDescribeTheirCause() {
        PointerError missing = new PointerError.MemberNotFound(pointer("/a"), "b");
        List<String> messages = List.of(
                new PatchError.NotAnArray().message(),
                new PatchError.NotAnObject(1).message(),
                new PatchError.MissingMember(1, "op").message(),
                new PatchError.DuplicateMember(1, "op").message(),
                new PatchError.NotAString(1, "path").message(),
                new PatchError.UnknownOperation(1, "spam").message(),
                new PatchError.InvalidPointer(1, "from", new PointerError.MissingSlash()).message(),
                new PatchError.PathNotFound(1, missing).message(),
                new PatchError.FromNotFound(1, missing).message(),
                new PatchError.RootRemoved(1).message(),
                new PatchError.MoveIntoChild(1).message(),
                new PatchError.TestFailed(1).message(),
                new PatchError.ValueNotRepresentable(1).message());
        assertEquals(List.of(
                "A JSON Patch document must be an array of operations.",
                "Operation 1 is not an object.",
                "Operation 1 has no 'op' member.",
                "Operation 1 has more than one 'op' member.",
                "The 'path' member of operation 1 is not a string.",
                "Operation 1 has the unknown op 'spam'.",
                "The 'from' member of operation 1 is not a JSON Pointer: A JSON Pointer that is not empty must start with '/'.",
                "The path of operation 1 does not exist: The object at '/a' has no member 'b'.",
                "The from location of operation 1 does not exist: The object at '/a' has no member 'b'.",
                "Operation 1 removes the whole document.",
                "Operation 1 moves a value into one of its children.",
                "The test of operation 1 failed: the values are not equal.",
                "The value of operation 1 cannot be added to the target document: a number is too large for it, or an object has duplicate member names."), messages);
    }
}
