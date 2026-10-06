package ca.marcusdunn.jsonlens.mergepatch;

import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Property;
import java.util.ArrayList;
import java.util.List;

/// Builds a changed copy of the target through a [JsonFactory]. It never changes a node.
///
/// The merge builds a new object for each object of the target that changes, and thus for each of
/// its ancestors (path copying). A new object refers to the same unchanged members as the old
/// object, so all values off the changed paths are shared, not copied. An object without changes
/// stays the same node.
///
/// @param <P> the node type of the model of the patch document
/// @param <N> the node type of the target document
/// @param <M> the type of the target model
final class CopyingMerger<P, N, M extends JsonModel<N> & JsonFactory<N>> extends Merger<P, N, M> {

    CopyingMerger(JsonModel<P> patchModel, M model) {
        super(patchModel, model);
    }

    @Override
    Maybe<N> complete(N object, Frame frame) {
        if (frame.puts.isEmpty() && frame.removals.isEmpty()) {
            return Maybe.none();
        }
        List<Property<N>> members = new ArrayList<>();
        for (Property<N> member : model.members(object).toList()) {
            String name = JsonString.copyOf(member.name());
            // A replaced member keeps its position.
            Property<N> put = frame.puts.remove(name);
            if (put != null) {
                members.add(put);
            } else if (!frame.removals.contains(name)) {
                members.add(member);
            }
        }
        // The new members come after the members of the target, in the order of the patch.
        members.addAll(frame.puts.values());
        return Maybe.some(model.object(members));
    }

    @Override
    void commit() {
        // The target did not change.
    }
}
