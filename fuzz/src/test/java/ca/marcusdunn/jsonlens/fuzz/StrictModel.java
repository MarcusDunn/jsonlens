package ca.marcusdunn.jsonlens.fuzz;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Property;
import java.util.stream.Stream;

/**
 * A model that fails if the evaluator breaks a promise: a null argument, a method for the wrong
 * kind of node, or an array index outside the array.
 */
final class StrictModel<N> implements JsonModel<N> {

    private final JsonModel<N> model;

    StrictModel(JsonModel<N> model) {
        this.model = model;
    }

    private N check(N node, JsonKind kind) {
        if (node == null) {
            throw new AssertionError("null node");
        }
        if (kind != null && model.kind(node) != kind) {
            throw new AssertionError("a " + kind + " method for a " + model.kind(node) + " node");
        }
        return node;
    }

    @Override
    public JsonKind kind(N node) {
        return model.kind(check(node, null));
    }

    @Override
    public int arrayLength(N array) {
        return model.arrayLength(check(array, JsonKind.ARRAY));
    }

    @Override
    public Maybe<N> element(N array, int index) {
        if (index < 0 || index >= model.arrayLength(check(array, JsonKind.ARRAY))) {
            throw new AssertionError("index " + index + " outside the array");
        }
        return model.element(array, index);
    }

    @Override
    public int memberCount(N object) {
        return model.memberCount(check(object, JsonKind.OBJECT));
    }

    @Override
    public Maybe<N> member(N object, JsonString name) {
        if (name == null) {
            throw new AssertionError("null name");
        }
        return model.member(check(object, JsonKind.OBJECT), name);
    }

    @Override
    public Stream<Property<N>> members(N object) {
        return model.members(check(object, JsonKind.OBJECT));
    }

    @Override
    public MemberCursor<N> memberCursor(N object) {
        return model.memberCursor(check(object, JsonKind.OBJECT));
    }

    @Override
    public JsonString stringValue(N string) {
        return model.stringValue(check(string, JsonKind.STRING));
    }

    @Override
    public JsonNumber numberValue(N number) {
        return model.numberValue(check(number, JsonKind.NUMBER));
    }

    /** Delegates, and checks the invariant: the result has the sign of the comparison of the exact values. */
    @Override
    public int compareNumbers(JsonNumber a, JsonNumber b) {
        int result = model.compareNumbers(a, b);
        if (Integer.signum(result) != a.exactValue().compareTo(b.exactValue())) {
            throw new AssertionError("compareNumbers(" + a.exactValue() + ", " + b.exactValue() + ") is " + result);
        }
        return result;
    }
}
