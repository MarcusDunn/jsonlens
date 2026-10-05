package ca.marcusdunn.jsonlens.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@Requirement("lib/model-equal-nodes")
class EqualNodesTest {

    private static final JavaCollectionsModel MODEL = JavaCollectionsModel.INSTANCE;

    /// A node that a model makes again for each read. Two nodes for the same value are equal.
    private record Fresh(Object value) {}

    /// A model that makes a new node object each time it reads a value.
    private static final class FreshModel implements JsonModel<Fresh> {
        @Override
        public JsonKind kind(Fresh node) {
            return MODEL.kind(node.value());
        }

        @Override
        public int arrayLength(Fresh array) {
            return MODEL.arrayLength(array.value());
        }

        @Override
        public Maybe<Fresh> element(Fresh array, int index) {
            return MODEL.element(array.value(), index).map(Fresh::new);
        }

        @Override
        public MemberCursor<Fresh> memberCursor(Fresh object) {
            MemberCursor<Object> cursor = MODEL.memberCursor(object.value());
            return new MemberCursor<>() {
                @Override
                public boolean next() {
                    return cursor.next();
                }

                @Override
                public JsonString name() {
                    return cursor.name();
                }

                @Override
                public Fresh value() {
                    return new Fresh(cursor.value());
                }
            };
        }

        @Override
        public JsonString stringValue(Fresh string) {
            return MODEL.stringValue(string.value());
        }

        @Override
        public JsonNumber numberValue(Fresh number) {
            return MODEL.numberValue(number.value());
        }
    }

    private static final FreshModel FRESH = new FreshModel();
    private static final Fresh ROOT = new Fresh(Map.of("a", List.of(1, "x"), "b", Map.of("c", true)));

    @Test
    void twoReadsGiveEqualNodesThatAreNotTheSameObject() {
        Fresh first = FRESH.member(ROOT, JsonString.of("a")).orElse(ROOT);
        Fresh second = FRESH.member(ROOT, JsonString.of("a")).orElse(ROOT);
        assertNotSame(first, second);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void theSharedOperationsDoNotCompareNodesByReference() {
        assertTrue(FRESH.equal(ROOT, new Fresh(Map.of("b", Map.of("c", true), "a", List.of(1.0, "x")))));
        assertFalse(FRESH.equal(ROOT, new Fresh(Map.of("a", List.of(1, "y"), "b", Map.of("c", true)))));
        assertTrue(JsonModel.equal(FRESH, ROOT, MODEL, ROOT.value()));
        assertFalse(FRESH.hasDuplicate(ROOT, JsonString.of("a")));
        assertTrue(MODEL.equal(ROOT.value(), MODEL.copyOf(FRESH, ROOT).orElse("none")));
    }
}
