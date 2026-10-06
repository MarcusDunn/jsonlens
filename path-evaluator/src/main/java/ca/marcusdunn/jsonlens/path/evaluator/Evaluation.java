package ca.marcusdunn.jsonlens.path.evaluator;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.path.core.function.FunctionType;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Property;
import ca.marcusdunn.jsonlens.path.core.path.NormalizedPath;
import ca.marcusdunn.jsonlens.path.core.query.ComparableExpression;
import ca.marcusdunn.jsonlens.path.core.query.ComparisonOperator;
import ca.marcusdunn.jsonlens.path.core.query.FilterQuery;
import ca.marcusdunn.jsonlens.path.core.query.FunctionArgument;
import ca.marcusdunn.jsonlens.path.core.query.FunctionCall;
import ca.marcusdunn.jsonlens.path.core.query.Identifier;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.core.query.Literal;
import ca.marcusdunn.jsonlens.path.core.query.LogicalExpression;
import ca.marcusdunn.jsonlens.path.core.query.NodesExpression;
import ca.marcusdunn.jsonlens.path.core.query.Segment;
import ca.marcusdunn.jsonlens.path.core.query.Selector;
import ca.marcusdunn.jsonlens.path.core.query.SingularQuery;
import ca.marcusdunn.jsonlens.path.core.query.SingularSegment;
import ca.marcusdunn.jsonlens.path.evaluator.iregexp.IRegexp;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One evaluation of a valid query against one query argument (RFC 9535, Section 2).
 *
 * <p>When an overflow or an extension error occurs, the evaluation records the first error and
 * stops its work. The result is then that error. The evaluation does not use exceptions.
 *
 * @param <N> the node type
 */
final class Evaluation<N> {

    private final JsonModel<N> model;
    private final Node<N> root;
    private final Functions functions;
    private final @Nullable Building<N> building;
    private final JsonPathEvaluator.Limits limits;
    /**
     * The last compiled regexp of each call of match() and search() in the query. A literal pattern
     * is compiled once. A pattern from the document can be different for each node, so the
     * evaluation keeps only the last one of each call: the query limits the memory.
     */
    private final IdentityHashMap<FunctionCall, CompiledRegexp> regexps = new IdentityHashMap<>();
    /** The value of each literal of the query. A literal is converted once, not for each node. */
    private @Nullable IdentityHashMap<Literal, Val<N>> literals;
    private @Nullable EvaluationError error;

    Evaluation(
            JsonModel<N> model,
            @Nullable Building<N> building,
            N root,
            Functions functions,
            JsonPathEvaluator.Limits limits) {
        this.model = model;
        this.building = building;
        this.root = new Node<>(root, NormalizedPath.root());
        this.functions = functions;
        this.limits = limits;
    }

    Result<List<Node<N>>, EvaluationError> run(JsonPathQuery query) {
        List<Node<N>> nodes = query(query.segments(), root);
        EvaluationError e = error;
        return e != null ? Result.err(e) : Result.ok(List.copyOf(nodes));
    }

    // ---------------------------------------------------------------------------------------
    // Segments and selectors (Sections 2.3 and 2.5)
    // ---------------------------------------------------------------------------------------

    /** Applies each segment to the nodelist of the previous segment (Section 2.1.2). */
    private List<Node<N>> query(List<Segment> segments, Node<N> start) {
        List<Node<N>> current = List.of(start);
        for (Segment segment : segments) {
            List<Node<N>> next = new ArrayList<>();
            for (Node<N> node : current) {
                if (!segment(segment, node, next)) {
                    return List.of();
                }
            }
            current = next;
            if (current.isEmpty()) {
                break;
            }
        }
        return current;
    }

    private boolean segment(Segment segment, Node<N> node, List<Node<N>> out) {
        return switch (segment) {
            case Segment.Child child -> selectAll(child.selectors(), node, out);
            case Segment.Descendant descendant -> {
                // Visit the node and its descendants in pre-order, with arrays in array order
                // (Section 2.5.2.2). The stack holds the walk of each open container, so it
                // prevents a stack overflow for deep values, and it grows with the depth only.
                if (!selectAll(descendant.selectors(), node, out)) {
                    yield false;
                }
                Deque<Children> stack = new ArrayDeque<>();
                stack.push(new Children(node));
                while (!stack.isEmpty()) {
                    Node<N> child = stack.element().next();
                    if (child == null) {
                        stack.pop();
                        continue;
                    }
                    if (!selectAll(descendant.selectors(), child, out)) {
                        yield false;
                    }
                    JsonKind kind = model.kind(child.value());
                    if (kind == JsonKind.ARRAY || kind == JsonKind.OBJECT) {
                        stack.push(new Children(child));
                    }
                }
                yield true;
            }
        };
    }

    /** Concatenates the results of the selectors in their order (Section 2.5.1.2). */
    private boolean selectAll(List<Selector> selectors, Node<N> node, List<Node<N>> out) {
        for (Selector selector : selectors) {
            if (!select(selector, node, out)) {
                return false;
            }
        }
        return true;
    }

    private boolean select(Selector selector, Node<N> node, List<Node<N>> out) {
        N value = node.value();
        JsonKind kind = model.kind(value);
        switch (selector) {
            case Selector.Name(String name) -> {
                JsonString key = JsonString.of(name);
                if (kind == JsonKind.OBJECT && model.member(value, key) instanceof Maybe.Some<N>(N member)) {
                    return add(out, new Node<>(member, node.path().member(key)));
                }
            }
            case Selector.Wildcard() -> {
                Children children = new Children(node);
                for (Node<N> child = children.next(); child != null; child = children.next()) {
                    if (!add(out, child)) {
                        return false;
                    }
                }
            }
            case Selector.Index indexSelector -> {
                long index = indexSelector.index();
                if (kind == JsonKind.ARRAY) {
                    int length = model.arrayLength(value);
                    long normalized = normalize(index, length);
                    if (normalized >= 0 && normalized < length) {
                        return element(node, (int) normalized, out);
                    }
                }
            }
            case Selector.Slice slice -> {
                if (kind == JsonKind.ARRAY) {
                    return slice(slice, node, out);
                }
            }
            case Selector.Filter(LogicalExpression expression) -> {
                Children children = new Children(node);
                for (Node<N> child = children.next(); child != null; child = children.next()) {
                    boolean selected = logical(expression, child);
                    if (error != null || (selected && !add(out, child))) {
                        return false;
                    }
                }
            }
        }
        // A selector that can fail returns false above.
        return true;
    }

    /** The array slice selector (Section 2.3.4.2.2). */
    private boolean slice(Selector.Slice slice, Node<N> node, List<Node<N>> out) {
        long length = model.arrayLength(node.value());
        long step = slice.step().orElse(1L);
        if (step > 0) {
            long start = slice.start().orElse(0L);
            long end = slice.end().orElse(length);
            long lower = Math.min(Math.max(normalize(start, length), 0), length);
            long upper = Math.min(Math.max(normalize(end, length), 0), length);
            for (long i = lower; i < upper; i += step) {
                if (!element(node, (int) i, out)) {
                    return false;
                }
            }
        } else if (step < 0) {
            long end = slice.end().orElse(-length - 1);
            // The default start, length - 1, is already in the bounds.
            long upper = slice.start() instanceof Maybe.Some<Long>(Long start)
                    ? Math.min(Math.max(normalize(start, length), -1), length - 1)
                    : length - 1;
            // Bounds() also limits lower to length - 1. That limit has no effect: lower < upper <= length - 1.
            long lower = Math.max(normalize(end, length), -1);
            for (long i = upper; lower < i; i += step) {
                if (!element(node, (int) i, out)) {
                    return false;
                }
            }
        }
        // A step of 0 selects nothing.
        return true;
    }

    /** Normalize(i, len) of RFC 9535, Section 2.3.4.2.2. */
    private static long normalize(long index, long length) {
        return index >= 0 ? index : length + index;
    }

    private boolean element(Node<N> array, int index, List<Node<N>> out) {
        if (model.element(array.value(), index) instanceof Maybe.Some<N>(N element)) {
            return add(out, new Node<>(element, array.path().element(index)));
        }
        return true;
    }

    /**
     * The walk of the children of a node, in order: array elements or object member values
     * (Section 1.1). It reads one child at a time, with a cursor over the members of an object,
     * and makes no list. One class for both kinds keeps the calls on it monomorphic for the JIT.
     */
    private final class Children {
        private final Node<N> parent;
        /** The cursor over the members of an object. For an array or a scalar, it is null. */
        private final @Nullable MemberCursor<N> members;
        /** The length of an array. For an object or a scalar, it is 0. */
        private final int length;
        private int index;

        Children(Node<N> parent) {
            this.parent = parent;
            JsonKind kind = model.kind(parent.value());
            this.members = kind == JsonKind.OBJECT ? model.memberCursor(parent.value()) : null;
            this.length = kind == JsonKind.ARRAY ? model.arrayLength(parent.value()) : 0;
        }

        /** The next child, or null after the last child. */
        @Nullable Node<N> next() {
            MemberCursor<N> cursor = members;
            if (cursor != null) {
                return cursor.next() ? new Node<>(cursor.value(), parent.path().member(cursor.name())) : null;
            }
            while (index < length) {
                int i = index++;
                if (model.element(parent.value(), i) instanceof Maybe.Some<N>(N element)) {
                    return new Node<>(element, parent.path().element(i));
                }
            }
            return null;
        }
    }

    /** Adds a node to a nodelist, or records an overflow if the nodelist is full. */
    private boolean add(List<Node<N>> out, Node<N> node) {
        if (out.size() >= limits.maxNodes()) {
            fail(new EvaluationError.NodelistTooLarge(limits.maxNodes()));
            return false;
        }
        out.add(node);
        return true;
    }

    // ---------------------------------------------------------------------------------------
    // Filter expressions (Section 2.3.5)
    // ---------------------------------------------------------------------------------------

    /** Evaluates a logical expression for a current node. && and || short-circuit. */
    private boolean logical(LogicalExpression expression, Node<N> current) {
        return switch (expression) {
            case LogicalExpression.Or(List<LogicalExpression> operands) -> {
                for (LogicalExpression operand : operands) {
                    if (logical(operand, current)) {
                        yield true;
                    }
                }
                yield false;
            }
            case LogicalExpression.And(List<LogicalExpression> operands) -> {
                for (LogicalExpression operand : operands) {
                    if (!logical(operand, current)) {
                        yield false;
                    }
                }
                yield true;
            }
            case LogicalExpression.Not(LogicalExpression operand) -> !logical(operand, current);
            case LogicalExpression.Comparison(
                    ComparableExpression left, ComparisonOperator operator, ComparableExpression right) ->
                    compare(operand(value(left, current)), operator, operand(value(right, current)));
            case FilterQuery query -> !filterQuery(query, current).isEmpty();
            case FunctionCall.Logical call -> logicalCall(call, current);
            case FunctionCall.Nodes call -> !nodesCall(call, current).isEmpty();
        };
    }

    private List<Node<N>> filterQuery(FilterQuery query, Node<N> current) {
        return query(query.segments(), query.identifier() == Identifier.ROOT ? root : current);
    }

    /** The value of a literal, from the cache. The cache exists only for a query with literals. */
    private Val<N> cachedLiteral(Literal literal) {
        IdentityHashMap<Literal, Val<N>> cache = literals;
        if (cache == null) {
            cache = new IdentityHashMap<>(4);
            literals = cache;
        }
        return cache.computeIfAbsent(literal, Evaluation::literal);
    }

    private static <M> Val<M> literal(Literal literal) {
        return new Prim<>(switch (literal) {
            case Literal.StringLiteral string -> new Text<>(JsonString.of(string.value()));
            case Literal.NumberLiteral number -> new Numeric<>(JsonNumber.of(number.value()));
            case Literal.BooleanLiteral bool -> new Bool<>(bool.value());
            case Literal.NullLiteral nullLiteral -> new Null<>();
        });
    }

    /** The value of a comparable: a literal, the node of a singular query, or a function result. */
    private Val<N> value(ComparableExpression expression, Node<N> current) {
        return switch (expression) {
            case Literal literal -> cachedLiteral(literal);
            case SingularQuery query -> singular(query, current);
            case FunctionCall.Value call -> valueCall(call, current);
        };
    }

    private Val<N> singular(SingularQuery query, Node<N> current) {
        N value = (query.identifier() == Identifier.ROOT ? root : current).value();
        for (SingularSegment segment : query.segments()) {
            Maybe<N> next = Maybe.none();
            switch (segment) {
                case SingularSegment.Name(String name) -> {
                    if (model.kind(value) == JsonKind.OBJECT) {
                        next = model.member(value, JsonString.of(name));
                    }
                }
                case SingularSegment.Index indexSegment -> {
                    if (model.kind(value) == JsonKind.ARRAY) {
                        int length = model.arrayLength(value);
                        long normalized = normalize(indexSegment.index(), length);
                        if (normalized >= 0 && normalized < length) {
                            next = model.element(value, (int) normalized);
                        }
                    }
                }
            }
            if (!(next instanceof Maybe.Some<N>(N found))) {
                return new Nothing<>();
            }
            value = found;
        }
        return new OfNode<>(value);
    }

    // ---------------------------------------------------------------------------------------
    // Values and comparisons (Section 2.3.5.2.2)
    // ---------------------------------------------------------------------------------------

    /** An instance of ValueType: Nothing, a node, or a primitive value that is not a node. */
    private sealed interface Val<M> {}

    private record Nothing<M>() implements Val<M> {}

    private record OfNode<M>(M node) implements Val<M> {}

    /** A literal of the query, or a result of length() or count(). */
    private record Prim<M>(Primitive<M> primitive) implements Val<M> {}

    /** A value in the form that comparisons and functions need. */
    private sealed interface Operand<M> {}

    /** Nothing, or an empty nodelist. */
    private record Absent<M>() implements Operand<M> {}

    /** An object or an array node. */
    private record Structured<M>(M node) implements Operand<M> {}

    private sealed interface Primitive<M> extends Operand<M> {}

    private record Text<M>(JsonString value) implements Primitive<M> {}

    private record Numeric<M>(JsonNumber value) implements Primitive<M> {}

    private record Bool<M>(boolean value) implements Primitive<M> {}

    private record Null<M>() implements Primitive<M> {}

    private Operand<N> operand(Val<N> value) {
        return switch (value) {
            case Nothing<N> nothing -> new Absent<>();
            case Prim<N> primitive -> primitive.primitive();
            case OfNode<N> node -> operand(node.node());
        };
    }

    private Operand<N> operand(N node) {
        return switch (model.kind(node)) {
            case OBJECT, ARRAY -> new Structured<>(node);
            case STRING -> new Text<>(model.stringValue(node));
            case NUMBER -> new Numeric<>(model.numberValue(node));
            case TRUE -> new Bool<>(true);
            case FALSE -> new Bool<>(false);
            case NULL -> new Null<>();
        };
    }

    private boolean compare(Operand<N> left, ComparisonOperator operator, Operand<N> right) {
        return switch (operator) {
            case EQUAL -> equal(left, right);
            case NOT_EQUAL -> !equal(left, right);
            case LESS -> less(left, right);
            case LESS_OR_EQUAL -> lessOrEqual(left, right);
            case GREATER -> less(right, left);
            case GREATER_OR_EQUAL -> lessOrEqual(right, left);
        };
    }

    private boolean equal(Operand<N> left, Operand<N> right) {
        return switch (left) {
            case Absent<N> a -> right instanceof Absent;
            case Structured<N> a -> right instanceof Structured<N> b && model.equal(a.node(), b.node());
            case Text<N> a -> right instanceof Text<N> b && JsonString.equal(a.value(), b.value());
            case Numeric<N> a -> right instanceof Numeric<N> b && model.compareNumbers(a.value(), b.value()) == 0;
            case Bool<N> a -> right instanceof Bool<N> b && a.value() == b.value();
            case Null<N> a -> right instanceof Null;
        };
    }

    private boolean less(Operand<N> left, Operand<N> right) {
        if (left instanceof Numeric<N> a && right instanceof Numeric<N> b) {
            return model.compareNumbers(a.value(), b.value()) < 0;
        }
        if (left instanceof Text<N> a && right instanceof Text<N> b) {
            return JsonString.compare(a.value(), b.value()) < 0;
        }
        return false;
    }

    /** Less or equal, with one comparison of numbers or strings. */
    private boolean lessOrEqual(Operand<N> left, Operand<N> right) {
        if (left instanceof Numeric<N> a && right instanceof Numeric<N> b) {
            return model.compareNumbers(a.value(), b.value()) <= 0;
        }
        if (left instanceof Text<N> a && right instanceof Text<N> b) {
            return JsonString.compare(a.value(), b.value()) <= 0;
        }
        return equal(left, right);
    }

    // ---------------------------------------------------------------------------------------
    // Function extensions (Section 2.4)
    // ---------------------------------------------------------------------------------------

    // The validator makes sure that each call agrees with its signature. Thus each argument has
    // the declared type of its parameter, and the casts below always succeed.

    private Val<N> valueArgument(FunctionCall call, int index, Node<N> current) {
        return value(((FunctionArgument.Value) call.arguments().get(index)).expression(), current);
    }

    private List<Node<N>> nodesArgument(FunctionCall call, int index, Node<N> current) {
        return nodes(((FunctionArgument.Nodes) call.arguments().get(index)).expression(), current);
    }

    private List<Node<N>> nodes(NodesExpression expression, Node<N> current) {
        return switch (expression) {
            case FilterQuery query -> filterQuery(query, current);
            case FunctionCall.Nodes call -> nodesCall(call, current);
        };
    }

    /** A call of a function with the result type ValueType. */
    private Val<N> valueCall(FunctionCall.Value call, Node<N> current) {
        return switch (call.name()) {
            case "length" -> length(valueArgument(call, 0, current));
            case "count" -> number(nodesArgument(call, 0, current).size());
            case "value" -> single(nodesArgument(call, 0, current));
            default -> {
                if (extension(call, current) instanceof Instance.ValueInstance<N> result) {
                    yield result.value() instanceof Maybe.Some<N>(N node) ? new OfNode<>(node) : new Nothing<>();
                }
                fail(new EvaluationError.ExtensionResultMismatch(call.name(), FunctionType.VALUE));
                yield new Nothing<>();
            }
        };
    }

    /** A call of a function with the result type LogicalType. */
    private boolean logicalCall(FunctionCall.Logical call, Node<N> current) {
        return switch (call.name()) {
            case "match" -> regexp(call, current, true);
            case "search" -> regexp(call, current, false);
            default -> {
                if (extension(call, current) instanceof Instance.LogicalInstance<N> result) {
                    yield result.value();
                }
                fail(new EvaluationError.ExtensionResultMismatch(call.name(), FunctionType.LOGICAL));
                yield false;
            }
        };
    }

    /** A call of a function with the result type NodesType. Only extensions have this type. */
    private List<Node<N>> nodesCall(FunctionCall.Nodes call, Node<N> current) {
        if (extension(call, current) instanceof Instance.NodesInstance<N> result) {
            return result.nodes();
        }
        fail(new EvaluationError.ExtensionResultMismatch(call.name(), FunctionType.NODES));
        return List.of();
    }

    private Instance<N> extension(FunctionCall call, Node<N> current) {
        List<Instance<N>> arguments = new ArrayList<>();
        for (FunctionArgument argument : call.arguments()) {
            arguments.add(switch (argument) {
                case FunctionArgument.Value(ComparableExpression expression) ->
                        new Instance.ValueInstance<>(asNode(value(expression, current)));
                case FunctionArgument.Logical(LogicalExpression expression) ->
                        new Instance.LogicalInstance<>(logical(expression, current));
                case FunctionArgument.Nodes(NodesExpression expression) ->
                        new Instance.NodesInstance<>(nodes(expression, current));
            });
        }
        List<Instance<N>> copy = List.copyOf(arguments);
        // The validator makes sure that the evaluator has each function of the query.
        FunctionExtension readOnly = functions.readOnly().get(call.name());
        if (readOnly != null) {
            return readOnly.apply(copy, model);
        }
        // Only a BuildingEvaluator has building extensions, and it always gives a Building.
        return Objects.requireNonNull(building)
                .apply(Objects.requireNonNull(functions.building().get(call.name())), copy);
    }

    /**
     * A ValueType argument as a node, for a building extension. A read-only extension has no
     * ValueType parameters. A primitive value that is not a node is built with the factory.
     */
    private Maybe<N> asNode(Val<N> value) {
        return switch (value) {
            case Nothing<N> nothing -> Maybe.none();
            case OfNode<N> node -> Maybe.some(node.node());
            case Prim<N> primitive -> build(Objects.requireNonNull(building), primitive.primitive());
        };
    }

    private Maybe<N> build(Building<N> factory, Primitive<N> primitive) {
        return switch (primitive) {
            case Text<N> text -> Maybe.some(factory.string(text.value()));
            case Numeric<N> number -> factory.number(number.value());
            case Bool<N> bool -> Maybe.some(factory.bool(bool.value()));
            case Null<N> nullValue -> Maybe.some(factory.nullValue());
        };
    }

    /** length() (Section 2.4.4). */
    private Val<N> length(Val<N> argument) {
        return switch (operand(argument)) {
            case Text<N> text -> number(JsonString.length(text.value()));
            case Structured<N> structured -> number(model.kind(structured.node()) == JsonKind.ARRAY
                    ? model.arrayLength(structured.node())
                    : model.memberCount(structured.node()));
            default -> new Nothing<>();
        };
    }

    /** value() (Section 2.4.8). */
    private Val<N> single(List<Node<N>> nodes) {
        return nodes.size() == 1 ? new OfNode<>(nodes.getFirst().value()) : new Nothing<>();
    }

    private Val<N> number(int value) {
        return new Prim<>(new Numeric<>(JsonNumber.of(value)));
    }

    /** match() and search() (Sections 2.4.6 and 2.4.7). */
    private boolean regexp(FunctionCall.Logical call, Node<N> current, boolean full) {
        if (!(operand(valueArgument(call, 0, current)) instanceof Text<N> text)
                || !(operand(valueArgument(call, 1, current)) instanceof Text<N> regex)) {
            return false;
        }
        String source = JsonString.copyOf(regex.value());
        CompiledRegexp compiled = regexps.get(call);
        if (compiled == null || !compiled.source().equals(source)) {
            compiled = new CompiledRegexp(source, IRegexp.compile(source, limits.maxRegexSize()));
            regexps.put(call, compiled);
        }
        return switch (compiled.result()) {
            case Result.Ok<IRegexp, IRegexp.Problem>(IRegexp regexp) -> full
                    ? regexp.matches(text.value().scalarValues())
                    : regexp.find(text.value().scalarValues());
            case Result.Err<IRegexp, IRegexp.Problem>(IRegexp.Problem problem) -> {
                if (problem instanceof IRegexp.Problem.TooComplex tooComplex) {
                    fail(new EvaluationError.RegexTooComplex(source, tooComplex.limit()));
                }
                yield false;
            }
        };
    }

    /** A source and its compiled regexp. */
    private record CompiledRegexp(String source, Result<IRegexp, IRegexp.Problem> result) {}

    private void fail(EvaluationError e) {
        if (error == null) {
            error = e;
        }
    }
}
