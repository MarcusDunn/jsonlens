package ca.marcusdunn.jsonlens.path.parser;

import static ca.marcusdunn.jsonlens.path.parser.Queries.accepts;
import static ca.marcusdunn.jsonlens.path.parser.Queries.canonical;
import static ca.marcusdunn.jsonlens.path.parser.Queries.filter;
import static ca.marcusdunn.jsonlens.path.parser.Queries.reject;
import static ca.marcusdunn.jsonlens.path.parser.Queries.rejects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import ca.marcusdunn.jsonlens.path.core.query.ComparisonOperator;
import ca.marcusdunn.jsonlens.path.core.query.FilterQuery;
import ca.marcusdunn.jsonlens.path.core.query.Identifier;
import ca.marcusdunn.jsonlens.path.core.query.Literal;
import ca.marcusdunn.jsonlens.path.core.query.LogicalExpression;
import ca.marcusdunn.jsonlens.path.core.query.SingularQuery;
import ca.marcusdunn.jsonlens.path.core.query.SingularSegment;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Section 2.3.5.1: the syntax of filter selectors. */
class FilterSyntaxTest {

    private static LogicalExpression.Comparison comparison(String query) {
        return assertInstanceOf(LogicalExpression.Comparison.class, filter(query));
    }

    private static Literal literal(String literal) {
        return assertInstanceOf(Literal.class, comparison("$[?@ == " + literal + "]").right());
    }

    private static BigDecimal number(String literal) {
        return assertInstanceOf(Literal.NumberLiteral.class, literal(literal)).value();
    }

    @Test
    @Requirement("2.3.5.1/syntax")
    void filterSelectorSyntax() {
        assertEquals(new FilterQuery(Identifier.CURRENT, List.of()), filter("$[?@]"));
        assertEquals(filter("$[?@]"), filter("$[? @]"));
        assertEquals(filter("$[?@]"), filter("$[?\t\n@]"));
        rejects("$[?]", "$[? ]", "$[?@ @]", "$[@]", "$[?(]");
    }

    @Test
    @Requirement("2.3.5.1/precedence")
    void operatorPrecedence() {
        assertEquals("$[?@['a'] || @['b'] && @['c']]", canonical("$[?@.a || @.b && @.c]"));
        assertEquals("$[?@['a'] && @['b'] || @['c']]", canonical("$[?@.a && @.b || @.c]"));
        assertEquals("$[?!@['a'] && @['b']]", canonical("$[?!@.a && @.b]"));
        assertEquals("$[?@['a'] == 1 && @['b'] != 2 || @['c'] < 3]", canonical("$[?@.a==1&&@.b!=2||@.c<3]"));
        LogicalExpression.Or or = assertInstanceOf(LogicalExpression.Or.class, filter("$[?@.a || @.b && @.c]"));
        assertInstanceOf(LogicalExpression.And.class, or.operands().get(1));
        LogicalExpression.And and = assertInstanceOf(LogicalExpression.And.class, filter("$[?!@.a && @.b]"));
        assertInstanceOf(LogicalExpression.Not.class, and.operands().get(0));
        // Relations bind more tightly than "!" can reach: "!" applies only to a test or parentheses.
        rejects("$[?!@.a == 1]", "$[?@.a == 1 == 1]", "$[?@.a == !@.b]");
    }

    @Test
    @Requirement("2.3.5.1/parentheses")
    void parenthesesGroup() {
        assertEquals(filter("$[?@.a]"), filter("$[?(@.a)]"));
        assertEquals(filter("$[?@.a]"), filter("$[?( ( @.a ) )]"));
        assertEquals(new LogicalExpression.Not(filter("$[?@.a]")), filter("$[?!(@.a)]"));
        assertEquals(new LogicalExpression.Not(filter("$[?@.a]")), filter("$[?! (@.a)]"));
        assertEquals("$[?(@['a'] || @['b']) && @['c']]", canonical("$[?(@.a || @.b) && @.c]"));
        assertEquals("$[?!(@['a'] == 1)]", canonical("$[?!(@.a == 1)]"));
        rejects("$[?(@.a]", "$[?@.a)]", "$[?()]", "$[?(@.a))]");
    }

    @Test
    @Requirement("2.3.5.1/test-expr")
    void testExpressions() {
        assertInstanceOf(FilterQuery.class, filter("$[?@.a]"));
        assertInstanceOf(LogicalExpression.Not.class, filter("$[?!@.a]"));
        accepts("$[?match(@.a, 'x')]", "$[?!search(@.a, 'x')]");
        reject("$[?1]", ParseError.LiteralWithoutComparison.class);
        reject("$[?'a']", ParseError.LiteralWithoutComparison.class);
        reject("$[?true]", ParseError.LiteralWithoutComparison.class);
        reject("$[?null]", ParseError.LiteralWithoutComparison.class);
        reject("$[?!false]", ParseError.LiteralWithoutComparison.class);
        reject("$[?@.a && 1]", ParseError.LiteralWithoutComparison.class);
    }

    @Test
    @Requirement("2.3.5.1/filter-query")
    void relativeAndAbsoluteQueries() {
        assertEquals(Identifier.CURRENT, assertInstanceOf(FilterQuery.class, filter("$[?@.a]")).identifier());
        assertEquals(Identifier.ROOT, assertInstanceOf(FilterQuery.class, filter("$[?$.a]")).identifier());
        assertEquals("$[?@[*]..['b'][?@[0:1]]]", canonical("$[?@.*..b[?@[0:1]]]"));
        rejects("$[?a]", "$[?.a]", "$[?@@]");
    }

    @Test
    @Requirement("2.3.5.1/comparable")
    void comparableSides() {
        accepts("$[?@.a == $.b]", "$[?1 == 1]", "$[?length(@) == 1]", "$[?'a' < @]", "$[?@ == @]");
        for (String query : List.of(
                "$[?@.* == 1]",
                "$[?@..a == 1]",
                "$[?@['a','b'] == 1]",
                "$[?@[0:1] == 1]",
                "$[?@[?@] == 1]",
                "$[?1 == $.*]",
                "$[?@..* == @..*]")) {
            reject(query, ParseError.NonSingularQuery.class);
        }
    }

    @Test
    @Requirement("2.3.5.1/singular-query")
    void singularQueries() {
        assertEquals(
                new SingularQuery(Identifier.CURRENT, List.of(
                        new SingularSegment.Name("a"), new SingularSegment.Index(0), new SingularSegment.Name("b"))),
                comparison("$[?@.a[0]['b'] == 1]").left());
        assertEquals(
                new SingularQuery(Identifier.ROOT, List.of(new SingularSegment.Index(-1))),
                comparison("$[?$[-1] == 1]").left());
        assertEquals(new SingularQuery(Identifier.CURRENT, List.of()), comparison("$[?@ == 1]").left());
    }

    @Test
    @Requirement("2.3.5.1/comparison-operators")
    void sixComparisonOperators() {
        assertEquals(ComparisonOperator.EQUAL, comparison("$[?@.a == 1]").operator());
        assertEquals(ComparisonOperator.NOT_EQUAL, comparison("$[?@.a != 1]").operator());
        assertEquals(ComparisonOperator.LESS, comparison("$[?@.a < 1]").operator());
        assertEquals(ComparisonOperator.LESS_OR_EQUAL, comparison("$[?@.a <= 1]").operator());
        assertEquals(ComparisonOperator.GREATER, comparison("$[?@.a > 1]").operator());
        assertEquals(ComparisonOperator.GREATER_OR_EQUAL, comparison("$[?@.a >= 1]").operator());
        assertEquals(ComparisonOperator.LESS_OR_EQUAL, comparison("$[?@.a<=1]").operator());
        rejects("$[?@.a = 1]", "$[?@.a === 1]", "$[?@.a <> 1]", "$[?@.a =< 1]", "$[?@.a => 1]", "$[?@.a < = 1]");
    }

    @Test
    @Requirement("2.3.5.1/literals")
    void literals() {
        assertEquals(new Literal.StringLiteral("x"), literal("'x'"));
        assertEquals(new Literal.StringLiteral("x"), literal("\"x\""));
        assertEquals(new Literal.BooleanLiteral(true), literal("true"));
        assertEquals(new Literal.BooleanLiteral(false), literal("false"));
        assertEquals(new Literal.NullLiteral(), literal("null"));
        assertEquals(new Literal.NumberLiteral(new BigDecimal("1")), literal("1"));
        rejects("$[?@ == True]", "$[?@ == NULL]", "$[?@ == FALSE]", "$[?@ == nul]", "$[?@ == truex]", "$[?@ == undefined]");
    }

    @Test
    @Requirement("2.3.5.1/number-syntax")
    void numberLiterals() {
        assertEquals(0, BigDecimal.ZERO.compareTo(number("0")));
        assertEquals(0, BigDecimal.ZERO.compareTo(number("-0")));
        assertEquals(0, new BigDecimal("-1").compareTo(number("-1")));
        assertEquals(0, new BigDecimal("1.5").compareTo(number("1.5")));
        assertEquals(0, new BigDecimal("-0.5").compareTo(number("-0.5")));
        assertEquals(0, new BigDecimal("100000").compareTo(number("1e5")));
        assertEquals(0, new BigDecimal("100000").compareTo(number("1E5")));
        assertEquals(0, new BigDecimal("100000").compareTo(number("1e+5")));
        assertEquals(0, new BigDecimal("0.00001").compareTo(number("1e-5")));
        assertEquals(0, new BigDecimal("0.0015").compareTo(number("1.5e-3")));
        assertEquals(0, BigDecimal.ZERO.compareTo(number("-0.0e0")));
        assertEquals(0, new BigDecimal("1e400").compareTo(number("1e400")));
        for (String bad : List.of("01", "-01", "1.", ".5", "+1", "1e", "1e+", "0x1", "1.e5", "--1", "-", "1ee5")) {
            reject("$[?@ == " + bad + "]");
        }
        reject("$[?@ == Infinity]", ParseError.UnexpectedCharacter.class);
        reject("$[?@ == 1e99999999999]", ParseError.NumberOutOfRange.class);
    }
}
