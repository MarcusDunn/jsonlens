package ca.marcusdunn.jsonlens.mergepatch;

import ca.marcusdunn.jsonlens.model.JsonEditor;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Property;
import java.util.ArrayList;
import java.util.List;

/// Changes the objects of the target in place, through a [JsonEditor].
///
/// The merge keeps the changes of each object in a list. It makes them only after it found all
/// changes without an error. A change never removes or replaces an object that another change
/// edits: the merge edits an object only if the patch merges into it, and then the parent keeps
/// it. Thus the order of the changes has no effect.
///
/// @param <P> the node type of the model of the patch document
/// @param <N> the node type of the target document
/// @param <M> the type of the target model
final class InPlaceMerger<P, N, M extends JsonModel<N> & JsonFactory<N> & JsonEditor<N>> extends Merger<P, N, M> {

    /// The changes of each object, in the order in which the merge completed the objects.
    private final List<Runnable> changes = new ArrayList<>();

    InPlaceMerger(JsonModel<P> patchModel, M model) {
        super(patchModel, model);
    }

    @Override
    Maybe<N> complete(N object, Frame frame) {
        changes.add(() -> {
            for (String name : frame.removals) {
                model.removeMember(object, JsonString.of(name));
            }
            for (Property<N> member : frame.puts.values()) {
                model.putMember(object, member.name(), member.value());
            }
        });
        // The parent keeps the object: the changes are in the object.
        return Maybe.none();
    }

    @Override
    void commit() {
        for (Runnable change : changes) {
            change.run();
        }
    }
}
