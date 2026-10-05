package ca.marcusdunn.jsonlens.path.core.query;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Adversarial findings for the core module. These tests assert the promises of the library and
 * fail against the current code.
 */
class AdversarialConformanceTest {

    private static JsonPathQuery deepQuery() {
        LogicalExpression nested = new FilterQuery(Identifier.CURRENT, List.of());
        for (int i = 0; i < 100_000; i++) {
            nested = new LogicalExpression.Not(nested);
        }
        return new JsonPathQuery(List.of(new Segment.Child(List.of(new Selector.Filter(nested)))));
    }

    /**
     * Library promise lib/core-exception-free: "With non-null arguments, no public operation of the
     * core module throws an exception." RFC 9535, Section 4.1 also asks implementations to guard
     * against resource exhaustion from deeply nested input.
     *
     * <p>NormalizedPath avoids recursion for this reason (NormalizedPathTest.deepPathsDoNotOverflowTheStack),
     * and the evaluator gives QueryTooDeep for the same deep query (RobustnessTest). But
     * JsonPathQuery.toString() (QueryWriter) and the generated equals() and hashCode() of the query
     * records use recursion.
     *
     * <p>Input: a query that a caller makes directly, {@code $[?!!!...!@]} with 100,000 negations.
     * Expected: toString(), hashCode(), and equals() return a value. Actual: each throws
     * StackOverflowError.
     */
    @Disabled("ISSUE-1: very deep hand-made queries overflow the stack")
    @Test
    @Requirement("lib/core-exception-free")
    void deepQueriesDoNotOverflowTheStack() {
        JsonPathQuery a = deepQuery();
        JsonPathQuery b = deepQuery();
        assertDoesNotThrow(() -> a.toString());
        assertDoesNotThrow(() -> a.hashCode());
        assertDoesNotThrow(() -> assertEquals(a, b));
    }
}
