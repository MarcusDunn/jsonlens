package ca.marcusdunn.jsonlens.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Property;
import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testkit.FaultyModel.Fault;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

@Requirement("lib/testkit-verifier")
class ModelVerifierTest {

    private static List<Violation> verify(Fault fault, String json) {
        return ModelVerifier.of(new FaultyModel(fault)).verify(Documents.collections(json));
    }

    private static Set<Rule> rules(Fault fault, String json) {
        return verify(fault, json).stream().map(Violation::rule).collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void aWellFormedModelHasNoViolations() {
        String document = "{\"a\": [1, \"x\", 9007199254740993, 9007199254740992, 2.5, 1E+0], \"b\": {\"c\": true, \"d\": null, \"e\": false},"
                + " \"absent\": [[], {}], \"absent0\": \"\\u00e9\", \"f\": [{\"x\": 1}, {\"x\": 1.0}]}";
        assertEquals(List.of(), verify(Fault.NONE, document));
        RandomJson random = RandomJson.withSeed(6902);
        for (int i = 0; i < 200; i++) {
            String json = random.next();
            assertEquals(List.of(), verify(Fault.NONE, json), json);
        }
    }

    @Test
    void reportsNullResultsAndExceptions() {
        assertEquals(List.of(new Violation(Rule.NO_NULLS, "$", "kind returned null")), verify(Fault.KIND_IS_NULL, "\"x\""));
        List<Violation> thrown = verify(Fault.STRING_VALUE_THROWS, "\"x\"");
        assertEquals(List.of(new Violation(Rule.NO_EXCEPTIONS, "$", "java.lang.IllegalStateException: broken")), thrown);
    }

    @Test
    void reportsWrongElements() {
        assertEquals(List.of(new Violation(Rule.ELEMENTS, "$[1]", "element(1) is None, but arrayLength is 2")),
                verify(Fault.LAST_ELEMENT_IS_MISSING, "[1, 2]"));
        assertEquals(List.of(
                        new Violation(Rule.ELEMENTS, "$", "element(-1) is present, but arrayLength is 1"),
                        new Violation(Rule.ELEMENTS, "$", "element(-2147483648) is present, but arrayLength is 1")),
                verify(Fault.NEGATIVE_INDEX_IS_PRESENT, "[1]"));
        assertEquals(Set.of(Rule.EQUAL_NODES), rules(Fault.ELEMENTS_ARE_NEW_NODES, "[1]"));
        assertEquals(Set.of(Rule.EQUAL_NODES), rules(Fault.ELEMENTS_HAVE_NEW_HASH_CODES, "[1]"));
    }

    @Test
    void visitsTheValuesInsideObjects() {
        assertEquals(List.of(
                        new Violation(Rule.MEMBER_COUNT, "$", "memberCount is 2, but the cursor gives 1 members"),
                        new Violation(Rule.MEMBER_COUNT, "$['a']", "memberCount is 2, but the cursor gives 1 members")),
                verify(Fault.MEMBER_COUNT_IS_TOO_LARGE, "{\"a\": {\"b\": 1}}"));
    }

    @Test
    void reportsWrongCursors() {
        assertEquals(List.of(new Violation(Rule.MEMBER_CURSOR, "$", "next() is true after it was false")),
                verify(Fault.CURSOR_STARTS_AGAIN, "{\"a\": 1}"));
        // The first walk can have either order, so members() differs from it too.
        assertEquals(Set.of(Rule.MEMBER_CURSOR, Rule.MEMBERS), rules(Fault.CURSOR_ORDER_CHANGES, "{\"a\": 1, \"b\": 2}"));
        assertEquals(Set.of(Rule.MEMBER, Rule.MEMBERS, Rule.EQUAL_NODES), rules(Fault.CURSOR_VALUES_ARE_NEW_NODES, "{\"a\": 1}"));
    }

    @Test
    void reportsWrongMemberMethods() {
        assertEquals(List.of(new Violation(Rule.MEMBER_COUNT, "$", "memberCount is 2, but the cursor gives 1 members")),
                verify(Fault.MEMBER_COUNT_IS_TOO_LARGE, "{\"a\": 1}"));
        assertEquals(Set.of(Rule.MEMBER, Rule.EQUAL), rules(Fault.MEMBER_GIVES_ANOTHER_VALUE, "{\"a\": 1}"));
        assertEquals(List.of(new Violation(Rule.MEMBER, "$['a']", "member() does not give the value of the first member with the name")),
                verify(Fault.MEMBER_NEEDS_OWN_NAMES, "{\"a\": 1}"));
        assertEquals(List.of(new Violation(Rule.MEMBER, "$", "member(\"absent1\") is present, but the cursor has no such member")),
                verify(Fault.MEMBER_FINDS_ANY_NAME, "{\"absent\": 1, \"absent0\": 2}"));
        assertEquals(List.of(new Violation(Rule.MEMBERS, "$", "members() gives the names [a], but the cursor gives [a, b]")),
                verify(Fault.MEMBERS_LOSE_THE_LAST, "{\"a\": 1, \"b\": 2}"));
        assertEquals(List.of(new Violation(Rule.MEMBERS, "$", "members() gives the names [b, a], but the cursor gives [a, b]")),
                verify(Fault.MEMBERS_ARE_REVERSED, "{\"a\": 1, \"b\": 2}"));
        assertEquals(List.of(new Violation(Rule.MEMBERS, "$", "members() gives values that are not equal to the values of the cursor")),
                verify(Fault.MEMBERS_ARE_NEW_NODES, "{\"a\": 1}"));
        assertEquals(List.of(
                        new Violation(Rule.HAS_DUPLICATE, "$['a']", "hasDuplicate is true, but the cursor gives the name 1 times"),
                        new Violation(Rule.HAS_DUPLICATE, "$['a']", "hasDuplicate is true, but the cursor gives the name 1 times"),
                        new Violation(Rule.HAS_DUPLICATE, "$", "hasDuplicate(\"absent\") is true, but the cursor has no such member")),
                verify(Fault.ALWAYS_DUPLICATE, "{\"a\": 1}"));
    }

    @Test
    void reportsValuesThatChange() {
        assertEquals(List.of(new Violation(Rule.STRING_VALUE, "$", "two reads give different strings")), verify(Fault.STRING_VALUE_CHANGES, "\"x\""));
        assertEquals(List.of(new Violation(Rule.NUMBER_VALUE, "$", "two reads give different exact values")), verify(Fault.NUMBER_VALUE_CHANGES, "1"));
    }

    @Test
    void reportsWrongComparisons() {
        assertEquals(Set.of(Rule.COMPARE_NUMBERS, Rule.EQUAL), rules(Fault.COMPARES_AS_DOUBLES, "[9007199254740993, 9007199254740992]"));
        List<Violation> own = verify(Fault.GREATER_COMPARES_AS_EQUAL, "[2.5, 1]");
        assertTrue(own.contains(new Violation(Rule.COMPARE_NUMBERS, "$[1]",
                "compareNumbers gives -1 for 1 and 25E-1, and 0 in the other order; the exact values give -1")), own::toString);
        assertEquals(List.of(new Violation(Rule.EQUAL, "$[0]", "equal does not give false, as the default method does, for a value of kind ARRAY")),
                verify(Fault.ALWAYS_EQUAL, "[1, 2]").subList(0, 1));
        assertEquals(Set.of(Rule.EQUAL), rules(Fault.CONTAINERS_EQUAL_SCALARS, "[1]"));
        assertEquals(Set.of(Rule.COMPARE_NUMBERS, Rule.EQUAL), rules(Fault.SELF_COMPARES_AS_GREATER, "1"));
        assertEquals(List.of(new Violation(Rule.COMPARE_NUMBERS, "$", "compareNumbers gives 1 for 1 and 1, and 1 in the other order; the exact values give 0")),
                verify(Fault.LITERALS_COMPARE_AS_SMALLER, "1"));
    }

    @Test
    void anEarlierValueStaysInTheComparisonsForAWhile() {
        // Each value is compared with the 8 values before it, and each number with the 16 numbers before it.
        StringBuilder values = new StringBuilder("[2");
        for (int i = 0; i < 20; i++) {
            values.append(", 1");
        }
        assertEquals(Set.of(Rule.EQUAL), rules(Fault.ALWAYS_EQUAL, values.append("]").toString()));
    }

    /// Objects as lists of pairs, so that they can hold duplicate names.
    private record Pairs(List<Map.Entry<String, Object>> members) {}

    /// A model of [Pairs] objects. It can report no duplicates, which is wrong for such objects.
    private static final class PairsModel implements JsonModel<Object> {
        private static final JavaCollectionsModel MODEL = JavaCollectionsModel.INSTANCE;
        private final boolean reportsDuplicates;

        PairsModel(boolean reportsDuplicates) {
            this.reportsDuplicates = reportsDuplicates;
        }

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
            return MemberCursor.of(((Pairs) object).members().stream()
                    .map(entry -> new Property<>(JsonString.of(entry.getKey()), entry.getValue())).toList());
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
        public boolean hasDuplicate(Object object, JsonString name) {
            return reportsDuplicates && JsonModel.super.hasDuplicate(object, name);
        }
    }

    @Test
    void checksDuplicateNames() {
        Pairs object = new Pairs(List.of(Map.entry("a", 1), Map.entry("b", 2), Map.entry("a", 3), Map.entry("b", 4)));
        assertEquals(List.of(), ModelVerifier.of(new PairsModel(true)).verify(object));
        assertEquals(List.of(
                        new Violation(Rule.HAS_DUPLICATE, "$['a']", "hasDuplicate is false, but the cursor gives the name 2 times"),
                        new Violation(Rule.HAS_DUPLICATE, "$['a']", "hasDuplicate is false, but the cursor gives the name 2 times"),
                        new Violation(Rule.HAS_DUPLICATE, "$['b']", "hasDuplicate is false, but the cursor gives the name 2 times"),
                        new Violation(Rule.HAS_DUPLICATE, "$['b']", "hasDuplicate is false, but the cursor gives the name 2 times")),
                ModelVerifier.of(new PairsModel(false)).verify(object));
    }

    @Test
    void describesEachRuleAndViolation() {
        assertEquals("No method throws an exception.", Rule.NO_EXCEPTIONS.text());
        assertEquals("MEMBER at $['a']: wrong", new Violation(Rule.MEMBER, "$['a']", "wrong").toString());
    }
}
