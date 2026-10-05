package ca.marcusdunn.jsonlens.testkit;

import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;

/// A model that passes on only the six required methods of another model. All other methods are
/// the defaults of [JsonModel], so this model gives the reference result for each override.
///
/// @param <N> the node type of the model
final class PrimitivesOnly<N> implements JsonModel<N> {

    private final JsonModel<N> model;

    PrimitivesOnly(JsonModel<N> model) {
        this.model = model;
    }

    @Override
    public JsonKind kind(N node) {
        return model.kind(node);
    }

    @Override
    public int arrayLength(N array) {
        return model.arrayLength(array);
    }

    @Override
    public Maybe<N> element(N array, int index) {
        return model.element(array, index);
    }

    @Override
    public MemberCursor<N> memberCursor(N object) {
        return model.memberCursor(object);
    }

    @Override
    public JsonString stringValue(N string) {
        return model.stringValue(string);
    }

    @Override
    public JsonNumber numberValue(N number) {
        return model.numberValue(number);
    }
}
