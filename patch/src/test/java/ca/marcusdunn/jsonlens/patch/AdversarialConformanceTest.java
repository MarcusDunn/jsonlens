package ca.marcusdunn.jsonlens.patch;

import static ca.marcusdunn.jsonlens.patch.OperationsTest.patch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.mapped.MappedNode;
import ca.marcusdunn.jsonlens.model.JsonEditor;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Property;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.pointer.JsonPointer;
import ca.marcusdunn.jsonlens.pointer.PointerError;
import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/// Tests that try to find places where the patch does not conform to RFC 6902, or breaks the
/// promises of the library.
class AdversarialConformanceTest {

    /// The Java collections model. It records if the patch calls [#object(List)] with a member
    /// name more than once, which the contract of [JsonFactory] forbids.
    private static final class StrictFactoryModel implements JsonModel<Object>, JsonFactory<Object>, JsonEditor<Object> {
        private static final JavaCollectionsModel MODEL = JavaCollectionsModel.INSTANCE;
        private boolean duplicateNames;

        @Override
        public JsonKind kind(Object node) {
            return MODEL.kind(node);
        }

        @Override
        public int arrayLength(Object array) {
            return MODEL.arrayLength(array);
        }

        @Override
        public Maybe<Object> element(Object array, int index) {
            return MODEL.element(array, index);
        }

        @Override
        public int memberCount(Object object) {
            return MODEL.memberCount(object);
        }

        @Override
        public Maybe<Object> member(Object object, JsonString name) {
            return MODEL.member(object, name);
        }

        @Override
        public MemberCursor<Object> memberCursor(Object object) {
            return MODEL.memberCursor(object);
        }

        @Override
        public JsonString stringValue(Object string) {
            return MODEL.stringValue(string);
        }

        @Override
        public JsonNumber numberValue(Object number) {
            return MODEL.numberValue(number);
        }

        @Override
        public Object string(JsonString value) {
            return MODEL.string(value);
        }

        @Override
        public Maybe<Object> number(JsonNumber value) {
            return MODEL.number(value);
        }

        @Override
        public Object bool(boolean value) {
            return MODEL.bool(value);
        }

        @Override
        public Object nullValue() {
            return MODEL.nullValue();
        }

        @Override
        public Object array(List<Object> elements) {
            return MODEL.array(elements);
        }

        @Override
        public Object object(List<Property<Object>> members) {
            Set<String> names = new HashSet<>();
            for (Property<Object> member : members) {
                if (!names.add(JsonString.copyOf(member.name()))) {
                    duplicateNames = true;
                }
            }
            return MODEL.object(members);
        }

        @Override
        public Maybe<Object> putMember(Object object, JsonString name, Object value) {
            return MODEL.putMember(object, name, value);
        }

        @Override
        public Maybe<Object> removeMember(Object object, JsonString name) {
            return MODEL.removeMember(object, name);
        }

        @Override
        public void insertElement(Object array, int index, Object value) {
            MODEL.insertElement(array, index, value);
        }

        @Override
        public Object setElement(Object array, int index, Object value) {
            return MODEL.setElement(array, index, value);
        }

        @Override
        public Object removeElement(Object array, int index) {
            return MODEL.removeElement(array, index);
        }
    }

    private static JsonPatch<MappedNode> mappedPatch(String json) {
        MappedJson document = Documents.mapped(json);
        return switch (JsonPatch.parse(document.root(), document.model())) {
            case Result.Ok<JsonPatch<MappedNode>, PatchError>(JsonPatch<MappedNode> value) -> value;
            case Result.Err<JsonPatch<MappedNode>, PatchError>(PatchError error) -> fail(error.message());
        };
    }

    private static Object collections(String json) {
        return JavaCollectionsModel.INSTANCE.copyOf(JacksonJsonModel.INSTANCE, Documents.jackson(json)).orElse("none");
    }

    @Test
    @Requirement("lib/patch-model-agnostic")
    void aValueWithDuplicateNamesIsNotGivenToTheFactory() {
        // RFC 6902, Section 4.1: "The operation object MUST contain a "value" member whose content
        // specifies the value to be added." RFC 8259, Section 4: "When the names within an object
        // are not unique, the behavior of software that receives such an object is
        // unpredictable." The value {"a": 1, "a": 2} has no defined content.
        // The contract of JsonFactory: "jsonlens calls object(List) only with unique member
        // names." So the patch must not give such a value to the factory: either it fails, or it
        // does not call object(List) with a duplicate name.
        StrictFactoryModel model = new StrictFactoryModel();
        for (String operation : List.of("add", "replace")) {
            Object target = collections("{\"x\": 0}");
            mappedPatch("[{\"op\": \"" + operation + "\", \"path\": \"/x\", \"value\": {\"a\": 1, \"a\": 2}}]")
                    .apply(target, model);
            assertFalse(model.duplicateNames, operation + " gave duplicate names to JsonFactory.object");
        }
    }

    @Test
    @Requirement("rfc6902-5/atomic")
    void aFailedPatchAfterAMoveToTheRootRestoresTheDocument() {
        // RFC 6902, Section 5: "If a normative requirement is violated by a JSON Patch document,
        // or if an operation is not successful, evaluation of the JSON Patch document SHOULD
        // terminate and application of the entire patch document SHALL NOT be deemed successful."
        // The library promises that the document then has its original value.
        String json = "{\"a\": {\"b\": [1, 2]}, \"c\": 3}";
        String failing = """
                [{"op": "move", "from": "/a", "path": ""},
                 {"op": "add", "path": "/b/-", "value": 9},
                 {"op": "move", "from": "/b/0", "path": "/z"},
                 {"op": "copy", "from": "", "path": "/self"},
                 {"op": "test", "path": "/c", "value": 3}]""";
        JsonNode jackson = Documents.jackson(json);
        assertEquals(Result.err(new PatchError.PathNotFound(4,
                        new PointerError.MemberNotFound(JsonPointer.root(), "c"))),
                patch(failing).apply(jackson, JacksonJsonModel.INSTANCE));
        assertTrue(JacksonJsonModel.INSTANCE.equal(Documents.jackson(json), jackson), jackson::toString);
        Object collections = collections(json);
        assertTrue(patch(failing).apply(collections, JavaCollectionsModel.INSTANCE).isErr());
        assertTrue(JsonModel.equal(JavaCollectionsModel.INSTANCE, collections, JacksonJsonModel.INSTANCE, Documents.jackson(json)),
                collections::toString);
    }

    @Test
    @Requirement("rfc6902-4.6/test")
    void testComparesStringsAndNumbersAcrossModels() {
        // RFC 6902, Section 4.6: "strings: are considered equal if they contain the same number
        // of Unicode characters and their code points are byte-by-byte equal." "numbers: are
        // considered equal if their values are numerically equal."
        JsonNode target = Documents.jackson("{\"s\": \"\\ud800x\\ud83d\\ude00\", \"n\": 1e400, \"z\": -0.0, \"i\": 100}");
        assertTrue(mappedPatch("""
                [{"op": "test", "path": "/s", "value": "\\ud800x\\ud83d\\ude00"},
                 {"op": "test", "path": "/n", "value": 10e399},
                 {"op": "test", "path": "/z", "value": 0},
                 {"op": "test", "path": "/i", "value": 1.00e2}]""")
                .apply(target, JacksonJsonModel.INSTANCE).isOk());
        assertEquals(Result.err(new PatchError.TestFailed(0)), mappedPatch("""
                [{"op": "test", "path": "/s", "value": "\\ud800x\\ud83d"}]""").apply(target, JacksonJsonModel.INSTANCE));
    }
}
