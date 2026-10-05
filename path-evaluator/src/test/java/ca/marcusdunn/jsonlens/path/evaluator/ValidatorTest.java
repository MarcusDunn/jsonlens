package ca.marcusdunn.jsonlens.path.evaluator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.query.ComparableExpression;
import ca.marcusdunn.jsonlens.path.core.query.ComparisonOperator;
import ca.marcusdunn.jsonlens.path.core.query.FilterQuery;
import ca.marcusdunn.jsonlens.path.core.query.FunctionArgument;
import ca.marcusdunn.jsonlens.path.core.query.FunctionCall;
import ca.marcusdunn.jsonlens.path.core.query.Identifier;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.core.query.Literal;
import ca.marcusdunn.jsonlens.path.core.query.LogicalExpression;
import ca.marcusdunn.jsonlens.path.core.query.Segment;
import ca.marcusdunn.jsonlens.path.core.query.Selector;
import ca.marcusdunn.jsonlens.path.core.query.SingularQuery;
import ca.marcusdunn.jsonlens.path.core.query.SingularSegment;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The validation of queries that a caller makes directly. */
@Requirement("2.1/no-validity-errors-at-evaluation")
class ValidatorTest {

    private static final long BAD = 1L << 53;
    private static final FilterQuery CURRENT = new FilterQuery(Identifier.CURRENT, List.of());
    private static final LogicalExpression BAD_FILTER = new FilterQuery(Identifier.CURRENT, List.of(child(new Selector.Index(BAD))));
    private static final ComparableExpression BAD_SINGULAR =
            new SingularQuery(Identifier.CURRENT, List.of(new SingularSegment.Index(BAD)));
    private static final ComparableExpression NULL = new Literal.NullLiteral();

    private static Segment child(Selector selector) {
        return new Segment.Child(List.of(selector));
    }

    private static JsonPathQuery filter(LogicalExpression expression) {
        return new JsonPathQuery(List.of(child(new Selector.Filter(expression))));
    }

    private static Result<List<Node<tools.jackson.databind.JsonNode>>, EvaluationError> evaluate(JsonPathQuery query) {
        return Eval.STANDARD.evaluate(query, Eval.jackson("[[1]]"), Eval.JACKSON);
    }

    private static EvaluationError error(JsonPathQuery query) {
        return (EvaluationError) assertInstanceOf(Result.Err.class, evaluate(query)).error();
    }

    private static LogicalExpression nots(int count) {
        LogicalExpression expression = CURRENT;
        for (int i = 0; i < count; i++) {
            expression = new LogicalExpression.Not(expression);
        }
        return expression;
    }

    @Test
    void depthLimitIsExact() {
        int limit = JsonPathEvaluator.MAX_QUERY_DEPTH;
        // Each Not and the query at the end are one level each.
        assertInstanceOf(Result.Ok.class, evaluate(filter(nots(limit - 1))));
        assertEquals(new EvaluationError.QueryTooDeep(limit), error(filter(nots(limit))));
    }

    @Test
    void wideQueriesAreNotDeep() {
        int width = 3 * JsonPathEvaluator.MAX_QUERY_DEPTH;
        List<LogicalExpression> operands = Collections.nCopies(width, (LogicalExpression) new LogicalExpression.Not(CURRENT));
        assertInstanceOf(Result.Ok.class, evaluate(filter(new LogicalExpression.Or(operands))));
        assertInstanceOf(Result.Ok.class, evaluate(filter(new LogicalExpression.And(operands))));
        FunctionCall.Value length = new FunctionCall.Value("length", List.of(new FunctionArgument.Value(NULL)));
        List<LogicalExpression> comparisons = Collections.nCopies(width,
                (LogicalExpression) new LogicalExpression.Comparison(length, ComparisonOperator.EQUAL, length));
        assertInstanceOf(Result.Ok.class, evaluate(filter(new LogicalExpression.And(comparisons))));
        FunctionCall.Value count = new FunctionCall.Value("count", List.of(new FunctionArgument.Nodes(CURRENT)));
        List<LogicalExpression> counts = Collections.nCopies(width,
                (LogicalExpression) new LogicalExpression.Comparison(count, ComparisonOperator.EQUAL, NULL));
        assertInstanceOf(Result.Ok.class, evaluate(filter(new LogicalExpression.Or(counts))));
        List<Selector> filters = Collections.nCopies(width, (Selector) new Selector.Filter(new LogicalExpression.Not(CURRENT)));
        assertInstanceOf(Result.Ok.class, evaluate(new JsonPathQuery(List.of(new Segment.Child(filters)))));
    }

    @Test
    void invalidPartsInEachPosition() {
        assertEquals(new EvaluationError.IntegerOutOfRange(BAD), error(new JsonPathQuery(List.of(
                child(new Selector.Slice(Maybe.none(), Maybe.some(BAD), Maybe.none()))))));
        assertEquals(new EvaluationError.IntegerOutOfRange(-BAD), error(new JsonPathQuery(List.of(
                child(new Selector.Slice(Maybe.none(), Maybe.none(), Maybe.some(-BAD)))))));
        assertEquals(new EvaluationError.IntegerOutOfRange(BAD),
                error(filter(new LogicalExpression.Or(List.of(CURRENT, BAD_FILTER)))));
        assertEquals(new EvaluationError.IntegerOutOfRange(BAD),
                error(filter(new LogicalExpression.And(List.of(CURRENT, BAD_FILTER)))));
        assertEquals(new EvaluationError.IntegerOutOfRange(BAD),
                error(filter(new LogicalExpression.Not(BAD_FILTER))));
        assertEquals(new EvaluationError.IntegerOutOfRange(BAD),
                error(filter(new LogicalExpression.Comparison(NULL, ComparisonOperator.EQUAL, BAD_SINGULAR))));
        assertEquals(new EvaluationError.IntegerOutOfRange(BAD),
                error(filter(new LogicalExpression.Comparison(BAD_SINGULAR, ComparisonOperator.EQUAL, NULL))));
        assertEquals(new EvaluationError.IntegerOutOfRange(BAD), error(filter(new LogicalExpression.Comparison(
                new FunctionCall.Value("length", List.of(new FunctionArgument.Value(BAD_SINGULAR))),
                ComparisonOperator.EQUAL, NULL))));
        assertEquals(new EvaluationError.IntegerOutOfRange(BAD), error(filter(new LogicalExpression.Comparison(
                new FunctionCall.Value("count", List.of(new FunctionArgument.Nodes((FilterQuery) BAD_FILTER))),
                ComparisonOperator.EQUAL, NULL))));
        assertEquals(new EvaluationError.IntegerOutOfRange(BAD), error(filter(new FunctionCall.Logical("match", List.of(
                new FunctionArgument.Value(NULL), new FunctionArgument.Value(BAD_SINGULAR))))));
        // A wrong argument type, and a wrong result type.
        assertEquals(new EvaluationError.SignatureMismatch("count"), error(filter(new LogicalExpression.Comparison(
                new FunctionCall.Value("count", List.of(new FunctionArgument.Value(NULL))),
                ComparisonOperator.EQUAL, NULL))));
        assertEquals(new EvaluationError.SignatureMismatch("match"), error(filter(
                new FunctionCall.Nodes("match", List.of(new FunctionArgument.Value(NULL), new FunctionArgument.Value(NULL))))));
        // The first error stays.
        assertEquals(new EvaluationError.IntegerOutOfRange(BAD), error(new JsonPathQuery(List.of(
                child(new Selector.Index(BAD)), child(new Selector.Index(BAD + 1))))));
    }
}
