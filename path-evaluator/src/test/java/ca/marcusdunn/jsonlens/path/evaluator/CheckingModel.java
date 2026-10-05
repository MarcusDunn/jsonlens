package ca.marcusdunn.jsonlens.path.evaluator;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Property;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * A model that checks the promises of the evaluator: it never passes null, and it calls a
 * method that is specific to a kind only for a node of that kind. It also never asks for an
 * array index outside the array. A broken promise fails the test.
 */
final class CheckingModel<N> implements JsonModel<N>, JsonFactory<N> {

    private final JsonModel<N> model;
    private final @Nullable JsonFactory<N> factory;

    <M extends JsonModel<N> & JsonFactory<N>> CheckingModel(M model) {
        this(model, model);
    }

    private CheckingModel(JsonModel<N> model, @Nullable JsonFactory<N> factory) {
        this.model = model;
        this.factory = factory;
    }

    /** A checking model for a read-only model. The evaluator must not build nodes with it. */
    static <N> CheckingModel<N> readOnly(JsonModel<N> model) {
        return new CheckingModel<>(model, null);
    }

    private JsonFactory<N> factory() {
        if (factory == null) {
            throw new AssertionError("the evaluator built a node with a read-only model");
        }
        return factory;
    }

    @Override
    public N string(JsonString value) {
        return factory().string(value);
    }

    @Override
    public Maybe<N> number(JsonNumber value) {
        return factory().number(value);
    }

    @Override
    public N bool(boolean value) {
        return factory().bool(value);
    }

    @Override
    public N nullValue() {
        return factory().nullValue();
    }

    @Override
    public N array(java.util.List<N> elements) {
        return factory().array(elements);
    }

    @Override
    public N object(java.util.List<ca.marcusdunn.jsonlens.model.Property<N>> members) {
        return factory().object(members);
    }

    private N check(@Nullable N node, JsonKind... kinds) {
        if (node == null) {
            throw new AssertionError("the evaluator passed null to the JsonModel");
        }
        JsonKind kind = model.kind(node);
        for (JsonKind allowed : kinds) {
            if (kind == allowed) {
                return node;
            }
        }
        throw new AssertionError("the evaluator called a " + kinds[0] + " method for a node of kind " + kind);
    }

    @Override
    public JsonKind kind(N node) {
        return model.kind(check(node, JsonKind.values()));
    }

    @Override
    public int arrayLength(N array) {
        return model.arrayLength(check(array, JsonKind.ARRAY));
    }

    @Override
    public Maybe<N> element(N array, int index) {
        N checked = check(array, JsonKind.ARRAY);
        if (index < 0 || index >= model.arrayLength(checked)) {
            throw new AssertionError("the evaluator asked for index " + index + " outside the array");
        }
        return model.element(checked, index);
    }

    @Override
    public int memberCount(N object) {
        return model.memberCount(check(object, JsonKind.OBJECT));
    }

    @Override
    public Maybe<N> member(N object, JsonString name) {
        if (name == null) {
            throw new AssertionError("the evaluator passed a null name to the JsonModel");
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
        int exact = a.exactValue().compareTo(b.exactValue());
        if (Integer.signum(result) != exact) {
            throw new AssertionError("compareNumbers(" + a.exactValue() + ", " + b.exactValue() + ") is " + result);
        }
        return result;
    }
}
