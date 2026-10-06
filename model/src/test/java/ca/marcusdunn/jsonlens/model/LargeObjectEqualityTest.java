package ca.marcusdunn.jsonlens.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/// The equality of objects with more than 16 members: a sorted copy of the members of the second
/// object, not a call of `member` for each name. A model with a linear `member` (such as the
/// memory-mapped model) then compares two objects of n members in O(n log n), not O(n²).
@Requirement("lib/model-equality-and-copy")
class LargeObjectEqualityTest {

    private static final JavaCollectionsModel MODEL = JavaCollectionsModel.INSTANCE;

    /// A model that counts the calls of `member`, and can give each member of an object twice.
    private static final class CountingModel implements JsonModel<Object> {
        private final boolean twice;
        private int lookups;

        CountingModel(boolean twice) {
            this.twice = twice;
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
        public Maybe<Object> member(Object object, JsonString name) {
            lookups++;
            return JsonModel.super.member(object, name);
        }

        @Override
        public MemberCursor<Object> memberCursor(Object object) {
            MemberCursor<Object> once = MODEL.memberCursor(object);
            return new MemberCursor<>() {
                private boolean again;

                @Override
                public boolean next() {
                    if (again) {
                        again = false;
                        return true;
                    }
                    boolean found = once.next();
                    again = found && twice;
                    return found;
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

    /// An object with members "m0" to "m<count - 1>", each with its index as the value.
    private static Map<String, Object> object(int count) {
        Map<String, Object> object = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            object.put("m" + i, i);
        }
        return object;
    }

    /// The same members as `object(count)`, in the reverse order.
    private static Map<String, Object> reversed(int count) {
        List<String> names = new ArrayList<>(object(count).keySet());
        Collections.reverse(names);
        Map<String, Object> object = new LinkedHashMap<>();
        for (String name : names) {
            object.put(name, Integer.parseInt(name.substring(1)));
        }
        return object;
    }

    @Test
    void aLargeObjectIsComparedWithoutMemberLookups() {
        CountingModel counting = new CountingModel(false);
        assertTrue(JsonModel.equal(MODEL, object(17), counting, reversed(17)));
        assertEquals(0, counting.lookups);
    }

    @Test
    void anObjectOfSixteenMembersUsesMemberLookups() {
        CountingModel counting = new CountingModel(false);
        assertTrue(JsonModel.equal(MODEL, object(16), counting, reversed(16)));
        assertEquals(16, counting.lookups);
    }

    @Test
    void eachNameOfALargeObjectIsFound() {
        // Each name, in each position of the sorted members: the smallest, the largest, and between.
        for (int count = 17; count <= 40; count++) {
            assertTrue(MODEL.equal(object(count), reversed(count)), count + " members");
        }
    }

    @Test
    void aMissingNameMakesLargeObjectsDifferent() {
        for (String name : List.of("m0", "m19", "m25", "m9")) {
            Map<String, Object> other = object(30);
            other.remove(name);
            other.put(name + "x", other.size());
            assertFalse(MODEL.equal(object(30), other), name);
            assertFalse(MODEL.equal(other, object(30)), name);
        }
        Map<String, Object> before = object(30);
        before.put("a", 0);
        before.remove("m0");
        assertFalse(MODEL.equal(object(30), before));
    }

    @Test
    void aDifferentValueMakesLargeObjectsDifferent() {
        Map<String, Object> other = reversed(30);
        other.put("m7", "seven");
        assertFalse(MODEL.equal(object(30), other));
    }

    @Test
    void duplicateNamesUseTheMemberLookupOfTheModel() {
        CountingModel twice = new CountingModel(true);
        Map<String, Object> object = object(9);
        // 18 members, two of each name: the sorted copy finds the duplicates, so the comparison
        // finds each name with the model.
        assertTrue(JsonModel.equal(twice, object, twice, reversed(9)));
        assertEquals(18, twice.lookups);
    }
}
