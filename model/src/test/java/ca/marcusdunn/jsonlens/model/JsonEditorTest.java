package ca.marcusdunn.jsonlens.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@Requirement("lib/model-editor")
class JsonEditorTest {

    private static final JavaCollectionsModel MODEL = JavaCollectionsModel.INSTANCE;

    @Test
    void changesAndReverseChangesOfTheReferenceModel() {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("a", new ArrayList<>(List.of(1, 2)));
        object.put("n", null);
        Object original = MODEL.copyOf(MODEL, object).orElse("none");

        assertEquals(Maybe.some(JavaCollectionsModel.NULL), MODEL.putMember(object, JsonString.of("n"), "x"));
        assertEquals(Maybe.none(), MODEL.putMember(object, JsonString.of("b"), true));
        Object array = object.get("a");
        MODEL.insertElement(array, 2, 3);
        assertEquals(1, MODEL.setElement(array, 0, 0));
        assertTrue(MODEL.equal(Map.of("a", List.of(0, 2, 3), "n", "x", "b", true), object));

        assertEquals(3, MODEL.removeElement(array, 2));
        MODEL.setElement(array, 0, 1);
        assertEquals(Maybe.some(true), MODEL.removeMember(object, JsonString.of("b")));
        assertEquals(Maybe.none(), MODEL.removeMember(object, JsonString.of("b")));
        MODEL.putMember(object, JsonString.of("n"), JavaCollectionsModel.NULL);
        assertTrue(MODEL.equal(original, object));
    }

    @Test
    void javaNullElementsAreJsonNull() {
        List<Object> array = new ArrayList<>(Arrays.asList(null, "x"));
        assertEquals(JavaCollectionsModel.NULL, MODEL.setElement(array, 0, 1));
        array.set(0, null);
        assertEquals(JavaCollectionsModel.NULL, MODEL.removeElement(array, 0));
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("k", null);
        assertEquals(Maybe.some(JavaCollectionsModel.NULL), MODEL.removeMember(object, JsonString.of("k")));
    }
}
