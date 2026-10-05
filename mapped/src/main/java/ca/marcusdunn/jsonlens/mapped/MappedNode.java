package ca.marcusdunn.jsonlens.mapped;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// A JSON value in a [MappedJson].
///
/// A node is only a position in the structural index of its [MappedJson]. It holds no copy of
/// the value. Only [MappedJson] makes nodes. Each read makes a new node, but two nodes for the
/// same value are equal: they have the same [MappedJson] and the same position.
public final class MappedNode {

    final MappedJson json;
    final int index;

    MappedNode(MappedJson json, int index) {
        this.json = json;
        this.index = index;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof MappedNode node && node.json == json && node.index == index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(json, index);
    }
}
