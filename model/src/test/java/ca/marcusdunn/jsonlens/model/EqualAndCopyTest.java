package ca.marcusdunn.jsonlens.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@Requirement("lib/model-equality-and-copy")
class EqualAndCopyTest {

    private static final JavaCollectionsModel MODEL = JavaCollectionsModel.INSTANCE;

    /// A model that is not well-formed: an array element that is the string "gap" is missing.
    private static final class GapModel implements JsonModel<Object> {
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
            Maybe<Object> element = MODEL.element(array, index);
            return element.equals(Maybe.some("gap")) ? Maybe.none() : element;
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
    }

    /// A factory that cannot hold any number.
    private static final class NoNumbers implements JsonFactory<Object> {
        @Override
        public Object string(JsonString value) {
            return MODEL.string(value);
        }

        @Override
        public Maybe<Object> number(JsonNumber value) {
            return Maybe.none();
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
            return MODEL.object(members);
        }
    }

    private static Object nested(int depth, Object leaf) {
        Object value = leaf;
        for (int i = 0; i < depth; i++) {
            value = List.of(value);
        }
        return value;
    }

    @Test
    void literalsOfTheSameKindAreEqual() {
        assertTrue(MODEL.equal(true, true));
        assertTrue(MODEL.equal(false, false));
        assertTrue(MODEL.equal(JavaCollectionsModel.NULL, JavaCollectionsModel.NULL));
        assertFalse(MODEL.equal(true, false));
        assertFalse(MODEL.equal(JavaCollectionsModel.NULL, false));
    }

    @Test
    void stringsAndNumbersCompareTheirValues() {
        assertTrue(MODEL.equal("aé", "aé"));
        assertFalse(MODEL.equal("a", "b"));
        assertTrue(MODEL.equal(1, new BigDecimal("1.00")));
        assertFalse(MODEL.equal(1, 2));
    }

    @Test
    void containersCompareTheirChildren() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("x", List.of(1, "y"));
        a.put("z", Map.of());
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("z", Map.of());
        b.put("x", List.of(1.0, "y"));
        assertTrue(MODEL.equal(a, b));
        assertFalse(MODEL.equal(a, Map.of("x", List.of(1, "y"))));
        assertFalse(MODEL.equal(Map.of("x", 1), Map.of("y", 1)));
        assertFalse(MODEL.equal(Map.of("x", 1), Map.of("x", 2)));
        assertFalse(MODEL.equal(List.of(1), List.of(1, 2)));
        assertFalse(MODEL.equal(List.of(List.of(1)), List.of(List.of(2))));
    }

    @Test
    void aModelOfMapsHasNoDuplicateNames() {
        assertFalse(MODEL.hasDuplicate(Map.of("a", 1), JsonString.of("a")));
    }

    @Test
    void valuesOfTwoModelsCompare() {
        GapModel other = new GapModel();
        assertTrue(JsonModel.equal(MODEL, List.of(1, Map.of("a", "x")), other, List.of(1.0, Map.of("a", "x"))));
        assertFalse(JsonModel.equal(MODEL, List.of(1), other, List.of("1")));
        assertFalse(JsonModel.equal(MODEL, Map.of("a", 1), other, Map.of("a", 2)));
        // A model with only the six required methods.
        assertTrue(JsonModel.equal(other, Map.of("a", List.of(1)), MODEL, Map.of("a", List.of(1.0))));
        assertFalse(JsonModel.equal(other, Map.of("a", 1), MODEL, Map.of("a", 2)));
    }

    @Test
    void missingElementsAreNotEqual() {
        GapModel gaps = new GapModel();
        assertFalse(gaps.equal(List.of("gap"), List.of("x")));
        assertFalse(gaps.equal(List.of("x"), List.of("gap")));
    }

    @Test
    void deepValuesDoNotOverflowTheStack() {
        assertTrue(MODEL.equal(nested(100_000, 1), nested(100_000, 1.0)));
        assertFalse(MODEL.equal(nested(100_000, 1), nested(100_000, 2)));
        assertTrue(MODEL.equal(nested(100_000, "x"), MODEL.copyOf(MODEL, nested(100_000, "x")).orElse("none")));
    }

    @Test
    void copiesEachKind() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("s", "aé");
        source.put("n", new BigDecimal("2.50"));
        source.put("t", true);
        source.put("f", false);
        source.put("z", JavaCollectionsModel.NULL);
        source.put("a", List.of(List.of(), Map.of(), List.of(1, 2, 3)));
        source.put("o", Map.of("k", "v"));
        Object copy = MODEL.copyOf(MODEL, source).orElse("none");
        assertTrue(MODEL.equal(source, copy));
        assertEquals(List.of("s", "n", "t", "f", "z", "a", "o"),
                MODEL.members(copy).map(property -> JsonString.copyOf(property.name())).toList());
        assertEquals(Maybe.some("x"), MODEL.copyOf(MODEL, "x"));
        assertEquals(Maybe.some(List.of()), MODEL.copyOf(MODEL, List.of()));
    }

    @Test
    void aCopyFailsForAMissingElement() {
        // A model that is not well-formed: it has no element below the length.
        assertEquals(Maybe.none(), MODEL.copyOf(new GapModel(), List.of("a", "gap", "b")));
    }

    @Test
    void aNumberTheFactoryCannotHoldFailsTheCopy() {
        NoNumbers factory = new NoNumbers();
        assertEquals(Maybe.none(), factory.copyOf(MODEL, 1));
        assertEquals(Maybe.none(), factory.copyOf(MODEL, List.of("a", Map.of("n", 1))));
        assertEquals(Maybe.some(List.of("a")), factory.copyOf(MODEL, List.of("a")));
    }

    @Test
    void copiesAreNewContainers() {
        List<Object> inner = new ArrayList<>(List.of(1));
        Object copy = MODEL.copyOf(MODEL, List.of(inner)).orElse("none");
        inner.add(2);
        assertTrue(MODEL.equal(List.of(List.of(1)), copy));
    }
    /// A model that gives each member of an object twice, as a parser that keeps duplicate names.
    private static final class TwiceModel implements JsonModel<Object> {
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
        public MemberCursor<Object> memberCursor(Object object) {
            MemberCursor<Object> once = MODEL.memberCursor(object);
            return new MemberCursor<>() {
                // Tells if the cursor gave a member one time, and must give it again.
                private boolean again;

                @Override
                public boolean next() {
                    if (again) {
                        again = false;
                        return true;
                    }
                    again = once.next();
                    return again;
                }

                @Override
                public JsonString name() {
                    return once.name();
                }

                @Override
                public Object value() {
                    return once.value();
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

    @Test
    void anObjectWithDuplicateNamesIsNotCopied() {
        TwiceModel twice = new TwiceModel();
        assertEquals(Maybe.none(), MODEL.copyOf(twice, Map.of("a", 1)));
        assertEquals(Maybe.none(), MODEL.copyOf(twice, List.of(1, List.of(Map.of("a", 1)))));
        assertEquals(Maybe.some(List.of(Map.of())), MODEL.copyOf(twice, List.of(Map.of())));
        // "Aa" and "BB" have the same hash code, but they are different names.
        assertEquals("Aa".hashCode(), "BB".hashCode());
        Map<String, Object> collision = new LinkedHashMap<>();
        collision.put("Aa", 1);
        collision.put("BB", 2);
        assertTrue(MODEL.equal(collision, MODEL.copyOf(MODEL, collision).orElse("none")));
    }
}
