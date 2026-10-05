package ca.marcusdunn.jsonlens.model;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/// The source of the default [JsonModel#members(Object)] stream: one [Property] for each member of
/// a [MemberCursor].
///
/// @param <N> the node type of the model
final class CursorSpliterator<N> extends Spliterators.AbstractSpliterator<Property<N>> {

    private final MemberCursor<N> cursor;

    CursorSpliterator(MemberCursor<N> cursor) {
        super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL);
        this.cursor = cursor;
    }

    @Override
    public boolean tryAdvance(Consumer<? super Property<N>> action) {
        if (!cursor.next()) {
            return false;
        }
        action.accept(new Property<>(cursor.name(), cursor.value()));
        return true;
    }
}
