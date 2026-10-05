package ca.marcusdunn.jsonlens.path.evaluator;

import static ca.marcusdunn.jsonlens.path.evaluator.Eval.holds;
import static ca.marcusdunn.jsonlens.path.evaluator.Eval.paths;
import static ca.marcusdunn.jsonlens.path.evaluator.Eval.jsonValues;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Section 2.3.5: filter selectors, logical operators, and comparisons. */
class FilterSemanticsTest {

    /** A document whose root has one child, {"x": 0}. A filter on the root tests one expression. */
    private static final String ONE = "{\"o\": {\"x\": 0}}";

    private static boolean test(String expression) {
        return holds(ONE, expression);
    }

    @Test
    @Requirement("2.3.5/children")
    void filterTestsEachChild() {
        String json = "{\"a\": [1, 5, 2, 7], \"o\": {\"p\": 1, \"q\": 9}}";
        assertEquals(List.of("$['a'][1]", "$['a'][3]"), paths(json, "$.a[?@ > 2]"));
        assertEquals(List.of("$['o']['q']"), paths(json, "$.o[?@ > 2]"));
    }

    @Test
    @Requirement("2.3.5.1/current-node")
    void currentNodeIsTheChildOfTheDirectlyEnclosingFilter() {
        String json = "[{\"k\": 1, \"l\": [1, 2]}, {\"k\": 2, \"l\": [1]}, {\"k\": 3, \"l\": [3]}]";
        // The inner @ is an element of l, not the outer element.
        assertEquals(List.of("$[0]", "$[1]"), paths(json, "$[?@.l[?@ == 1]]"));
        assertEquals(List.of("$[0]", "$[2]"), paths(json, "$[?@.l[?@ == $[2].k || @ == 2]]"));
        assertEquals(List.of("$[2]['l'][0]"), paths(json, "$[*].l[?@ == $[2].k]"));
    }

    @Test
    @Requirement("2.3.5.1/evaluation-order")
    void shortCircuitGivesTheSameResult() {
        // An evaluation that is not short-circuit gives the same results, because nothing has side effects.
        assertTrue(test("@.x == 0 || @.a.b.c[?@ > 1]"));
        assertFalse(test("@.x == 1 && @.a.b.c[?@ > 1]"));
        assertEquals(paths(ONE, "$[?@.x == 0 || @.y]"), paths(ONE, "$[?@.y || @.x == 0]"));
        assertEquals(paths(ONE, "$[?@.x == 1 && @.y]"), paths(ONE, "$[?@.y && @.x == 1]"));
    }

    @Test
    @Requirement("2.3.5.1/logical-operators")
    void booleanAlgebra() {
        String t = "@.x == 0";
        String f = "@.x == 1";
        assertTrue(test(t + " || " + f));
        assertTrue(test(f + " || " + t));
        assertFalse(test(f + " || " + f));
        assertTrue(test(t + " && " + t));
        assertFalse(test(t + " && " + f));
        assertFalse(test(f + " && " + t));
        assertFalse(test("!(" + t + ")"));
        assertTrue(test("!(" + f + ")"));
        // De Morgan's laws.
        assertEquals(test("!(" + t + " && " + f + ")"), test("!(" + t + ") || !(" + f + ")"));
        assertEquals(test("!(" + t + " || " + f + ")"), test("!(" + t + ") && !(" + f + ")"));
        assertTrue(test("!@.y"));
        assertFalse(test("!@.x"));
    }

    @Test
    @Requirement("2.3.5.2/structured-only")
    void filterOnPrimitivesSelectsNothing() {
        for (String json : List.of("1", "\"abc\"", "true", "null")) {
            assertEquals(List.of(), paths(json, "$[?@ == @]"), json);
        }
    }

    @Test
    @Requirement("2.3.5.2/array-order")
    void filterKeepsArrayOrder() {
        assertEquals(List.of("5", "9", "7"), jsonValues("[5, 1, 9, 7, 2]", "$[?@ > 4]"));
    }

    @Test
    @Requirement("2.3.5.2.1/existence")
    void queryAsTestIsAnExistenceTest() {
        String json = "[{\"a\": null}, {\"a\": false}, {\"b\": 1}, {\"a\": []}, {}]";
        assertEquals(List.of("$[0]", "$[1]", "$[3]"), paths(json, "$[?@.a]"));
        assertEquals(List.of("$[2]", "$[4]"), paths(json, "$[?!@.a]"));
        assertEquals(List.of("$[0]", "$[1]", "$[2]", "$[3]"), paths(json, "$[?@.*]"));
        assertEquals(List.of("$[0]", "$[1]", "$[2]", "$[3]", "$[4]"), paths(json, "$[?$[0]]"));
    }

    @Test
    @Requirement("2.3.5.2.2/empty-equality")
    void equalityWithNothing() {
        assertTrue(test("@.a == @.b"));
        assertTrue(test("@.a == $.b"));
        assertFalse(test("@.a == null"));
        assertFalse(test("null == @.a"));
        assertFalse(test("@.a == @.x"));
        assertTrue(test("@.a != @.x"));
        assertTrue(test("length(@.a) == @.b"));
    }

    @Test
    @Requirement("2.3.5.2.2/empty-less-than")
    void lessThanWithNothing() {
        assertFalse(test("@.a < @.b"));
        assertFalse(test("@.a < 1"));
        assertFalse(test("1 < @.a"));
        assertFalse(test("@.a > 1"));
        assertTrue(test("@.a <= @.b"));
        assertFalse(test("@.a <= 1"));
        assertFalse(test("@.a >= 0"));
    }

    @Test
    @Requirement("2.3.5.2.2/number-equality")
    void numbersAreEqualByValue() {
        String json = "[1, 1.0, 10e-1, 0.1e1, -0, 0, 2]";
        assertEquals(List.of("$[0]", "$[1]", "$[2]", "$[3]"), paths(json, "$[?@ == 1]"));
        assertEquals(List.of("$[4]", "$[5]"), paths(json, "$[?@ == 0]"));
        assertEquals(List.of("$[4]", "$[5]"), paths(json, "$[?@ == -0.0]"));
        assertTrue(test("1 == 1.00"));
        assertTrue(test("100 == 1e2"));
    }

    @Test
    @Requirement("2.3.5.2.2/number-ordering")
    void numbersCompareMathematically() {
        assertEquals(List.of("-1.5", "-5"), jsonValues("[3, -1.5, -5, 0, 2e0]", "$[?@ < -1]"));
        assertTrue(test("-1 < 0"));
        assertTrue(test("0.1 < 0.2"));
        assertTrue(test("9007199254740991 > 9007199254740990"));
        assertTrue(test("1e-1 < 1"));
    }

    @Test
    @Requirement("2.3.5.2.2/non-interoperable-numbers")
    void numbersOutsideIjsonCompareExactly() {
        String json = "[9007199254740993, 1e400, 123456789012345678901234567890]";
        assertEquals(List.of("$[0]"), paths(json, "$[?@ == 9007199254740993]"));
        assertEquals(List.of(), paths(json, "$[?@ == 9007199254740992]"));
        assertEquals(List.of("$[1]"), paths(json, "$[?@ == 10e399]"));
        assertEquals(List.of("$[1]", "$[2]"), paths(json, "$[?@ > 123456789012345678901234567889]"));
        assertTrue(test("0.30000000000000001 != 0.3"));
    }

    @Test
    @Requirement("2.3.5.2.2/primitive-equality")
    void primitiveEquality() {
        String json = "[\"a\", \"b\", true, false, null, \"true\", \"null\"]";
        assertEquals(List.of("$[0]"), paths(json, "$[?@ == 'a']"));
        assertEquals(List.of("$[2]"), paths(json, "$[?@ == true]"));
        assertEquals(List.of("$[3]"), paths(json, "$[?@ == false]"));
        assertEquals(List.of("$[4]"), paths(json, "$[?@ == null]"));
        assertTrue(test("'é' == '\\u00e9'"));
        assertFalse(test("'é' == 'é'"));
    }

    @Test
    @Requirement("2.3.5.2.2/array-equality")
    void arrayEquality() {
        String json = "{\"a\": [1, [2, {\"x\": 3}]], \"b\": [1, [2, {\"x\": 3}]], \"c\": [1, [2, {\"x\": 4}]],"
                + " \"d\": [[2, {\"x\": 3}], 1], \"e\": [1], \"f\": []}";
        assertTrue(holds(json, "$.a == $.b"));
        assertFalse(holds(json, "$.a == $.c"));
        assertFalse(holds(json, "$.a == $.d"));
        assertFalse(holds(json, "$.a == $.e"));
        assertFalse(holds(json, "$.f == $.e"));
        assertTrue(holds(json, "$.f == $.f"));
    }

    @Test
    @Requirement("2.3.5.2.2/object-equality")
    void objectEquality() {
        String json = "{\"a\": {\"x\": 1, \"y\": [2]}, \"b\": {\"y\": [2], \"x\": 1.0}, \"c\": {\"x\": 1},"
                + " \"d\": {\"x\": 1, \"z\": [2]}, \"e\": {}}";
        assertTrue(holds(json, "$.a == $.b"));
        assertFalse(holds(json, "$.a == $.c"));
        assertFalse(holds(json, "$.c == $.a"));
        assertFalse(holds(json, "$.a == $.d"));
        assertTrue(holds(json, "$.e == $.e"));
        assertFalse(holds(json, "$.e == $.c"));
    }

    @Test
    @Requirement("2.3.5.2.2/kind-mismatch")
    void differentKindsAreNeverEqual() {
        assertFalse(test("1 == '1'"));
        assertFalse(test("null == false"));
        assertFalse(test("0 == false"));
        assertFalse(test("'' == null"));
        assertFalse(holds("{\"a\": [], \"b\": {}}", "$.a == $.b"));
        assertFalse(holds("{\"a\": [1]}", "$.a == 1"));
    }

    @Test
    @Requirement("2.3.5.2.2/string-ordering")
    void stringsCompareByScalarValues() {
        assertTrue(test("'' < 'a'"));
        assertTrue(test("'a' < 'aa'"));
        assertTrue(test("'a' < 'b'"));
        assertTrue(test("'B' < 'a'"));
        // U+FFFF is less than U+10000. In UTF-16 code units, ￿ is greater than \uD800.
        assertTrue(test("'￿' < '𐀀'"));
        assertTrue(test("'' < '😀'"));
        assertFalse(test("'😀' < ''"));
    }

    @Test
    @Requirement("2.3.5.2.2/less-than-kinds")
    void lessThanOnlyForNumbersOrStrings() {
        assertFalse(test("1 < '2'"));
        assertFalse(test("'1' < 2"));
        assertFalse(test("false < true"));
        assertFalse(test("null < null"));
        assertFalse(holds("{\"a\": [1], \"b\": [2]}", "$.a < $.b"));
        assertFalse(holds("{\"a\": {}, \"b\": {\"x\": 1}}", "$.a < $.b"));
    }

    @Test
    @Requirement("2.3.5.2.2/derived-operators")
    void derivedOperators() {
        record Case(String a, String b) {}
        for (Case c : List.of(
                new Case("1", "2"), new Case("2", "1"), new Case("1", "1"), new Case("'a'", "'b'"),
                new Case("true", "true"), new Case("1", "'1'"), new Case("@.missing", "1"), new Case("null", "null"))) {
            boolean equal = test(c.a() + " == " + c.b());
            boolean less = test(c.a() + " < " + c.b());
            boolean greater = test(c.b() + " < " + c.a());
            assertEquals(!equal, test(c.a() + " != " + c.b()), c.toString());
            assertEquals(less || equal, test(c.a() + " <= " + c.b()), c.toString());
            assertEquals(greater, test(c.a() + " > " + c.b()), c.toString());
            assertEquals(greater || equal, test(c.a() + " >= " + c.b()), c.toString());
        }
    }
}
