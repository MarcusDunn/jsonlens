package ca.marcusdunn.jsonlens.model;

import java.util.Iterator;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// The [MemberCursor] of [MemberCursor#of(Iterable)]: it walks a sequence of properties.
///
/// @param <N> the node type of the model
final class PropertyCursor<N> implements MemberCursor<N> {

    private final Iterator<Property<N>> properties;
    private @Nullable Property<N> current;

    PropertyCursor(Iterator<Property<N>> properties) {
        this.properties = properties;
    }

    @Override
    public boolean next() {
        current = properties.hasNext() ? properties.next() : null;
        return current != null;
    }

    @Override
    public JsonString name() {
        // A call before next() returned true breaks the contract of MemberCursor.
        return Objects.requireNonNull(current).name();
    }

    @Override
    public N value() {
        return Objects.requireNonNull(current).value();
    }
}
