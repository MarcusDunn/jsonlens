package ca.marcusdunn.jsonlens.model;

import java.util.ArrayDeque;
import java.util.Deque;

/// The equality of two JSON values of two models, without recursion. Two stacks hold the pairs of
/// values that must also be equal.
///
/// @param <A> the node type of the first model
/// @param <B> the node type of the second model
final class Equality<A, B> {

    private final JsonModel<A> first;
    private final JsonModel<B> second;
    private final Deque<A> left = new ArrayDeque<>();
    private final Deque<B> right = new ArrayDeque<>();

    Equality(JsonModel<A> first, JsonModel<B> second) {
        this.first = first;
        this.second = second;
    }

    boolean equal(A a, B b) {
        left.push(a);
        right.push(b);
        while (!left.isEmpty()) {
            A x = left.pop();
            B y = right.pop();
            JsonKind kind = first.kind(x);
            if (kind != second.kind(y) || !sameContent(kind, x, y)) {
                return false;
            }
        }
        return true;
    }

    /// Compares the content of two values of the same kind. For an array or an object, it pushes
    /// the pairs of children that must also be equal.
    private boolean sameContent(JsonKind kind, A x, B y) {
        return switch (kind) {
            case ARRAY -> sameElements(x, y);
            case OBJECT -> sameMembers(x, y);
            case STRING -> JsonString.equal(first.stringValue(x), second.stringValue(y));
            case NUMBER -> first.compareNumbers(first.numberValue(x), second.numberValue(y)) == 0;
            // true, false, and null: the same kind is the same value.
            default -> true;
        };
    }

    private boolean sameElements(A x, B y) {
        int length = first.arrayLength(x);
        if (length != second.arrayLength(y)) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (!(first.element(x, i) instanceof Maybe.Some<A>(A p))
                    || !(second.element(y, i) instanceof Maybe.Some<B>(B q))) {
                return false;
            }
            left.push(p);
            right.push(q);
        }
        return true;
    }

    private boolean sameMembers(A x, B y) {
        if (first.memberCount(x) != second.memberCount(y)) {
            return false;
        }
        for (MemberCursor<A> cursor = first.memberCursor(x); cursor.next(); ) {
            if (!(second.member(y, cursor.name()) instanceof Maybe.Some<B>(B q))) {
                return false;
            }
            left.push(cursor.value());
            right.push(q);
        }
        return true;
    }
}
