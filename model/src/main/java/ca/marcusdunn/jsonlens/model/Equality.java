package ca.marcusdunn.jsonlens.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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
        int count = first.memberCount(x);
        if (count != second.memberCount(y)) {
            return false;
        }
        if (count > SMALL) {
            List<Member<B>> members = sorted(y, count);
            if (unique(members)) {
                return sameMembers(x, members);
            }
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

    /// The largest object that the comparison reads with [JsonModel#member(Object, JsonString)]. A
    /// model can find a member in linear time (the default method does), so a larger object gets
    /// a sorted copy of its members: O(n log n), not O(n²).
    private static final int SMALL = 16;

    /// A member of the second object.
    private record Member<B>(JsonString name, B value) {}

    /// Returns the members of an object, sorted by name.
    private List<Member<B>> sorted(B object, int count) {
        List<Member<B>> members = new ArrayList<>(count);
        for (MemberCursor<B> cursor = second.memberCursor(object); cursor.next(); ) {
            members.add(new Member<>(cursor.name(), cursor.value()));
        }
        members.sort((p, q) -> JsonString.compare(p.name(), q.name()));
        return members;
    }

    /// Tells if sorted members have different names. If two members have the same name,
    /// [JsonModel#member(Object, JsonString)] of the model decides which member it finds, so the
    /// comparison uses it.
    private static <B> boolean unique(List<Member<B>> sorted) {
        for (int i = 1; i < sorted.size(); i++) {
            if (JsonString.equal(sorted.get(i - 1).name(), sorted.get(i).name())) {
                return false;
            }
        }
        return true;
    }

    /// Compares the members of an object with the sorted members of another object.
    private boolean sameMembers(A x, List<Member<B>> members) {
        for (MemberCursor<A> cursor = first.memberCursor(x); cursor.next(); ) {
            int found = indexOf(members, cursor.name());
            if (found < 0) {
                return false;
            }
            left.push(cursor.value());
            right.push(members.get(found).value());
        }
        return true;
    }

    /// Returns the index of a name in sorted members, or -1 if no member has the name.
    private static <B> int indexOf(List<Member<B>> members, JsonString name) {
        int low = 0;
        int high = members.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int order = JsonString.compare(members.get(middle).name(), name);
            if (order < 0) {
                low = middle + 1;
            } else if (order > 0) {
                high = middle - 1;
            } else {
                return middle;
            }
        }
        return -1;
    }
}
