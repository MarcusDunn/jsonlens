package ca.marcusdunn.jsonlens.patch;

import static ca.marcusdunn.jsonlens.patch.OperationsTest.patch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.model.JsonEditor;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

@Requirement("rfc6902-5/atomic")
class AtomicityTest {

    private static final String DOCUMENT = "{\"o\": {\"a\": 1, \"b\": [1, 2, 3]}, \"keep\": {\"k\": [true]}}";

    /// Each kind of change, and then a test that fails.
    private static final List<String> CHANGES = List.of(
            "{\"op\": \"add\", \"path\": \"/o/new\", \"value\": {\"n\": 1}}",
            "{\"op\": \"add\", \"path\": \"/o/a\", \"value\": 2}",
            "{\"op\": \"add\", \"path\": \"/o/b/1\", \"value\": 9}",
            "{\"op\": \"replace\", \"path\": \"/o/b/0\", \"value\": 8}",
            "{\"op\": \"replace\", \"path\": \"/o/a\", \"value\": 3}",
            "{\"op\": \"remove\", \"path\": \"/o/b/2\"}",
            "{\"op\": \"remove\", \"path\": \"/keep\"}",
            "{\"op\": \"move\", \"from\": \"/o/b/0\", \"path\": \"/o/moved\"}",
            "{\"op\": \"copy\", \"from\": \"/o\", \"path\": \"/copy\"}",
            "{\"op\": \"replace\", \"path\": \"\", \"value\": [\"root\"]}",
            "{\"op\": \"add\", \"path\": \"/-\", \"value\": 1}");

    private static <N, M extends JsonModel<N> & JsonFactory<N> & JsonEditor<N>> void assertAtomic(M model, N document) {
        JsonNode original = Documents.jackson(DOCUMENT);
        for (int count = 1; count <= CHANGES.size(); count++) {
            String patch = "[" + String.join(", ", CHANGES.subList(0, count)) + ", {\"op\": \"test\", \"path\": \"\", \"value\": 0}]";
            Result<N, PatchError> result = patch(patch).apply(document, model);
            assertEquals(Result.err(new PatchError.TestFailed(count)), result);
            assertTrue(JsonModel.equal(model, document, JacksonJsonModel.INSTANCE, original),
                    "after " + count + " changes: " + document);
        }
    }

    @Test
    void aFailedPatchRestoresAJacksonDocument() {
        JsonNode document = Documents.jackson(DOCUMENT);
        JsonNode keep = document.get("keep");
        JsonNode inner = document.get("o").get("b");
        assertAtomic(JacksonJsonModel.INSTANCE, document);
        // The restored document has the original nodes, not copies.
        assertSame(keep, document.get("keep"));
        assertSame(inner, document.get("o").get("b"));
    }

    @Test
    void aFailedPatchRestoresACollectionsDocument() {
        Object document = JavaCollectionsModel.INSTANCE.copyOf(JacksonJsonModel.INSTANCE, Documents.jackson(DOCUMENT)).orElse("none");
        assertAtomic(JavaCollectionsModel.INSTANCE, document);
    }

    @Test
    void anErrorInTheDocumentChangesNothing() {
        JsonNode document = Documents.jackson(DOCUMENT);
        // Appendix A.9 and the example of Section 5.
        assertEquals(Result.err(new PatchError.TestFailed(1)), patch("""
                [{"op": "replace", "path": "/o/a", "value": 42},
                 {"op": "test", "path": "/o/a", "value": "C"}]""").apply(document, JacksonJsonModel.INSTANCE));
        assertTrue(JacksonJsonModel.INSTANCE.equal(Documents.jackson(DOCUMENT), document));
    }

    /// The Jackson model, but `kind` throws for the string "boom".
    private static final class ThrowingModel implements JsonModel<JsonNode> {
        @Override
        public JsonKind kind(JsonNode node) {
            if (node.isString() && node.asString().equals("boom")) {
                throw new IllegalStateException("boom");
            }
            return JacksonJsonModel.INSTANCE.kind(node);
        }

        @Override
        public int arrayLength(JsonNode array) {
            return JacksonJsonModel.INSTANCE.arrayLength(array);
        }

        @Override
        public Maybe<JsonNode> element(JsonNode array, int index) {
            return JacksonJsonModel.INSTANCE.element(array, index);
        }

        @Override
        public MemberCursor<JsonNode> memberCursor(JsonNode object) {
            return JacksonJsonModel.INSTANCE.memberCursor(object);
        }

        @Override
        public JsonString stringValue(JsonNode string) {
            return JacksonJsonModel.INSTANCE.stringValue(string);
        }

        @Override
        public JsonNumber numberValue(JsonNode number) {
            return JacksonJsonModel.INSTANCE.numberValue(number);
        }
    }

    @Test
    @Requirement("lib/patch-rollback-on-throw")
    void anExceptionFromAModelRestoresTheDocument() {
        JsonNode document = Documents.jackson(DOCUMENT);
        List<Operation<JsonNode>> operations = patch("""
                [{"op": "add", "path": "/o/new", "value": 1},
                 {"op": "replace", "path": "/o/a", "value": 5},
                 {"op": "remove", "path": "/keep"},
                 {"op": "add", "path": "/x", "value": "boom"}]""").operations();
        JsonPatch<JsonNode> throwing = JsonPatch.of(operations, new ThrowingModel());
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> throwing.apply(document, JacksonJsonModel.INSTANCE));
        assertEquals("boom", thrown.getMessage());
        assertEquals(Documents.jackson(DOCUMENT), document);
        assertThrows(IllegalStateException.class, () -> throwing.applyToCopy(document, JacksonJsonModel.INSTANCE));
        assertEquals(Documents.jackson(DOCUMENT), document);
    }
}
