package ca.marcusdunn.jsonlens.patch;

import ca.marcusdunn.jsonlens.model.JsonEditor;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import java.util.ArrayDeque;
import java.util.Deque;

/// Changes the document in place, through a [JsonEditor], and keeps an undo log.
///
/// Each change pushes the action that reverses it. If an operation fails, the applier runs the
/// actions in reverse order, so that the document gets its original value again (RFC 6902,
/// Section 5). A replaced root needs no undo action: after an error, the caller keeps the
/// original root, and the undo actions restore its content.
///
/// `copy` makes one deep copy, because the two locations of a document that can change must not
/// share a node.
///
/// @param <P> the node type of the model of the patch document
/// @param <N> the node type of the target document
/// @param <M> the type of the target model
final class InPlaceApplier<P, N, M extends JsonModel<N> & JsonFactory<N> & JsonEditor<N>> extends Applier<P, N, M> {

    private final Deque<Runnable> undo = new ArrayDeque<>();

    InPlaceApplier(JsonModel<P> patchModel, M model, N root, JsonPatch.Limits limits) {
        super(patchModel, model, root, limits);
    }

    @Override
    void putMember(Slot<N> target, JsonString name, N value) {
        N object = target.parent();
        if (model.putMember(object, name, value) instanceof Maybe.Some<N>(N replaced)) {
            undo.push(() -> model.putMember(object, name, replaced));
        } else {
            undo.push(() -> model.removeMember(object, name));
        }
    }

    @Override
    N removeMember(Slot<N> target, JsonString name) {
        N object = target.parent();
        // The slot exists, so the member exists. A model that is not well-formed gives the parent.
        N removed = model.removeMember(object, name).orElse(object);
        undo.push(() -> model.putMember(object, name, removed));
        return removed;
    }

    @Override
    void insertElement(Slot<N> target, N value) {
        model.insertElement(target.parent(), target.index(), value);
        undo.push(() -> model.removeElement(target.parent(), target.index()));
    }

    @Override
    void setElement(Slot<N> target, N value) {
        N previous = model.setElement(target.parent(), target.index(), value);
        undo.push(() -> model.setElement(target.parent(), target.index(), previous));
    }

    @Override
    N removeElement(Slot<N> target) {
        N removed = model.removeElement(target.parent(), target.index());
        undo.push(() -> model.insertElement(target.parent(), target.index(), removed));
        return removed;
    }

    @Override
    Maybe<N> duplicate(N value) {
        return model.copyOf(model, value);
    }

    @Override
    void rollback() {
        while (!undo.isEmpty()) {
            undo.pop().run();
        }
    }
}
