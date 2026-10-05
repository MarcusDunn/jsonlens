package ca.marcusdunn.jsonlens.model;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// A member of a JSON object: a name and a value (RFC 9535, Section 1.1).
///
/// The name is a [JsonString] in the representation of the model, so a model over bytes does not
/// have to decode each name. Use [JsonString#of(String)] for a `String` name.
///
/// Two properties are equal if their names have the same scalar values and their values are
/// equal.
///
/// @param name the member name
/// @param value the member value
/// @param <N> the node type of the JSON model
public record Property<N>(JsonString name, N value) {

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Property<?> property
                && JsonString.equal(name, property.name)
                && Objects.equals(value, property.value);
    }

    @Override
    public int hashCode() {
        return 31 * JsonString.hash(name) + Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Property[name=" + JsonString.copyOf(name) + ", value=" + value + "]";
    }
}
