package ca.marcusdunn.jsonlens.path.core.query;

import ca.marcusdunn.jsonlens.model.Maybe;
import java.util.List;

/**
 * Writes a query as query text.
 *
 * <p>The text uses bracket notation and single quotes. The writer adds parentheses only where
 * operator precedence makes them necessary, and also around a nested operand of the same
 * operator, so that the parser makes the same tree from the text.
 */
final class QueryWriter {

    private static final String HEX = "0123456789abcdef";

    private final StringBuilder text = new StringBuilder();

    private QueryWriter() {}

    static String write(JsonPathQuery query) {
        QueryWriter writer = new QueryWriter();
        writer.text.append('$');
        writer.segments(query.segments());
        return writer.text.toString();
    }

    private void segments(List<Segment> segments) {
        for (Segment segment : segments) {
            if (segment instanceof Segment.Descendant) {
                text.append("..");
            }
            text.append('[');
            List<Selector> selectors = segment.selectors();
            for (int i = 0; i < selectors.size(); i++) {
                if (i > 0) {
                    text.append(',');
                }
                selector(selectors.get(i));
            }
            text.append(']');
        }
    }

    private void selector(Selector selector) {
        switch (selector) {
            case Selector.Name(String name) -> string(name);
            case Selector.Wildcard() -> text.append('*');
            case Selector.Index index -> text.append(index.index());
            case Selector.Slice(Maybe<Long> start, Maybe<Long> end, Maybe<Long> step) -> {
                optional(start);
                text.append(':');
                optional(end);
                if (step.isSome()) {
                    text.append(':');
                    optional(step);
                }
            }
            case Selector.Filter(LogicalExpression expression) -> {
                text.append('?');
                logical(expression, 0);
            }
        }
    }

    private void optional(Maybe<Long> value) {
        if (value instanceof Maybe.Some<Long>(Long number)) {
            text.append(number.longValue());
        }
    }

    /** Writes an expression. Parentheses enclose it if its precedence is not above {@code outer}. */
    private void logical(LogicalExpression expression, int outer) {
        int precedence = precedence(expression);
        boolean parenthesize = precedence <= outer;
        if (parenthesize) {
            text.append('(');
        }
        switch (expression) {
            case LogicalExpression.Or(List<LogicalExpression> operands) -> join(operands, " || ", precedence);
            case LogicalExpression.And(List<LogicalExpression> operands) -> join(operands, " && ", precedence);
            case LogicalExpression.Not(LogicalExpression operand) -> {
                text.append('!');
                boolean test = operand instanceof FilterQuery || operand instanceof FunctionCall;
                logical(operand, test ? 0 : Integer.MAX_VALUE);
            }
            case LogicalExpression.Comparison(
                    ComparableExpression left, ComparisonOperator operator, ComparableExpression right) -> {
                comparable(left);
                text.append(' ').append(operator.symbol()).append(' ');
                comparable(right);
            }
            case FilterQuery query -> filterQuery(query);
            case FunctionCall.Logical call -> function(call);
            case FunctionCall.Nodes call -> function(call);
        }
        if (parenthesize) {
            text.append(')');
        }
    }

    private static int precedence(LogicalExpression expression) {
        return switch (expression) {
            case LogicalExpression.Or or -> 1;
            case LogicalExpression.And and -> 2;
            case LogicalExpression.Comparison comparison -> 3;
            case LogicalExpression.Not not -> 4;
            case FilterQuery query -> 5;
            case FunctionCall.Logical call -> 5;
            case FunctionCall.Nodes call -> 5;
        };
    }

    private void join(List<LogicalExpression> operands, String operator, int precedence) {
        for (int i = 0; i < operands.size(); i++) {
            if (i > 0) {
                text.append(operator);
            }
            logical(operands.get(i), precedence);
        }
    }

    private void comparable(ComparableExpression expression) {
        switch (expression) {
            case Literal.StringLiteral(String value) -> string(value);
            case Literal.NumberLiteral(java.math.BigDecimal value) -> text.append(value);
            case Literal.BooleanLiteral bool -> text.append(bool.value());
            case Literal.NullLiteral() -> text.append("null");
            case SingularQuery query -> {
                identifier(query.identifier());
                for (SingularSegment segment : query.segments()) {
                    text.append('[');
                    switch (segment) {
                        case SingularSegment.Name(String name) -> string(name);
                        case SingularSegment.Index index -> text.append(index.index());
                    }
                    text.append(']');
                }
            }
            case FunctionCall.Value call -> function(call);
        }
    }

    private void filterQuery(FilterQuery query) {
        identifier(query.identifier());
        segments(query.segments());
    }

    private void identifier(Identifier identifier) {
        text.append(identifier == Identifier.ROOT ? '$' : '@');
    }

    private void function(FunctionCall call) {
        text.append(call.name()).append('(');
        List<FunctionArgument> arguments = call.arguments();
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            switch (arguments.get(i)) {
                case FunctionArgument.Value(ComparableExpression expression) -> comparable(expression);
                case FunctionArgument.Logical(LogicalExpression expression) -> logical(expression, 0);
                case FunctionArgument.Nodes(NodesExpression expression) -> {
                    switch (expression) {
                        case FilterQuery query -> filterQuery(query);
                        case FunctionCall.Nodes nodes -> function(nodes);
                    }
                }
            }
        }
        text.append(')');
    }

    /** Writes a string literal in single quotes, with the escapes of a Normalized Path. */
    private void string(String value) {
        text.append('\'');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\b' -> text.append("\\b");
                case '\f' -> text.append("\\f");
                case '\n' -> text.append("\\n");
                case '\r' -> text.append("\\r");
                case '\t' -> text.append("\\t");
                case '\'' -> text.append("\\'");
                case '\\' -> text.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        text.append("\\u00").append(HEX.charAt(c >> 4)).append(HEX.charAt(c & 0xF));
                    } else {
                        text.append(c);
                    }
                }
            }
        }
        text.append('\'');
    }
}
