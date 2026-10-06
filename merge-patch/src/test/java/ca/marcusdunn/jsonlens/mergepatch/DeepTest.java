package ca.marcusdunn.jsonlens.mergepatch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@Requirement("lib/merge-patch-deep-nesting")
class DeepTest {

    private static final JavaCollectionsModel MODEL = JavaCollectionsModel.INSTANCE;
    private static final int DEPTH = 100_000;

    /// Objects `{"a": {"a": ... leaf}}`, with a new leaf, at a depth.
    private static Object nested(List<Object> leafMembers) {
        Map<String, Object> leaf = new LinkedHashMap<>();
        for (int i = 0; i < leafMembers.size(); i += 2) {
            leaf.put((String) leafMembers.get(i), leafMembers.get(i + 1));
        }
        Object value = leaf;
        for (int i = 0; i < DEPTH; i++) {
            Map<String, Object> parent = new LinkedHashMap<>();
            parent.put("a", value);
            value = parent;
        }
        return value;
    }

    /// Compares without recursion. The `equals` method of a `Map` uses recursion.
    private static void assertEqual(Object expected, Object actual) {
        assertTrue(MODEL.equal(expected, actual));
    }

    @Test
    void deepDocumentsDoNotOverflowTheStack() {
        JsonMergePatch<Object> patch = Documents.valid(JsonMergePatch.parse(
                nested(List.of("v", 2, "n", JavaCollectionsModel.NULL, "o", nested(List.of("x", JavaCollectionsModel.NULL)))), MODEL));
        Object expected = nested(List.of("v", 2, "o", nested(List.of())));

        Object target = nested(List.of("v", 1, "n", true));
        assertEqual(expected, Documents.success(patch.applyToCopy(target, MODEL)));
        assertEqual(nested(List.of("v", 1, "n", true)), target);
        assertEqual(expected, Documents.success(patch.apply(target, MODEL)));
        assertEqual(expected, target);

        // A target that is not an object: the merge copies the patch without its null members.
        assertEqual(expected, Documents.success(patch.apply("s", MODEL)));
        assertEqual(expected, Documents.success(patch.applyToCopy("s", MODEL)));
    }
}
