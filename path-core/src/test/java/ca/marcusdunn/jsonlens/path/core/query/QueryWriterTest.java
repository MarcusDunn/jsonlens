package ca.marcusdunn.jsonlens.path.core.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.path.core.function.FunctionType;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** JsonPathQuery.toString(): query text from a syntax tree that a caller makes directly. */
@Requirement("4.2/query-construction")
class QueryWriterTest {

    private static final FilterQuery CURRENT = new FilterQuery(Identifier.CURRENT, List.of());
    private static final FilterQuery A = query(Identifier.CURRENT, "a");
    private static final FilterQuery B = query(Identifier.CURRENT, "b");
    private static final FilterQuery C = query(Identifier.ROOT, "c");

    private static FilterQuery query(Identifier identifier, String name) {
        return new FilterQuery(identifier, List.of(new Segment.Child(List.of(new Selector.Name(name)))));
    }

    private static String text(Selector... selectors) {
        return new JsonPathQuery(List.of(new Segment.Child(List.of(selectors)))).toString();
    }

    private static String filter(LogicalExpression expression) {
        return text(new Selector.Filter(expression));
    }

    private static Maybe<Long> some(long value) {
        return Maybe.some(value);
    }

    @Test
    void segmentsAndSelectors() {
        assertEquals("$", new JsonPathQuery(List.of()).toString());
        JsonPathQuery query = new JsonPathQuery(List.of(
                new Segment.Child(List.of(new Selector.Name("a"), new Selector.Wildcard(), new Selector.Index(-1))),
                new Segment.Descendant(List.of(new Selector.Index(0)))));
        assertEquals("$['a',*,-1]..[0]", query.toString());
    }

    @Test
    void slices() {
        assertEquals("$[:]", text(new Selector.Slice(Maybe.none(), Maybe.none(), Maybe.none())));
        assertEquals("$[1:2]", text(new Selector.Slice(some(1), some(2), Maybe.none())));
        assertEquals("$[::-1]", text(new Selector.Slice(Maybe.none(), Maybe.none(), some(-1))));
        assertEquals("$[-3:-1:2]", text(new Selector.Slice(some(-3), some(-1), some(2))));
    }

    @Test
    void stringEscapes() {
        assertEquals("$[' ']", text(new Selector.Name(" ")));
        assertEquals("$['\\b\\f\\n\\r\\t\\'\\\\\\u0000\\u001f\"/é']",
                text(new Selector.Name("\b\f\n\r\t'\\\u0000\u001f\"/é")));
    }

    @Test
    void literalsAndComparisons() {
        List<ComparisonOperator> operators = List.of(ComparisonOperator.values());
        List<String> symbols = operators.stream().map(ComparisonOperator::symbol).toList();
        assertEquals(List.of("==", "!=", "<", "<=", ">", ">="), symbols);
        assertEquals("$[?'x' < 1.50 && true != false && null >= @['a'][0] && $ > @]", filter(new LogicalExpression.And(List.of(
                new LogicalExpression.Comparison(
                        new Literal.StringLiteral("x"), ComparisonOperator.LESS, new Literal.NumberLiteral(new BigDecimal("1.50"))),
                new LogicalExpression.Comparison(
                        new Literal.BooleanLiteral(true), ComparisonOperator.NOT_EQUAL, new Literal.BooleanLiteral(false)),
                new LogicalExpression.Comparison(
                        new Literal.NullLiteral(),
                        ComparisonOperator.GREATER_OR_EQUAL,
                        new SingularQuery(Identifier.CURRENT, List.of(new SingularSegment.Name("a"), new SingularSegment.Index(0)))),
                new LogicalExpression.Comparison(
                        new SingularQuery(Identifier.ROOT, List.of()),
                        ComparisonOperator.GREATER,
                        new SingularQuery(Identifier.CURRENT, List.of()))))));
    }

    @Test
    void precedenceAndParentheses() {
        LogicalExpression or = new LogicalExpression.Or(List.of(A, B));
        LogicalExpression and = new LogicalExpression.And(List.of(A, B));
        assertEquals("$[?@['a'] || @['b']]", filter(or));
        assertEquals("$[?@['a'] && @['b'] || $['c']]", filter(new LogicalExpression.Or(List.of(and, C))));
        assertEquals("$[?(@['a'] || @['b']) && $['c']]", filter(new LogicalExpression.And(List.of(or, C))));
        assertEquals("$[?(@['a'] || @['b']) || $['c']]", filter(new LogicalExpression.Or(List.of(or, C))));
        assertEquals("$[?(@['a'] && @['b']) && $['c']]", filter(new LogicalExpression.And(List.of(and, C))));
        assertEquals("$[?!@['a']]", filter(new LogicalExpression.Not(A)));
        assertEquals("$[?!(@['a'] && @['b'])]", filter(new LogicalExpression.Not(and)));
        assertEquals("$[?!(!@['a'])]", filter(new LogicalExpression.Not(new LogicalExpression.Not(A))));
        LogicalExpression comparison = new LogicalExpression.Comparison(
                new Literal.NullLiteral(), ComparisonOperator.EQUAL, new Literal.NullLiteral());
        assertEquals("$[?!(null == null)]", filter(new LogicalExpression.Not(comparison)));
        assertEquals("$[?null == null || @]", filter(new LogicalExpression.Or(List.of(comparison, CURRENT))));
    }

    @Test
    void functionCalls() {
        FunctionCall.Nodes nodes = new FunctionCall.Nodes("f", List.of(new FunctionArgument.Nodes(C)));
        FunctionCall.Logical logical = new FunctionCall.Logical("g", List.of(
                new FunctionArgument.Value(new Literal.NumberLiteral(BigDecimal.ONE)),
                new FunctionArgument.Logical(new LogicalExpression.Or(List.of(A, B))),
                new FunctionArgument.Nodes(nodes)));
        assertEquals("$[?g(1, @['a'] || @['b'], f($['c']))]", filter(logical));
        assertEquals("$[?!f($['c'])]", filter(new LogicalExpression.Not(nodes)));
        FunctionCall.Value value = new FunctionCall.Value("h", List.of());
        assertEquals("$[?h() == f()]", filter(new LogicalExpression.Comparison(
                value, ComparisonOperator.EQUAL, new FunctionCall.Value("f", List.of()))));
        assertEquals(FunctionType.VALUE, value.resultType());
        assertEquals(FunctionType.LOGICAL, logical.resultType());
        assertEquals(FunctionType.NODES, nodes.resultType());
        assertEquals(
                List.of(FunctionType.VALUE, FunctionType.LOGICAL, FunctionType.NODES),
                logical.arguments().stream().map(FunctionArgument::type).toList());
    }
}
