package ca.marcusdunn.jsonlens.path.evaluator;

import static ca.marcusdunn.jsonlens.path.evaluator.Eval.paths;
import static ca.marcusdunn.jsonlens.path.evaluator.Eval.jsonValues;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Property;
import ca.marcusdunn.jsonlens.path.core.path.NormalizedPath;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Sections 1.1, 2.1.2, 2.2, 2.3.1 to 2.3.4, 2.5, and 2.6: selectors and segments. */
class SelectorSemanticsTest {

    private static final String DOC = "{\"a\": [10, 11, {\"b\": 12}], \"c\": {\"d\": 13, \"e\": [14]}, \"f\": 15}";
    private static final String ARRAY = "[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]";

    @Test
    @Requirement("1.1/children")
    void childrenAreElementsAndMemberValues() {
        assertEquals(List.of("$['a'][0]", "$['a'][1]", "$['a'][2]"), paths(DOC, "$.a[*]"));
        assertEquals(List.of("$['c']['d']", "$['c']['e']"), paths(DOC, "$.c[*]"));
        assertEquals(List.of(), paths(DOC, "$.f[*]"));
        assertEquals(List.of(), paths("\"text\"", "$[*]"));
    }

    @Test
    @Requirement("1.1/descendants")
    void descendantsAreTheTransitiveClosure() {
        assertEquals(
                List.of("$['a']", "$['c']", "$['f']", "$['a'][0]", "$['a'][1]", "$['a'][2]", "$['a'][2]['b']",
                        "$['c']['d']", "$['c']['e']", "$['c']['e'][0]"),
                paths(DOC, "$..*"));
    }

    @Test
    @Requirement("1.1.1/member-names-not-nodes")
    void memberNamesAreNeverSelected() {
        assertEquals(List.of("15"), jsonValues(DOC, "$.f"));
        assertEquals(List.of("13", "[14]"), jsonValues(DOC, "$.c.*"));
        assertEquals(List.of(), jsonValues("{\"x\": \"y\"}", "$[?@ == 'x']"));
    }

    @Test
    @Requirement("1.3/duplicate-names")
    void theModelDecidesForDuplicateNames() {
        // A model of an object with the member "k" two times: member() gives the first value.
        JsonModel<Object> model = new DuplicateModel();
        List<Node<Object>> wildcard = evaluate("$.*", model);
        assertEquals(List.of("$['k']", "$['k']"), Eval.paths(wildcard));
        assertEquals(List.of(1), evaluate("$.k", model).stream().map(Node::value).toList());
    }

    @Test
    @Requirement("2.1.2/segment-sequence")
    void eachSegmentAppliesToThePreviousResult() {
        assertEquals(List.of("$['a'][2]['b']"), paths(DOC, "$.a[2].b"));
        assertEquals(List.of("$['c']['e'][0]"), paths(DOC, "$['c']['e'][0]"));
    }

    @Test
    @Requirement("2.1.2/input-order")
    void resultsFollowTheInputOrder() {
        assertEquals(List.of("$[2][0]", "$[0][0]", "$[1][0]"), paths("[[\"a\"], [\"b\"], [\"c\"]]", "$[2, 0, 1][0]"));
    }

    @Test
    @Requirement("2.1.2/duplicates-kept")
    void duplicatesAreKept() {
        assertEquals(List.of("$[1]", "$[1]", "$[1]"), paths(ARRAY, "$[1, 1, -9]"));
        assertEquals(List.of("$['a']", "$['a']"), paths(DOC, "$['a', 'a']"));
    }

    @Test
    @Requirement("2.1.2/no-evaluation-errors")
    void mismatchesSelectNothingWithoutError() {
        for (String query : List.of("$[99]", "$[-99]", "$.a.b.c", "$[0]", "$.f[0]", "$.f.x", "$.a[5:9]", "$[?@.x.y > 1]")) {
            assertEquals(List.of(), paths(DOC, query), query);
        }
    }

    @Test
    @Requirement("2.1.2/empty-propagates")
    void anEmptyNodelistGivesAnEmptyResult() {
        assertEquals(List.of(), paths(DOC, "$.missing.a.*..b[0]"));
        assertEquals(List.of(), paths(DOC, "$.a[9]..*"));
    }

    @Test
    @Requirement("2.1.2/result-representation")
    void eachNodeHasItsValueAndItsPath() {
        JsonNode root = Eval.jackson(DOC);
        List<Node<JsonNode>> nodes = Eval.nodes(Eval.STANDARD, Eval.query("$.c.e[0]"), root, Eval.JACKSON);
        assertEquals(1, nodes.size());
        assertSame(root.get("c").get("e").get(0), nodes.getFirst().value());
        assertEquals(NormalizedPath.root().member("c").member("e").element(0), nodes.getFirst().path());
    }

    @Test
    @Requirement("2.2.2/root-node")
    void rootIdentifierGivesTheRootNode() {
        JsonNode root = Eval.jackson(DOC);
        List<Node<JsonNode>> nodes = Eval.nodes(Eval.STANDARD, Eval.query("$"), root, Eval.JACKSON);
        assertEquals(List.of(new Node<>(root, NormalizedPath.root())), nodes);
        assertEquals(List.of("$"), paths("3", "$"));
    }

    @Test
    @Requirement("2.3.1.2/select-member")
    void nameSelectorSelectsTheMemberValue() {
        assertEquals(List.of("15"), jsonValues(DOC, "$['f']"));
        assertEquals(List.of(), jsonValues(DOC, "$['g']"));
        assertEquals(List.of("1"), jsonValues("{\"\": 1}", "$['']"));
    }

    @Test
    @Requirement("2.3.1.2/non-object")
    void nameSelectorOnOtherValues() {
        assertEquals(List.of(), paths("[{\"0\": 1}]", "$['0']"));
        assertEquals(List.of(), paths("\"a\"", "$.a"));
        assertEquals(List.of(), paths("null", "$.a"));
    }

    @Test
    @Requirement("2.3.1.2/no-normalization")
    void namesAreNotNormalized() {
        String json = "{\"é\": 1, \"é\": 2, \"A\": 3}";
        assertEquals(List.of("1"), jsonValues(json, "$['é']"));
        assertEquals(List.of("2"), jsonValues(json, "$['é']"));
        assertEquals(List.of(), jsonValues(json, "$.a"));
    }

    @Test
    @Requirement("2.3.2.2/all-children")
    void wildcardSelectsAllChildren() {
        assertEquals(3, paths(DOC, "$.*").size());
        assertEquals(10, paths(ARRAY, "$[*]").size());
    }

    @Test
    @Requirement("2.3.2.2/array-order")
    void wildcardKeepsArrayOrder() {
        assertEquals(List.of("3", "1", "2"), jsonValues("[3, 1, 2]", "$[*]"));
    }

    @Test
    @Requirement("2.3.2.2/object-order")
    @Requirement("2.3.5.2/object-order")
    @Requirement("2.5.2.2/object-order")
    void objectOrderIsTheOrderOfTheModel() {
        String json = "{\"z\": {\"y\": 1}, \"a\": {\"b\": 2}, \"m\": {\"n\": 3}}";
        assertEquals(List.of("$['z']", "$['a']", "$['m']"), paths(json, "$.*"));
        assertEquals(List.of("$['z']", "$['a']", "$['m']"), paths(json, "$[?@.*]"));
        assertEquals(List.of("$['z']['y']", "$['a']['b']", "$['m']['n']"), paths(json, "$..*.*"));
    }

    @Test
    @Requirement("2.3.2.2/primitive")
    void wildcardOnPrimitives() {
        for (String json : List.of("1", "\"a\"", "true", "false", "null")) {
            assertEquals(List.of(), paths(json, "$.*"), json);
        }
    }

    @Test
    @Requirement("2.3.3.2/non-negative")
    void nonNegativeIndex() {
        assertEquals(List.of("0"), jsonValues(ARRAY, "$[0]"));
        assertEquals(List.of("9"), jsonValues(ARRAY, "$[9]"));
    }

    @Test
    @Requirement("2.3.3.2/negative")
    void negativeIndex() {
        assertEquals(List.of("9"), jsonValues(ARRAY, "$[-1]"));
        assertEquals(List.of("0"), jsonValues(ARRAY, "$[-10]"));
        assertEquals(List.of("$[8]"), paths(ARRAY, "$[-2]"));
    }

    @Test
    @Requirement("2.3.3.2/out-of-range")
    void indexOutsideTheArray() {
        assertEquals(List.of(), paths(ARRAY, "$[10]"));
        assertEquals(List.of(), paths(ARRAY, "$[-11]"));
        assertEquals(List.of(), paths(ARRAY, "$[9007199254740991]"));
        assertEquals(List.of(), paths(ARRAY, "$[-9007199254740991]"));
        assertEquals(List.of(), paths("[]", "$[0]"));
    }

    @Test
    @Requirement("2.3.3.2/non-array")
    void indexOnOtherValues() {
        assertEquals(List.of(), paths("{\"0\": 1}", "$[0]"));
        assertEquals(List.of(), paths("\"abc\"", "$[0]"));
    }

    @Test
    @Requirement("2.3.4.2.2/non-array")
    void sliceOnOtherValues() {
        assertEquals(List.of(), paths("{\"0\": 1, \"1\": 2}", "$[0:2]"));
        assertEquals(List.of(), paths("\"abc\"", "$[:]"));
    }

    @Test
    @Requirement("2.3.4.2.2/defaults")
    void sliceDefaults() {
        assertEquals(jsonValues(ARRAY, "$[0:10:1]"), jsonValues(ARRAY, "$[:]"));
        assertEquals(jsonValues(ARRAY, "$[0:10:2]"), jsonValues(ARRAY, "$[::2]"));
        assertEquals(jsonValues(ARRAY, "$[9:-11:-1]"), jsonValues(ARRAY, "$[::-1]"));
        assertEquals(List.of("9", "6", "3", "0"), jsonValues(ARRAY, "$[::-3]"));
    }

    @Test
    @Requirement("2.3.4.2.2/bounds")
    void sliceBounds() {
        assertEquals(List.of("8", "9"), jsonValues(ARRAY, "$[-2:]"));
        assertEquals(List.of("0", "1"), jsonValues(ARRAY, "$[-100:2]"));
        assertEquals(List.of("8", "9"), jsonValues(ARRAY, "$[8:100]"));
        assertEquals(List.of(), jsonValues(ARRAY, "$[5:2]"));
        assertEquals(List.of("9", "8"), jsonValues(ARRAY, "$[100:7:-1]"));
        assertEquals(List.of("1", "0"), jsonValues(ARRAY, "$[1:-100:-1]"));
        assertEquals(List.of(), jsonValues(ARRAY, "$[-9007199254740991:-9007199254740991]"));
        assertEquals(10, jsonValues(ARRAY, "$[-9007199254740991:9007199254740991]").size());
    }

    @Test
    @Requirement("2.3.4.2.2/positive-step")
    void positiveStep() {
        assertEquals(List.of("1", "4", "7"), jsonValues(ARRAY, "$[1:9:3]"));
        assertEquals(List.of("0"), jsonValues(ARRAY, "$[0:10:9007199254740991]"));
    }

    @Test
    @Requirement("2.3.4.2.2/negative-step")
    void negativeStep() {
        assertEquals(List.of("8", "5", "2"), jsonValues(ARRAY, "$[8:1:-3]"));
        assertEquals(List.of("9"), jsonValues(ARRAY, "$[::-9007199254740991]"));
    }

    @Test
    @Requirement("2.3.4.2.2/zero-step")
    void zeroStep() {
        assertEquals(List.of(), jsonValues(ARRAY, "$[::0]"));
        assertEquals(List.of(), jsonValues(ARRAY, "$[1:5:0]"));
    }

    @Test
    @Requirement("2.5.1.2/selector-order")
    void selectorResultsAreConcatenatedInOrder() {
        assertEquals(List.of("$[3]", "$[0]", "$[1]", "$[2]"), paths("[0, 1, 2, 3]", "$[3, :3]"));
        assertEquals(List.of("$['f']", "$['a']"), paths(DOC, "$['f', 'a']"));
    }

    @Test
    @Requirement("2.5.2.2/visit-order")
    @Requirement("2.5.2.2/concatenation")
    void descendantVisitsNodesBeforeTheirDescendants() {
        String json = "[[1, [2]], [3]]";
        assertEquals(
                List.of("$[0]", "$[1]", "$[0][0]", "$[0][1]", "$[0][1][0]", "$[1][0]"),
                paths(json, "$..[*]"));
        // The input node is visited first, then the descendants in pre-order.
        assertEquals(List.of("$[0]", "$[0][0]", "$[0][1][0]", "$[1][0]"), paths(json, "$..[0]"));
        assertEquals(List.of("$[0][0]", "$[0][1]", "$[0][1][0]"), paths(json, "$[0]..[0, 1]"));
    }

    @Test
    @Requirement("2.6/null-is-value")
    void nullIsAValue() {
        String json = "{\"n\": null, \"list\": [null, 1]}";
        assertEquals(List.of("null"), jsonValues(json, "$.n"));
        assertEquals(List.of("$['n']"), paths(json, "$[?@ == null]"));
        assertEquals(List.of("$['list'][0]"), paths(json, "$.list[?@ == null]"));
        assertEquals(List.of("$['n']", "$['list']"), paths(json, "$[?@]"));
    }

    private static List<Node<Object>> evaluate(String query, JsonModel<Object> model) {
        return switch (Eval.STANDARD.evaluate(Eval.query(query), DuplicateModel.ROOT, model)) {
            case Result.Ok<List<Node<Object>>, EvaluationError>(List<Node<Object>> nodes) -> nodes;
            case Result.Err<List<Node<Object>>, EvaluationError>(EvaluationError error) ->
                    org.junit.jupiter.api.Assertions.fail(error.message());
        };
    }

    /** One object with two members named "k": 1 and 2. */
    private static final class DuplicateModel implements JsonModel<Object> {
        static final Object ROOT = new Object();

        @Override
        public JsonKind kind(Object node) {
            return node instanceof Integer ? JsonKind.NUMBER : JsonKind.OBJECT;
        }

        @Override
        public int arrayLength(Object array) {
            return 0;
        }

        @Override
        public Maybe<Object> element(Object array, int index) {
            return Maybe.none();
        }

        @Override
        public MemberCursor<Object> memberCursor(Object object) {
            return MemberCursor.of(List.of(new Property<>(JsonString.of("k"), 1), new Property<>(JsonString.of("k"), 2)));
        }

        @Override
        public JsonString stringValue(Object string) {
            return JsonString.of("");
        }

        @Override
        public JsonNumber numberValue(Object number) {
            return JsonNumber.of((Integer) number);
        }
    }
}
