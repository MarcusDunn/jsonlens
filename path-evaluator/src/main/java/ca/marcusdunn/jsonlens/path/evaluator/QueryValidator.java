package ca.marcusdunn.jsonlens.path.evaluator;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.path.core.function.FunctionSignature;
import ca.marcusdunn.jsonlens.path.core.query.ComparableExpression;
import ca.marcusdunn.jsonlens.path.core.query.FilterQuery;
import ca.marcusdunn.jsonlens.path.core.query.FunctionArgument;
import ca.marcusdunn.jsonlens.path.core.query.FunctionCall;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.core.query.Literal;
import ca.marcusdunn.jsonlens.path.core.query.LogicalExpression;
import ca.marcusdunn.jsonlens.path.core.query.NodesExpression;
import ca.marcusdunn.jsonlens.path.core.query.Segment;
import ca.marcusdunn.jsonlens.path.core.query.Selector;
import ca.marcusdunn.jsonlens.path.core.query.SingularQuery;
import ca.marcusdunn.jsonlens.path.core.query.SingularSegment;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Checks the validity rules that the Java types of a query cannot express: integer ranges,
 * function signatures, and nesting depth.
 *
 * <p>The parser already makes sure of these rules. The check is for a query that a caller made
 * directly. Each logical expression and each function call is one level of nesting. The
 * recursion stops at the depth limit, so a very deep query cannot cause a stack overflow.
 */
final class QueryValidator {

    private static final long MAX_INTEGER = (1L << 53) - 1;

    private final Map<String, FunctionSignature> signatures;
    private final int maxDepth;
    private int depth;
    private @Nullable EvaluationError error;

    private QueryValidator(Map<String, FunctionSignature> signatures, int maxDepth) {
        this.signatures = signatures;
        this.maxDepth = maxDepth;
    }

    static @Nullable EvaluationError validate(
            JsonPathQuery query, Map<String, FunctionSignature> signatures, int maxDepth) {
        QueryValidator validator = new QueryValidator(signatures, maxDepth);
        validator.segments(query.segments());
        return validator.error;
    }

    private void segments(List<Segment> segments) {
        for (Segment segment : segments) {
            for (Selector selector : segment.selectors()) {
                switch (selector) {
                    case Selector.Index index -> integer(index.index());
                    case Selector.Slice slice -> {
                        integer(slice.start());
                        integer(slice.end());
                        integer(slice.step());
                    }
                    case Selector.Filter filter -> logical(filter.expression());
                    case Selector.Name name -> {}
                    case Selector.Wildcard wildcard -> {}
                }
            }
        }
    }

    private void logical(LogicalExpression expression) {
        if (!enter()) {
            return;
        }
        switch (expression) {
            case LogicalExpression.Or or -> or.operands().forEach(this::logical);
            case LogicalExpression.And and -> and.operands().forEach(this::logical);
            case LogicalExpression.Not not -> logical(not.operand());
            case LogicalExpression.Comparison comparison -> {
                comparable(comparison.left());
                comparable(comparison.right());
            }
            case FilterQuery query -> segments(query.segments());
            case FunctionCall.Logical call -> call(call);
            case FunctionCall.Nodes call -> call(call);
        }
        depth--;
    }

    private void comparable(ComparableExpression expression) {
        switch (expression) {
            case Literal literal -> {}
            case SingularQuery query -> {
                for (SingularSegment segment : query.segments()) {
                    if (segment instanceof SingularSegment.Index index) {
                        integer(index.index());
                    }
                }
            }
            case FunctionCall.Value call -> call(call);
        }
    }

    private void call(FunctionCall call) {
        if (!enter()) {
            return;
        }
        FunctionSignature signature = signatures.get(call.name());
        if (signature == null) {
            fail(new EvaluationError.UnknownFunction(call.name()));
        } else if (!agrees(call, signature)) {
            fail(new EvaluationError.SignatureMismatch(call.name()));
        } else {
            for (FunctionArgument argument : call.arguments()) {
                switch (argument) {
                    case FunctionArgument.Value value -> comparable(value.expression());
                    case FunctionArgument.Logical logical -> logical(logical.expression());
                    case FunctionArgument.Nodes nodes -> {
                        switch (nodes.expression()) {
                            case FilterQuery query -> segments(query.segments());
                            case FunctionCall.Nodes call2 -> call(call2);
                        }
                    }
                }
            }
        }
        depth--;
    }

    private static boolean agrees(FunctionCall call, FunctionSignature signature) {
        List<FunctionArgument> arguments = call.arguments();
        if (signature.resultType() != call.resultType() || signature.parameterTypes().size() != arguments.size()) {
            return false;
        }
        for (int i = 0; i < arguments.size(); i++) {
            if (arguments.get(i).type() != signature.parameterTypes().get(i)) {
                return false;
            }
        }
        return true;
    }

    /** Starts one more level of nesting. Returns false, after it records the error, if the query is too deep. */
    private boolean enter() {
        if (depth == maxDepth) {
            fail(new EvaluationError.QueryTooDeep(maxDepth));
            return false;
        }
        depth++;
        return true;
    }

    private void integer(Maybe<Long> value) {
        if (value instanceof Maybe.Some<Long>(Long number)) {
            integer(number.longValue());
        }
    }

    private void integer(long value) {
        if (value < -MAX_INTEGER || value > MAX_INTEGER) {
            fail(new EvaluationError.IntegerOutOfRange(value));
        }
    }

    /** Records the first error. The check continues, so a later error does not replace it. */
    private void fail(EvaluationError e) {
        if (error == null) {
            error = e;
        }
    }
}
