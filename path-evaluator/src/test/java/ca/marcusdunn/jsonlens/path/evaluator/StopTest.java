package ca.marcusdunn.jsonlens.path.evaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * After an overflow, the evaluator stops its work. It does not read more of the query argument.
 * Each document has three large children, and each query overflows in the first child.
 */
@Requirement("2.1/overflow-indication")
@Requirement("4.1/evaluator-resources")
class StopTest {

    private static final JsonPathEvaluator LIMITED = Eval.STANDARD.withLimits(new JsonPathEvaluator.Limits(3, 100));
    private static final String ARRAYS = threeTimes("[" + IntStream.range(0, 1000)
            .mapToObj(Integer::toString).collect(Collectors.joining(",")) + "]");
    private static final String OBJECTS = threeTimes("{" + IntStream.range(0, 1000)
            .mapToObj(i -> "\"k" + i + "\": " + i).collect(Collectors.joining(",")) + ", \"a\": 1}");

    private static String threeTimes(String child) {
        return "[" + child + "," + child + "," + child + "]";
    }

    /** Evaluates, checks for the overflow, and returns the number of reads. */
    private static int reads(String json, String query) {
        CountingModel<JsonNode> model = new CountingModel<>(Eval.JACKSON);
        Result<List<Node<JsonNode>>, EvaluationError> result =
                LIMITED.evaluate(Eval.query(query), Eval.jackson(json), model);
        assertEquals(new EvaluationError.NodelistTooLarge(3),
                assertInstanceOf(Result.Err.class, result).error(), query);
        return model.reads;
    }

    @Test
    void wildcardStops() {
        // 3 reads for $[*], then 1000 for the children of the first array. The others are not read.
        assertTrue(reads(ARRAYS, "$[*][0, 1, *]") < 1100);
        // A filter after the wildcard would read 1000 more children.
        assertTrue(reads(ARRAYS, "$[*][*, ?@]") < 1100);
    }

    @Test
    void nameStops() {
        assertEquals(3 + 4, reads(OBJECTS, "$[*]['a', 'a', 'a', 'a']"));
    }

    @Test
    void indexStops() {
        assertEquals(3 + 4, reads(ARRAYS, "$[*][0, 0, 0, 0]"));
    }

    @Test
    void sliceStops() {
        assertEquals(3 + 4, reads(ARRAYS, "$[*][0:10]"));
        assertEquals(3 + 4, reads(ARRAYS, "$[*][10:0:-1]"));
        assertEquals(3 + 4, reads(ARRAYS, "$[*][0:2, 0:10]"));
    }

    @Test
    void filterStops() {
        assertTrue(reads(ARRAYS, "$[*][?@ >= 0]") < 1100);
    }

    @Test
    void descendantStops() {
        assertTrue(reads(ARRAYS, "$[*]..[0, 1, 2, 3]") < 1100);
    }

    @Test
    void zeroStepDoesNotStop() {
        assertEquals(List.of("$[0]"), Eval.paths("[1]", "$[::0, 0]"));
    }
    @Test
    void childWalksStopAtTheLimit() {
        // The walk reads one child at a time: after the 3 nodes of $[*], it reads the children of
        // the first container only until the nodelist is full.
        for (String query : List.of("$[*][*]", "$[*][?@ >= 0]", "$[*][*, ?@]", "$[*]..*")) {
            assertEquals(7, reads(ARRAYS, query), query);
            assertEquals(8, reads(OBJECTS, query), query);
        }
    }
}
