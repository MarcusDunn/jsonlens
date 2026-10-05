package ca.marcusdunn.jsonlens.path.evaluator;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Property;
import java.math.BigDecimal;
import java.util.stream.Stream;

/** A model that counts the calls that read children, to show how much work an evaluation does. */
final class CountingModel<N> implements JsonModel<N> {

    private final JsonModel<N> model;
    int reads;

    CountingModel(JsonModel<N> model) {
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
        reads++;
        return model.element(array, index);
    }

    @Override
    public int memberCount(N object) {
        return model.memberCount(object);
    }

    @Override
    public Maybe<N> member(N object, JsonString name) {
        reads++;
        return model.member(object, name);
    }

    @Override
    public Stream<Property<N>> members(N object) {
        reads++;
        return model.members(object);
    }

    @Override
    public MemberCursor<N> memberCursor(N object) {
        reads++;
        MemberCursor<N> cursor = model.memberCursor(object);
        // Each member is a read, so that an early stop in an object is visible.
        return new MemberCursor<>() {
            @Override
            public boolean next() {
                reads++;
                return cursor.next();
            }

            @Override
            public JsonString name() {
                return cursor.name();
            }

            @Override
            public N value() {
                return cursor.value();
            }
        };
    }

    @Override
    public JsonString stringValue(N string) {
        return model.stringValue(string);
    }

    @Override
    public JsonNumber numberValue(N number) {
        return model.numberValue(number);
    }

    @Override
    public int compareNumbers(JsonNumber a, JsonNumber b) {
        return model.compareNumbers(a, b);
    }
}
