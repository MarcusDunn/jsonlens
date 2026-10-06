package ca.marcusdunn.jsonlens.mergepatch;

import static ca.marcusdunn.jsonlens.mergepatch.Documents.assertJson;
import static ca.marcusdunn.jsonlens.mergepatch.Documents.collections;
import static ca.marcusdunn.jsonlens.mergepatch.Documents.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CopiesTest {

    private static final JavaCollectionsModel MODEL = JavaCollectionsModel.INSTANCE;

    private static Object member(Object object, String name) {
        return MODEL.member(object, JsonString.of(name)).orElse("none");
    }

    private static JsonMergePatch<Object> patch(String json) {
        return Documents.valid(JsonMergePatch.parse(collections(json), MODEL));
    }

    private static List<Object> names(Object object) {
        return new ArrayList<>(((Map<?, ?>) object).keySet());
    }

    @Test
    @Requirement("lib/merge-patch-minimal-copies")
    void applyCopiesEachValueOnceAndBuildsNoOtherNode() {
        CountingModel model = new CountingModel();
        Object target = collections("{\"a\": {\"b\": 1, \"c\": 2, \"k\": {}}, \"e\": 3, \"keep\": {\"x\": [1]}}");
        Object a = member(target, "a");
        Object keep = member(target, "keep");
        JsonMergePatch<Object> patch =
                patch("{\"a\": {\"b\": \"x\", \"c\": null, \"k\": {\"z\": null}}, \"d\": [1, true], \"e\": null, \"n\": {\"m\": false, \"z\": null}}");
        Object result = success(patch.apply(target, model));
        assertJson("{\"a\": {\"b\": \"x\", \"k\": {}}, \"keep\": {\"x\": [1]}, \"d\": [1, true], \"n\": {\"m\": false}}", model, result);
        // "x": one node. [1, true]: three nodes. {"m": false}: two nodes.
        assertEquals(6, model.built);
        // Two removals ("c" and "e") and three puts ("b", "d", and "n").
        assertEquals(5, model.edits);
        assertSame(target, result);
        assertSame(a, member(result, "a"));
        assertSame(keep, member(result, "keep"));
        // The result shares no node with the patch document.
        assertNotSame(member(patch.document(), "d"), member(result, "d"));

        // A target that is not an object: one new object for each object of the patch.
        model.built = 0;
        model.edits = 0;
        assertJson("{\"n\": {}}", model, success(patch("{\"n\": {\"z\": null}}").apply(collections("[1]"), model)));
        assertEquals(2, model.built);
        assertEquals(0, model.edits);
    }

    @Test
    @Requirement("lib/merge-patch-copy")
    void applyToCopyBuildsOnlyTheObjectsThatChange() {
        CountingModel model = new CountingModel();
        String json = "{\"a\": {\"b\": {\"c\": 1}, \"d\": {\"e\": 2}}, \"keep\": {\"x\": [1]}}";
        Object target = collections(json);
        Object result = success(patch("{\"a\": {\"b\": {\"c\": 5}, \"d\": {\"zz\": null}}}").applyToCopy(target, model));
        assertJson("{\"a\": {\"b\": {\"c\": 5}, \"d\": {\"e\": 2}}, \"keep\": {\"x\": [1]}}", model, result);
        // The number, and the objects "/a/b", "/a", and the root.
        assertEquals(4, model.built);
        assertEquals(0, model.edits);
        assertJson(json, model, target);
        assertSame(member(target, "keep"), member(result, "keep"));
        assertSame(member(member(target, "a"), "d"), member(member(result, "a"), "d"));

        // A removal also builds the objects on its path.
        model.built = 0;
        result = success(patch("{\"a\": {\"d\": {\"e\": null}}}").applyToCopy(target, model));
        assertJson("{\"a\": {\"b\": {\"c\": 1}, \"d\": {}}, \"keep\": {\"x\": [1]}}", model, result);
        assertEquals(3, model.built);
        assertSame(member(member(target, "a"), "b"), member(member(result, "a"), "b"));

        // An object patch that changes nothing gives the target itself, and builds no node.
        model.built = 0;
        for (String nothing : List.of("{}", "{\"zz\": null}", "{\"a\": {\"d\": {}}}", "{\"a\": {\"b\": {\"q\": null}}}")) {
            assertSame(target, success(patch(nothing).applyToCopy(target, model)));
        }
        assertEquals(0, model.built);
        assertJson(json, model, target);
    }

    @Test
    @Requirement("lib/merge-patch-copy")
    void applyToCopyKeepsThePositionsOfTheMembers() {
        Object target = collections("{\"x\": 1, \"y\": 2, \"z\": 3}");
        Object result = success(patch("{\"w\": 0, \"y\": 9, \"x\": null, \"v\": 8}").applyToCopy(target, MODEL));
        // A replaced member keeps its position. New members come last, in the order of the patch.
        assertEquals(List.of("y", "z", "w", "v"), names(result));
        assertEquals(List.of("x", "y", "z"), names(target));
    }
}
