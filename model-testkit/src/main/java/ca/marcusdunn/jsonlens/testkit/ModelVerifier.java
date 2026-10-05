package ca.marcusdunn.jsonlens.testkit;

import ca.marcusdunn.jsonlens.path.core.path.NormalizedPath;
import ca.marcusdunn.jsonlens.model.JsonDecimal;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Property;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Checks the rules of a [JsonModel] on a document, and returns each [Violation].
///
/// The verifier visits each value of the document. For each value, it calls the methods of the
/// model for the kind of the value, and it compares the results with each other:
///
/// - the members of the cursor with [JsonModel#memberCount(Object)],
///   [JsonModel#member(Object, JsonString)], [JsonModel#members(Object)], and
///   [JsonModel#hasDuplicate(Object, JsonString)];
/// - two reads of each value, which must give equal nodes;
/// - [JsonModel#compareNumbers(JsonNumber, JsonNumber)] with the exact values;
/// - [JsonModel#equal(Object, Object)] with the default method of [JsonModel].
///
/// Thus an override of a default method must give the result of the default method. The verifier
/// calls a method for a kind only for a node of that kind, as jsonlens does. It does not throw:
/// an exception or a `null` result of the model is a violation.
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.TestkitSnippets region=verify}
///
/// @param <N> the node type of the model
public final class ModelVerifier<N> {

    /// The number of earlier numbers that each number is compared with.
    private static final int NUMBER_PAIRS = 16;
    /// The number of earlier values that each value is compared with.
    private static final int VALUE_PAIRS = 8;

    private final JsonModel<N> model;
    private final JsonModel<N> reference;

    private ModelVerifier(JsonModel<N> model) {
        this.model = model;
        this.reference = new PrimitivesOnly<>(model);
    }

    /// Returns a verifier for a model.
    ///
    /// @param model the model to check
    /// @param <N> the node type of the model
    /// @return the verifier
    public static <N> ModelVerifier<N> of(JsonModel<N> model) {
        return new ModelVerifier<>(model);
    }

    /// Checks each value of a document.
    ///
    /// @param root the root value of the document
    /// @return the violations, in the order of the visits; an empty list if the model obeys each rule
    public List<Violation> verify(N root) {
        return new Run().verify(root);
    }

    /// A value to visit, and its location.
    private record Visit<N>(N node, NormalizedPath path) {}

    /// The members of one walk of a cursor.
    private record Walk<N>(List<JsonString> names, List<N> values, boolean stopsAtTheEnd) {}

    /// A result of the model that is `null`.
    private static final class NullResult extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String call;

        NullResult(String call) {
            super(call + " returned null", null, false, false);
            this.call = call;
        }
    }

    /// The state of one verification.
    private final class Run {
        private final List<Violation> violations = new ArrayList<>();
        private final Deque<Visit<N>> pending = new ArrayDeque<>();
        private final List<N> values = new ArrayList<>();
        private final List<JsonNumber> numbers = new ArrayList<>();

        List<Violation> verify(N root) {
            pending.push(new Visit<>(root, NormalizedPath.root()));
            while (!pending.isEmpty()) {
                Visit<N> visit = pending.pop();
                try {
                    visit(visit.node(), visit.path());
                } catch (NullResult e) {
                    add(Rule.NO_NULLS, visit.path(), e.call + " returned null");
                } catch (RuntimeException e) {
                    add(Rule.NO_EXCEPTIONS, visit.path(), e.toString());
                }
            }
            return List.copyOf(violations);
        }

        private void add(Rule rule, NormalizedPath path, String detail) {
            violations.add(new Violation(rule, path.toString(), detail));
        }

        private <T> T checked(@Nullable T value, String call) {
            if (value == null) {
                throw new NullResult(call);
            }
            return value;
        }

        private void visit(N node, NormalizedPath path) {
            JsonKind kind = checked(model.kind(node), "kind");
            equal(node, path);
            switch (kind) {
                case ARRAY -> array(node, path);
                case OBJECT -> object(node, path);
                case STRING -> string(node, path);
                case NUMBER -> number(node, path);
                default -> {
                    // A literal has no value to read.
                }
            }
        }

        private void array(N array, NormalizedPath path) {
            int length = model.arrayLength(array);
            List<Visit<N>> children = new ArrayList<>();
            for (int i = 0; i < length; i++) {
                NormalizedPath at = new NormalizedPath.Element(path, i);
                if (checked(model.element(array, i), "element") instanceof Maybe.Some<N>(N element)) {
                    if (!model.element(array, i).map(again -> equalNodes(element, again)).orElse(false)) {
                        add(Rule.EQUAL_NODES, at, "two reads of element(" + i + ") give nodes that are not equal");
                    }
                    children.add(new Visit<>(element, at));
                } else {
                    add(Rule.ELEMENTS, at, "element(" + i + ") is None, but arrayLength is " + length);
                }
            }
            for (int outside : new int[] {-1, length, Integer.MIN_VALUE}) {
                if (checked(model.element(array, outside), "element").isSome()) {
                    add(Rule.ELEMENTS, path, "element(" + outside + ") is present, but arrayLength is " + length);
                }
            }
            push(children);
        }

        private void object(N object, NormalizedPath path) {
            Walk<N> walk = walk(object);
            Walk<N> again = walk(object);
            List<JsonString> names = walk.names();
            if (!walk.stopsAtTheEnd()) {
                add(Rule.MEMBER_CURSOR, path, "next() is true after it was false");
            }
            if (!sameNames(names, again.names())) {
                add(Rule.MEMBER_CURSOR, path, "two walks give the names " + text(names) + " and " + text(again.names()));
            } else if (!sameValues(walk.values(), again.values())) {
                add(Rule.EQUAL_NODES, path, "two walks of the cursor give values that are not equal");
            }
            int count = model.memberCount(object);
            if (count != names.size()) {
                add(Rule.MEMBER_COUNT, path, "memberCount is " + count + ", but the cursor gives " + names.size() + " members");
            }
            members(object, walk, path);
            List<Visit<N>> children = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                NormalizedPath at = new NormalizedPath.Member(path, names.get(i));
                if (first(names, i)) {
                    member(object, names.get(i), walk.values().get(i), occurrences(names, names.get(i)), at);
                }
                children.add(new Visit<>(walk.values().get(i), at));
            }
            absent(object, names, path);
            push(children);
        }

        private Walk<N> walk(N object) {
            MemberCursor<N> cursor = checked(model.memberCursor(object), "memberCursor");
            List<JsonString> names = new ArrayList<>();
            List<N> members = new ArrayList<>();
            while (cursor.next()) {
                names.add(checked(cursor.name(), "MemberCursor.name"));
                members.add(checked(cursor.value(), "MemberCursor.value"));
            }
            return new Walk<>(names, members, !cursor.next());
        }

        private void members(N object, Walk<N> walk, NormalizedPath path) {
            List<Property<N>> properties = checked(model.members(object), "members").toList();
            List<JsonString> names = properties.stream().map(Property::name).toList();
            if (!sameNames(walk.names(), names)) {
                add(Rule.MEMBERS, path, "members() gives the names " + text(names) + ", but the cursor gives " + text(walk.names()));
            } else if (!sameValues(walk.values(), properties.stream().map(Property::value).toList())) {
                add(Rule.MEMBERS, path, "members() gives values that are not equal to the values of the cursor");
            }
        }

        /// Checks member and hasDuplicate for a name, as the model gives it and as a `String`.
        private void member(N object, JsonString name, N expected, int occurrences, NormalizedPath at) {
            for (JsonString form : List.of(name, JsonString.of(JsonString.copyOf(name)))) {
                if (!(checked(model.member(object, form), "member") instanceof Maybe.Some<N>(N found) && equalNodes(expected, found))) {
                    add(Rule.MEMBER, at, "member() does not give the value of the first member with the name");
                }
                boolean duplicate = model.hasDuplicate(object, form);
                if (duplicate != occurrences > 1) {
                    add(Rule.HAS_DUPLICATE, at, "hasDuplicate is " + duplicate + ", but the cursor gives the name " + occurrences + " times");
                }
            }
        }

        /// Checks member and hasDuplicate for a name that the object does not have.
        private void absent(N object, List<JsonString> names, NormalizedPath path) {
            JsonString absent = JsonString.of("absent");
            for (int i = 0; occurrences(names, absent) > 0; i++) {
                absent = JsonString.of("absent" + i);
            }
            if (checked(model.member(object, absent), "member").isSome()) {
                add(Rule.MEMBER, path, "member(\"" + JsonString.copyOf(absent) + "\") is present, but the cursor has no such member");
            }
            if (model.hasDuplicate(object, absent)) {
                add(Rule.HAS_DUPLICATE, path, "hasDuplicate(\"" + JsonString.copyOf(absent) + "\") is true, but the cursor has no such member");
            }
        }

        private void string(N string, NormalizedPath path) {
            JsonString value = checked(model.stringValue(string), "stringValue");
            if (!JsonString.equal(value, checked(model.stringValue(string), "stringValue"))) {
                add(Rule.STRING_VALUE, path, "two reads give different strings");
            }
        }

        private void number(N number, NormalizedPath path) {
            JsonNumber value = checked(model.numberValue(number), "numberValue");
            JsonDecimal exact = checked(value.exactValue(), "JsonNumber.exactValue");
            if (!exact.equals(checked(model.numberValue(number), "numberValue").exactValue())) {
                add(Rule.NUMBER_VALUE, path, "two reads give different exact values");
            }
            compare(value, value, path);
            compare(value, JsonNumber.of(exact), path);
            int from = Math.max(0, numbers.size() - NUMBER_PAIRS);
            for (JsonNumber other : numbers.subList(from, numbers.size())) {
                compare(value, other, path);
            }
            numbers.add(value);
        }

        /// Compares two numbers in both orders with the comparison of the exact values.
        private void compare(JsonNumber a, JsonNumber b, NormalizedPath path) {
            int expected = a.exactValue().compareTo(b.exactValue());
            int forward = Integer.signum(model.compareNumbers(a, b));
            int backward = Integer.signum(model.compareNumbers(b, a));
            if (forward != expected || backward != -expected) {
                add(Rule.COMPARE_NUMBERS, path, "compareNumbers gives " + forward + " for " + a.exactValue() + " and "
                        + b.exactValue() + ", and " + backward + " in the other order; the exact values give " + expected);
            }
        }

        /// Compares a value with itself and with earlier values, in both orders, with the default method.
        private void equal(N node, NormalizedPath path) {
            int from = Math.max(0, values.size() - VALUE_PAIRS);
            List<N> others = new ArrayList<>(values.subList(from, values.size()));
            others.add(node);
            for (N other : others) {
                boolean expected = reference.equal(node, other);
                if (model.equal(node, other) != expected || model.equal(other, node) != expected) {
                    add(Rule.EQUAL, path, "equal does not give " + expected + ", as the default method does, for a value of kind "
                            + model.kind(other));
                }
            }
            values.add(node);
        }

        /// Pushes children so that the first child is the next visit.
        private void push(List<Visit<N>> children) {
            for (int i = children.size() - 1; i >= 0; i--) {
                pending.push(children.get(i));
            }
        }
    }

    private static boolean equalNodes(Object a, Object b) {
        return a.equals(b) && a.hashCode() == b.hashCode();
    }

    private static boolean sameNames(List<JsonString> a, List<JsonString> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!JsonString.equal(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static <N> boolean sameValues(List<N> a, List<N> b) {
        // The callers compare lists of the same size.
        for (int i = 0; i < a.size(); i++) {
            if (!equalNodes(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /// Tells if the name at an index is the first occurrence of the name.
    private static boolean first(List<JsonString> names, int index) {
        for (int i = 0; i < index; i++) {
            if (JsonString.equal(names.get(i), names.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static int occurrences(List<JsonString> names, JsonString name) {
        int count = 0;
        for (JsonString other : names) {
            if (JsonString.equal(other, name)) {
                count++;
            }
        }
        return count;
    }

    private static String text(List<JsonString> names) {
        return names.stream().map(JsonString::copyOf).toList().toString();
    }
}
