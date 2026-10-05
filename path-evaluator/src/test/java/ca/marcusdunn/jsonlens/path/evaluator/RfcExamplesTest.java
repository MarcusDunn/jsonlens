package ca.marcusdunn.jsonlens.path.evaluator;

import static ca.marcusdunn.jsonlens.path.evaluator.Eval.holds;
import static ca.marcusdunn.jsonlens.path.evaluator.Eval.paths;
import static ca.marcusdunn.jsonlens.path.evaluator.Eval.jsonValues;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The example tables of RFC 9535. Both models keep the document order of object members. */
class RfcExamplesTest {

    private static void assertResult(String json, String query, List<String> values, List<String> paths) {
        assertEquals(paths, paths(json, query), query);
        assertEquals(values, jsonValues(json, query), query);
    }

    @Test
    @Requirement("2.1.3/example")
    void section213() {
        assertResult("{\"a\":[{\"b\":0},{\"b\":1},{\"c\":2}]}", "$.a[*].b",
                List.of("0", "1"), List.of("$['a'][0]['b']", "$['a'][1]['b']"));
    }

    @Test
    @Requirement("2.2.3/examples")
    void table3() {
        assertResult("{\"k\": \"v\"}", "$", List.of("{\"k\":\"v\"}"), List.of("$"));
    }

    @Test
    @Requirement("2.3.1.3/examples")
    void table5() {
        String json = "{\"o\": {\"j j\": {\"k.k\": 3}}, \"'\": {\"@\": 2}}";
        assertResult(json, "$.o['j j']", List.of("{\"k.k\":3}"), List.of("$['o']['j j']"));
        assertResult(json, "$.o['j j']['k.k']", List.of("3"), List.of("$['o']['j j']['k.k']"));
        assertResult(json, "$.o[\"j j\"][\"k.k\"]", List.of("3"), List.of("$['o']['j j']['k.k']"));
        assertResult(json, "$[\"'\"][\"@\"]", List.of("2"), List.of("$['\\'']['@']"));
    }

    @Test
    @Requirement("2.3.2.3/examples")
    void table6() {
        String json = "{\"o\": {\"j\": 1, \"k\": 2}, \"a\": [5, 3]}";
        assertResult(json, "$[*]", List.of("{\"j\":1,\"k\":2}", "[5,3]"), List.of("$['o']", "$['a']"));
        // The RFC permits either order for an object. Both models give document order.
        assertResult(json, "$.o[*]", List.of("1", "2"), List.of("$['o']['j']", "$['o']['k']"));
        assertResult(json, "$.o[*, *]", List.of("1", "2", "1", "2"),
                List.of("$['o']['j']", "$['o']['k']", "$['o']['j']", "$['o']['k']"));
        assertResult(json, "$.a[*]", List.of("5", "3"), List.of("$['a'][0]", "$['a'][1]"));
    }

    @Test
    @Requirement("2.3.3.3/examples")
    void table7() {
        assertResult("[\"a\",\"b\"]", "$[1]", List.of("\"b\""), List.of("$[1]"));
        assertResult("[\"a\",\"b\"]", "$[-2]", List.of("\"a\""), List.of("$[0]"));
    }

    @Test
    @Requirement("2.3.4.3/examples")
    void table9() {
        String json = "[\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\"]";
        assertResult(json, "$[1:3]", List.of("\"b\"", "\"c\""), List.of("$[1]", "$[2]"));
        assertResult(json, "$[5:]", List.of("\"f\"", "\"g\""), List.of("$[5]", "$[6]"));
        assertResult(json, "$[1:5:2]", List.of("\"b\"", "\"d\""), List.of("$[1]", "$[3]"));
        assertResult(json, "$[5:1:-2]", List.of("\"f\"", "\"d\""), List.of("$[5]", "$[3]"));
        assertEquals(List.of("$[6]", "$[5]", "$[4]", "$[3]", "$[2]", "$[1]", "$[0]"), paths(json, "$[::-1]"));
    }

    @Test
    @Requirement("2.3.5.3/examples")
    void table11() {
        String json = "{\"obj\": {\"x\": \"y\"}, \"arr\": [2, 3]}";
        record Row(String comparison, boolean result) {}
        for (Row row : List.of(
                new Row("$.absent1 == $.absent2", true),
                new Row("$.absent1 <= $.absent2", true),
                new Row("$.absent == 'g'", false),
                new Row("$.absent1 != $.absent2", false),
                new Row("$.absent != 'g'", true),
                new Row("1 <= 2", true),
                new Row("1 > 2", false),
                new Row("13 == '13'", false),
                new Row("'a' <= 'b'", true),
                new Row("'a' > 'b'", false),
                new Row("$.obj == $.arr", false),
                new Row("$.obj != $.arr", true),
                new Row("$.obj == $.obj", true),
                new Row("$.obj != $.obj", false),
                new Row("$.arr == $.arr", true),
                new Row("$.arr != $.arr", false),
                new Row("$.obj == 17", false),
                new Row("$.obj != 17", true),
                new Row("$.obj <= $.arr", false),
                new Row("$.obj < $.arr", false),
                new Row("$.obj <= $.obj", true),
                new Row("$.arr <= $.arr", true),
                new Row("1 <= $.arr", false),
                new Row("1 >= $.arr", false),
                new Row("1 > $.arr", false),
                new Row("1 < $.arr", false),
                new Row("true <= true", true),
                new Row("true > true", false))) {
            assertEquals(row.result(), holds(json, row.comparison()), row.comparison());
        }
    }

    @Test
    @Requirement("2.3.5.3/examples")
    void table12() {
        String json = """
                {"a": [3, 5, 1, 2, 4, 6, {"b": "j"}, {"b": "k"}, {"b": {}}, {"b": "kilo"}],
                 "o": {"p": 1, "q": 2, "r": 3, "s": 5, "t": {"u": 6}},
                 "e": "f"}""";
        assertResult(json, "$.a[?@.b == 'kilo']", List.of("{\"b\":\"kilo\"}"), List.of("$['a'][9]"));
        assertResult(json, "$.a[?(@.b == 'kilo')]", List.of("{\"b\":\"kilo\"}"), List.of("$['a'][9]"));
        assertResult(json, "$.a[?@>3.5]", List.of("5", "4", "6"), List.of("$['a'][1]", "$['a'][4]", "$['a'][5]"));
        assertEquals(List.of("$['a'][6]", "$['a'][7]", "$['a'][8]", "$['a'][9]"), paths(json, "$.a[?@.b]"));
        assertEquals(List.of("$['a']", "$['o']"), paths(json, "$[?@.*]"));
        assertEquals(List.of("$['a']"), paths(json, "$[?@[?@.b]]"));
        assertEquals(List.of("$['o']['p']", "$['o']['q']", "$['o']['p']", "$['o']['q']"), paths(json, "$.o[?@<3, ?@<3]"));
        assertResult(json, "$.a[?@<2 || @.b == \"k\"]", List.of("1", "{\"b\":\"k\"}"), List.of("$['a'][2]", "$['a'][7]"));
        assertEquals(List.of("$['a'][6]", "$['a'][7]"), paths(json, "$.a[?match(@.b, \"[jk]\")]"));
        assertEquals(List.of("$['a'][6]", "$['a'][7]", "$['a'][9]"), paths(json, "$.a[?search(@.b, \"[jk]\")]"));
        assertResult(json, "$.o[?@>1 && @<4]", List.of("2", "3"), List.of("$['o']['q']", "$['o']['r']"));
        assertResult(json, "$.o[?@.u || @.x]", List.of("{\"u\":6}"), List.of("$['o']['t']"));
        assertEquals(
                List.of("$['a'][0]", "$['a'][1]", "$['a'][2]", "$['a'][3]", "$['a'][4]", "$['a'][5]"),
                paths(json, "$.a[?@.b == $.x]"));
        assertEquals(10, paths(json, "$.a[?@ == @]").size());
    }

    @Test
    @Requirement("2.5.1.3/examples")
    void table15() {
        String json = "[\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\"]";
        assertResult(json, "$[0, 3]", List.of("\"a\"", "\"d\""), List.of("$[0]", "$[3]"));
        assertResult(json, "$[0:2, 5]", List.of("\"a\"", "\"b\"", "\"f\""), List.of("$[0]", "$[1]", "$[5]"));
        assertResult(json, "$[0, 0]", List.of("\"a\"", "\"a\""), List.of("$[0]", "$[0]"));
    }

    @Test
    @Requirement("2.5.2.3/examples")
    void table16() {
        String json = "{\"o\": {\"j\": 1, \"k\": 2}, \"a\": [5, 3, [{\"j\": 4}, {\"k\": 6}]]}";
        assertResult(json, "$..j", List.of("1", "4"), List.of("$['o']['j']", "$['a'][2][0]['j']"));
        assertResult(json, "$..[0]", List.of("5", "{\"j\":4}"), List.of("$['a'][0]", "$['a'][2][0]"));
        List<String> all = List.of(
                "$['o']", "$['a']", "$['o']['j']", "$['o']['k']", "$['a'][0]", "$['a'][1]", "$['a'][2]",
                "$['a'][2][0]", "$['a'][2][1]", "$['a'][2][0]['j']", "$['a'][2][1]['k']");
        assertEquals(Set.copyOf(all), Set.copyOf(paths(json, "$..[*]")));
        assertEquals(all.size(), paths(json, "$..[*]").size());
        assertEquals(paths(json, "$..[*]"), paths(json, "$..*"));
        assertOrderConstraints(paths(json, "$..*"));
        assertResult(json, "$..o", List.of("{\"j\":1,\"k\":2}"), List.of("$['o']"));
        assertEquals(List.of("$['o']['j']", "$['o']['k']", "$['o']['j']", "$['o']['k']"), paths(json, "$.o..[*, *]"));
        assertResult(json, "$.a..[0, 1]", List.of("5", "3", "{\"j\":4}", "{\"k\":6}"),
                List.of("$['a'][0]", "$['a'][1]", "$['a'][2][0]", "$['a'][2][1]"));
    }

    /** The order constraints of the note after Table 16. */
    private static void assertOrderConstraints(List<String> paths) {
        String[][] before = {
            {"$['o']", "$['o']['j']"}, {"$['o']", "$['o']['k']"},
            {"$['a']", "$['a'][0]"}, {"$['a']", "$['a'][1]"}, {"$['a']", "$['a'][2]"},
            {"$['a'][0]", "$['a'][1]"}, {"$['a'][1]", "$['a'][2]"},
            {"$['a'][1]", "$['a'][2][0]"}, {"$['a'][1]", "$['a'][2][0]['j']"},
            {"$['a'][2]", "$['a'][2][0]"}, {"$['a'][2]", "$['a'][2][1]"},
            {"$['a'][2][0]", "$['a'][2][1]"}, {"$['a'][2][1]", "$['a'][2][0]['j']"},
            {"$['a'][2][0]['j']", "$['a'][2][1]['k']"}};
        for (String[] pair : before) {
            assertTrue(paths.indexOf(pair[0]) < paths.indexOf(pair[1]), pair[0] + " before " + pair[1]);
        }
    }

    @Test
    @Requirement("2.6.1/examples")
    void table17() {
        String json = "{\"a\": null, \"b\": [null], \"c\": [{}], \"null\": 1}";
        assertResult(json, "$.a", List.of("null"), List.of("$['a']"));
        assertResult(json, "$.a[0]", List.of(), List.of());
        assertResult(json, "$.a.d", List.of(), List.of());
        assertResult(json, "$.b[0]", List.of("null"), List.of("$['b'][0]"));
        assertResult(json, "$.b[*]", List.of("null"), List.of("$['b'][0]"));
        assertResult(json, "$.b[?@]", List.of("null"), List.of("$['b'][0]"));
        assertResult(json, "$.b[?@==null]", List.of("null"), List.of("$['b'][0]"));
        assertResult(json, "$.c[?@.d==null]", List.of(), List.of());
        assertResult(json, "$.null", List.of("1"), List.of("$['null']"));
    }

    @Test
    @Requirement("2.7/unique")
    void table18() {
        assertEquals(List.of("$['a']"), paths("{\"a\": 1}", "$.a"));
        assertEquals(List.of("$[1]"), paths("[0, 1]", "$[1]"));
        assertEquals(List.of("$[2]"), paths("[0, 1, 2, 3, 4]", "$[-3]"));
        assertEquals(List.of("$['a']['b'][1]"), paths("{\"a\": {\"b\": [0, 1, 2]}}", "$.a.b[1:2]"));
        assertEquals(List.of("$['\\u000b']"), paths("{\"\\u000b\": 1}", "$[\"\\u000B\"]"));
        assertEquals(List.of("$['a']"), paths("{\"a\": 1}", "$[\"\\u0061\"]"));
        // A negative index, a slice, and a filter give the same path for the same node.
        assertEquals(List.of("$[4]", "$[4]", "$[4]"), paths("[0, 1, 2, 3, 4]", "$[-1, 4:, ?@ == 4]"));
    }
}
