package ca.marcusdunn.jsonlens.patch;

import static ca.marcusdunn.jsonlens.patch.OperationsTest.patch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import ca.marcusdunn.jsonlens.model.Property;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.pointer.PointerError;
import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;

class CopiesTest {

    /// The Java collections model, with a count of the nodes that its factory builds. It can also
    /// refuse all numbers, as a representation with a limit does.
    private static final class CountingModel implements JsonModel<Object>, JsonFactory<Object>, JsonEditor<Object> {
        private static final JavaCollectionsModel MODEL = JavaCollectionsModel.INSTANCE;
        private final boolean numbers;
        private final String duplicate;
        private int built;

        CountingModel(boolean numbers) {
            this(numbers, "");
        }

        /// A model that also reports one member name as a duplicate in each object.
        CountingModel(boolean numbers, String duplicate) {
            this.numbers = numbers;
            this.duplicate = duplicate;
        }

        @Override
        public boolean hasDuplicate(Object object, JsonString name) {
            return !duplicate.isEmpty() && JsonString.equal(name, JsonString.of(duplicate));
        }

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
            built++;
            return MODEL.string(value);
        }

        @Override
        public Maybe<Object> number(JsonNumber value) {
            built++;
            return numbers ? MODEL.number(value) : Maybe.none();
        }

        @Override
        public Object bool(boolean value) {
            built++;
            return MODEL.bool(value);
        }

        @Override
        public Object nullValue() {
            built++;
            return MODEL.nullValue();
        }

        @Override
        public Object array(List<Object> elements) {
            built++;
            return MODEL.array(elements);
        }

        @Override
        public Object object(List<Property<Object>> members) {
            built++;
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

    private static Object document(String json) {
        return JavaCollectionsModel.INSTANCE.copyOf(JacksonJsonModel.INSTANCE, Documents.jackson(json)).orElse("none");
    }

    @Test
    @Requirement("lib/patch-minimal-copies")
    void eachOperationCopiesOnlyWhatItMust() {
        CountingModel model = new CountingModel(true);
        Object target = document("{\"a\": {\"b\": [1, \"x\"]}, \"c\": []}");
        Object moved = OperationsTest.pointer("/a/b").resolve(target, model).orElse("none");

        // move and test: no node is built.
        Object result = patch("""
                [{"op": "move", "from": "/a/b", "path": "/c/0"},
                 {"op": "test", "path": "/c", "value": [[1, "x"]]}]""").apply(target, model).orElse("none");
        assertEquals(0, model.built);
        assertSame(moved, OperationsTest.pointer("/c/0").resolve(result, model).orElse("none"));

        // add and replace: one copy of the value, one node for each value node.
        patch("[{\"op\": \"add\", \"path\": \"/d\", \"value\": [true, null]}]").apply(result, model);
        assertEquals(3, model.built);
        patch("[{\"op\": \"replace\", \"path\": \"/d\", \"value\": \"s\"}]").apply(result, model);
        assertEquals(4, model.built);

        // copy: one deep copy of the source.
        patch("[{\"op\": \"copy\", \"from\": \"/c\", \"path\": \"/e\"}]").apply(result, model);
        assertEquals(8, model.built);

        // remove: no node is built.
        patch("[{\"op\": \"remove\", \"path\": \"/e\"}]").apply(result, model);
        assertEquals(8, model.built);
    }

    @Test
    @Requirement("rfc6902-5/errors")
    void aNumberThatTheTargetCannotHoldIsAnError() {
        CountingModel model = new CountingModel(false);
        String json = "{\"n\": 1, \"a\": [2]}";
        for (String operation : List.of(
                "{\"op\": \"add\", \"path\": \"/x\", \"value\": [3]}",
                "{\"op\": \"replace\", \"path\": \"/n\", \"value\": 4}",
                "{\"op\": \"copy\", \"from\": \"/a\", \"path\": \"/b\"}")) {
            Object target = document(json);
            Result<Object, PatchError> result = patch("[{\"op\": \"add\", \"path\": \"/z\", \"value\": \"z\"}, " + operation + "]")
                    .apply(target, model);
            assertEquals(Result.err(new PatchError.ValueNotRepresentable(1)), result);
            assertTrue(JsonModel.equal(model, target, JacksonJsonModel.INSTANCE, Documents.jackson(json)));
        }
        // Without numbers, other values still work.
        Object target = document(json);
        assertTrue(patch("[{\"op\": \"add\", \"path\": \"/x\", \"value\": [\"s\", true]}]").apply(target, model).isOk());
    }
    @Test
    @Requirement("rfc6901-4/duplicate-names")
    void aTargetNameThatIsNotUniqueIsAnError() {
        CountingModel model = new CountingModel(true, "dup");
        String json = "{\"dup\": 1, \"o\": {\"dup\": 2}}";
        for (String operation : List.of(
                "{\"op\": \"add\", \"path\": \"/o/dup\", \"value\": 3}",
                "{\"op\": \"replace\", \"path\": \"/o/dup\", \"value\": 3}",
                "{\"op\": \"remove\", \"path\": \"/o/dup\"}")) {
            Object target = document(json);
            assertEquals(Result.err(new PatchError.PathNotFound(0, new PointerError.DuplicateName(OperationsTest.pointer("/o"), "dup"))),
                    patch("[" + operation + "]").apply(target, model));
        }
        assertTrue(patch("[{\"op\": \"add\", \"path\": \"/o/x\", \"value\": 3}]").apply(document(json), model).isOk());
    }
    @Test
    @Requirement("lib/patch-copy")
    void applyToCopyBuildsOnlyTheContainersOnThePaths() {
        CountingModel model = new CountingModel(true);
        String json = "{\"a\": {\"b\": [1, \"x\"]}, \"c\": [], \"big\": {\"k\": [1, 2, 3]}}";
        Object document = document(json);
        Object big = OperationsTest.pointer("/big").resolve(document, model).orElse("none");

        // move: the removal builds "/a" and the root; the insertion builds "/c" and the root.
        Object result = patch("""
                [{"op": "move", "from": "/a/b", "path": "/c/0"},
                 {"op": "test", "path": "/c", "value": [[1, "x"]]}]""").applyToCopy(document, model).orElse("none");
        assertEquals(4, model.built);
        // The parts off the paths are shared, and the document did not change.
        assertSame(big, OperationsTest.pointer("/big").resolve(result, model).orElse("none"));
        assertTrue(JsonModel.equal(model, document, JacksonJsonModel.INSTANCE, Documents.jackson(json)));

        // copy shares its source: it builds only the containers on the path.
        Object copied = patch("[{\"op\": \"copy\", \"from\": \"/big\", \"path\": \"/c/-\"}]").applyToCopy(result, model).orElse("none");
        assertEquals(6, model.built);
        assertSame(big, OperationsTest.pointer("/c/1").resolve(copied, model).orElse("none"));

        // remove and replace build the containers on their paths, and replace copies its value once.
        patch("[{\"op\": \"remove\", \"path\": \"/big/k/0\"}]").applyToCopy(copied, model);
        assertEquals(9, model.built);
        patch("[{\"op\": \"replace\", \"path\": \"/big/k\", \"value\": \"s\"}]").applyToCopy(copied, model);
        assertEquals(12, model.built);
        patch("[{\"op\": \"replace\", \"path\": \"/c/0\", \"value\": true}]").applyToCopy(copied, model);
        assertEquals(15, model.built);
    }
}
