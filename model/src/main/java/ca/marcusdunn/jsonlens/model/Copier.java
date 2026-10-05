package ca.marcusdunn.jsonlens.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/// Copies a value of one model into a factory, without recursion.
///
/// The copy visits the source in post-order: it builds the children of a container first, and
/// then the container. A stack of frames holds the containers that are not complete.
///
/// @param <S> the node type of the source
/// @param <N> the node type of the factory
final class Copier<S, N> {

    private final JsonModel<S> model;
    private final JsonFactory<N> factory;

    Copier(JsonModel<S> model, JsonFactory<N> factory) {
        this.model = model;
        this.factory = factory;
    }

    /// A container of the source, and the copies of its children so far.
    private final class Frame {
        private final S source;
        private final boolean array;
        /// The cursor over the members of an object source. For an array, it is `null`.
        private final @Nullable MemberCursor<S> cursor;
        private final int size;
        private int next;
        /// The name of the current member of an object source.
        private JsonString name = JsonString.of("");
        /// Tells if the current member has the name of an earlier member.
        private boolean duplicate;
        /// The names so far, in buckets by hash code: they compare by scalar values, without a decoded copy.
        private final Map<Integer, List<JsonString>> names = new HashMap<>();
        private final List<N> elements = new ArrayList<>();
        private final List<Property<N>> copies = new ArrayList<>();

        Frame(S source, boolean array) {
            this.source = source;
            this.array = array;
            this.cursor = array ? null : model.memberCursor(source);
            this.size = array ? model.arrayLength(source) : 0;
        }

        /// Moves to the next child, if there is one. For an object, it also finds a duplicate name.
        boolean hasNext() {
            if (array) {
                return next < size;
            }
            MemberCursor<S> members = Objects.requireNonNull(cursor);
            if (!members.next()) {
                return false;
            }
            name = members.name();
            List<JsonString> bucket = names.computeIfAbsent(JsonString.hash(name), hash -> new ArrayList<>());
            for (JsonString earlier : bucket) {
                duplicate |= JsonString.equal(earlier, name);
            }
            bucket.add(name);
            return true;
        }

        /// The current child of the source, or `null` if the model has no element at the index.
        @Nullable S nextChild() {
            if (!array) {
                return Objects.requireNonNull(cursor).value();
            }
            return model.element(source, next++) instanceof Maybe.Some<S>(S element) ? element : null;
        }

        void add(N copy) {
            if (array) {
                elements.add(copy);
            } else {
                copies.add(new Property<>(name, copy));
            }
        }

        N build() {
            return array ? factory.array(elements) : factory.object(copies);
        }
    }

    Maybe<N> copy(S root) {
        Deque<Frame> stack = new ArrayDeque<>();
        @Nullable S pending = root;
        while (true) {
            @Nullable N done = null;
            if (pending != null) {
                JsonKind kind = model.kind(pending);
                if (kind == JsonKind.ARRAY || kind == JsonKind.OBJECT) {
                    stack.push(new Frame(pending, kind == JsonKind.ARRAY));
                } else if (scalar(kind, pending) instanceof Maybe.Some<N>(N copy)) {
                    done = copy;
                } else {
                    return Maybe.none();
                }
                pending = null;
            }
            if (done == null) {
                // A container is not complete: there is always a frame.
                Frame top = Objects.requireNonNull(stack.peek());
                if (top.hasNext()) {
                    if (top.duplicate) {
                        // Duplicate names have no defined value (RFC 8259, Section 4).
                        return Maybe.none();
                    }
                    pending = top.nextChild();
                    if (pending == null) {
                        // A model that is not well-formed: it has no element below the length.
                        return Maybe.none();
                    }
                    continue;
                }
                stack.pop();
                done = top.build();
            }
            Frame parent = stack.peek();
            if (parent == null) {
                return Maybe.some(done);
            }
            parent.add(done);
        }
    }

    private Maybe<N> scalar(JsonKind kind, S value) {
        return switch (kind) {
            case STRING -> Maybe.some(factory.string(model.stringValue(value)));
            case NUMBER -> factory.number(model.numberValue(value));
            case TRUE -> Maybe.some(factory.bool(true));
            case FALSE -> Maybe.some(factory.bool(false));
            default -> Maybe.some(factory.nullValue());
        };
    }
}
