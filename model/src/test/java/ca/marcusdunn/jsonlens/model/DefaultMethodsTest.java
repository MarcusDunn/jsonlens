package ca.marcusdunn.jsonlens.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/// The default methods for objects, on a model with only the six required methods.
@Requirement("lib/model-defaults")
class DefaultMethodsTest {

    private static final JavaCollectionsModel MODEL = JavaCollectionsModel.INSTANCE;

    /// An object as a list of name/value pairs, so that it can hold duplicate names.
    private record Pairs(List<Map.Entry<String, Object>> members) {}

    private static final class PairsModel implements JsonModel<Object> {
        @Override
        public JsonKind kind(Object node) {
            return node instanceof Pairs ? JsonKind.OBJECT : MODEL.kind(node);
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
        public MemberCursor<Object> memberCursor(Object object) {
            Iterator<Map.Entry<String, Object>> entries = ((Pairs) object).members().iterator();
            return new MemberCursor<>() {
                private Map.Entry<String, Object> current = Map.entry("", "");

                @Override
                public boolean next() {
                    if (!entries.hasNext()) {
                        return false;
                    }
                    current = entries.next();
                    return true;
                }

                @Override
                public JsonString name() {
                    return JsonString.of(current.getKey());
                }

                @Override
                public Object value() {
                    return current.getValue();
                }
            };
        }

        @Override
        public JsonString stringValue(Object string) {
            return MODEL.stringValue(string);
        }

        @Override
        public JsonNumber numberValue(Object number) {
            return MODEL.numberValue(number);
        }
    }

    private static final PairsModel PAIRS = new PairsModel();
    private static final Pairs EMPTY = new Pairs(List.of());
    private static final Pairs OBJECT = new Pairs(List.of(Map.entry("a", 1), Map.entry("b", 2), Map.entry("a", 3)));

    @Test
    void memberCountCountsEachMember() {
        assertEquals(0, PAIRS.memberCount(EMPTY));
        assertEquals(3, PAIRS.memberCount(OBJECT));
    }

    @Test
    void memberGivesTheFirstMemberWithTheName() {
        assertEquals(Maybe.some(1), PAIRS.member(OBJECT, JsonString.of("a")));
        assertEquals(Maybe.some(2), PAIRS.member(OBJECT, JsonString.of("b")));
        assertEquals(Maybe.none(), PAIRS.member(OBJECT, JsonString.of("c")));
        assertEquals(Maybe.none(), PAIRS.member(EMPTY, JsonString.of("a")));
    }

    @Test
    void membersGivesEachMemberInTheOrderOfTheCursor() {
        assertEquals(
                List.of(new Property<>(JsonString.of("a"), 1), new Property<>(JsonString.of("b"), 2),
                        new Property<>(JsonString.of("a"), 3)),
                PAIRS.members(OBJECT).toList());
        assertEquals(List.of(), PAIRS.members(EMPTY).toList());
    }

    @Test
    void aCursorOverPropertiesWalksThemInOrder() {
        MemberCursor<Object> cursor = MemberCursor.of(List.of(new Property<>(JsonString.of("x"), 1), new Property<>(JsonString.of("y"), 2)));
        assertTrue(cursor.next());
        assertEquals("x", JsonString.copyOf(cursor.name()));
        assertEquals(1, cursor.value());
        assertTrue(cursor.next());
        assertEquals("y", JsonString.copyOf(cursor.name()));
        assertEquals(2, cursor.value());
        assertFalse(cursor.next());
        assertFalse(cursor.next());
        assertFalse(MemberCursor.of(List.of()).next());
    }

    @Test
    void hasDuplicateFindsANameThatOccursTwoTimes() {
        assertTrue(PAIRS.hasDuplicate(OBJECT, JsonString.of("a")));
        assertFalse(PAIRS.hasDuplicate(OBJECT, JsonString.of("b")));
        assertFalse(PAIRS.hasDuplicate(OBJECT, JsonString.of("c")));
        assertFalse(PAIRS.hasDuplicate(EMPTY, JsonString.of("a")));
    }
}
