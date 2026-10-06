package ca.marcusdunn.jsonlens.patch;

import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Property;
import ca.marcusdunn.jsonlens.pointer.JsonPointer;
import java.util.ArrayList;
import java.util.List;

/// Builds a changed copy of the document through a [JsonFactory]. It never changes a node.
///
/// A change builds a new container for the changed parent, and then a new container for each
/// ancestor up to the root (path copying). Each new container refers to the same children as the
/// old one, so all values off the path are shared, not copied. The original document keeps its
/// value, also after an error, so the applier needs no undo log.
///
/// `copy` shares the source node: no node changes, so two locations can share one.
///
/// @param <P> the node type of the model of the patch document
/// @param <N> the node type of the target document
/// @param <M> the type of the target model
final class CopyingApplier<P, N, M extends JsonModel<N> & JsonFactory<N>> extends Applier<P, N, M> {

    CopyingApplier(JsonModel<P> patchModel, M model, N root, JsonPatch.Limits limits) {
        super(patchModel, model, root, limits);
    }

    @Override
    void putMember(Slot<N> target, JsonString name, N value) {
        replaceParent(target, objectWith(target.parent(), name, value));
    }

    @Override
    N removeMember(Slot<N> target, JsonString name) {
        N object = target.parent();
        List<Property<N>> members = new ArrayList<>();
        // The slot exists, so the member exists. A model that is not well-formed gives the parent.
        N removed = model.member(object, name).orElse(object);
        for (Property<N> member : model.members(object).toList()) {
            if (!JsonString.equal(member.name(), name)) {
                members.add(member);
            }
        }
        replaceParent(target, model.object(members));
        return removed;
    }

    @Override
    void insertElement(Slot<N> target, N value) {
        List<N> elements = elements(target.parent());
        elements.add(target.index(), value);
        replaceParent(target, model.array(elements));
    }

    @Override
    void setElement(Slot<N> target, N value) {
        replaceParent(target, arrayWith(target.parent(), target.index(), value));
    }

    @Override
    N removeElement(Slot<N> target) {
        List<N> elements = elements(target.parent());
        N removed = elements.remove(target.index());
        replaceParent(target, model.array(elements));
        return removed;
    }

    @Override
    Maybe<N> duplicate(N value) {
        return Maybe.some(value);
    }

    @Override
    void rollback() {
        // The original document did not change.
    }

    /// Replaces the container of a slot with a new container, and builds each ancestor again.
    private void replaceParent(Slot<N> target, N container) {
        JsonPointer path = target.parentPath();
        // The slot exists, so its path resolves. A model that is not well-formed gives the root.
        List<N> ancestors = path.resolvePath(root, model).orElse(List.of(root));
        List<String> tokens = path.tokens();
        N current = container;
        for (int depth = tokens.size() - 1; depth >= 0; depth--) {
            N parent = ancestors.get(depth);
            String token = tokens.get(depth);
            current = model.kind(parent) == JsonKind.OBJECT
                    ? objectWith(parent, JsonString.of(token), current)
                    : arrayWith(parent, JsonPointer.arrayIndex(token).orElse(0), current);
        }
        root = current;
    }

    /// A new object with a member value replaced, or a member added at the end.
    private N objectWith(N object, JsonString name, N value) {
        List<Property<N>> members = new ArrayList<>();
        boolean replaced = false;
        for (Property<N> member : model.members(object).toList()) {
            if (JsonString.equal(member.name(), name)) {
                members.add(new Property<>(member.name(), value));
                replaced = true;
            } else {
                members.add(member);
            }
        }
        if (!replaced) {
            members.add(new Property<>(name, value));
        }
        return model.object(members);
    }

    /// A new array with an element replaced.
    private N arrayWith(N array, int index, N value) {
        List<N> elements = elements(array);
        elements.set(index, value);
        return model.array(elements);
    }

    /// The elements of an array, in a new list.
    private List<N> elements(N array) {
        int length = model.arrayLength(array);
        List<N> elements = new ArrayList<>(length + 1);
        for (int i = 0; i < length; i++) {
            // A well-formed model has each element up to the length.
            elements.add(model.element(array, i).orElse(array));
        }
        return elements;
    }
}
