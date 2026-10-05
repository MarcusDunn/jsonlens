package ca.marcusdunn.jsonlens.patch;

import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.pointer.JsonPointer;
import ca.marcusdunn.jsonlens.pointer.PointerError;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Applies the operations of a patch: the semantics of RFC 6902, Section 4.
///
/// This class checks each location, gives each error, and decides each change. A subclass makes
/// the changes: [InPlaceApplier] changes the document through a `JsonEditor`, and
/// [CopyingApplier] builds a changed copy through a [JsonFactory].
///
/// `add`, `replace`, and `test` read their values in the model of the patch document. `add` and
/// `replace` copy the value once into the target model, so that the target shares no node with
/// the patch. `move` moves the node itself, and `test` compares without a copy.
///
/// @param <P> the node type of the model of the patch document
/// @param <N> the node type of the target document
/// @param <M> the type of the target model
abstract sealed class Applier<P, N, M extends JsonModel<N> & JsonFactory<N>> permits InPlaceApplier, CopyingApplier {

    private final JsonModel<P> patchModel;
    final M model;
    N root;

    Applier(JsonModel<P> patchModel, M model, N root) {
        this.patchModel = patchModel;
        this.model = model;
        this.root = root;
    }

    /// A location in a container: a member name of an object, or an index of an array.
    ///
    /// @param parent the object or the array
    /// @param parentPath the pointer to the container
    /// @param name the member name, or `null` for an array
    /// @param index the array index
    record Slot<N>(N parent, JsonPointer parentPath, @Nullable JsonString name, int index) {}

    // -------------------------------------------------------------------------------------------
    // Changes: the subclasses make them
    // -------------------------------------------------------------------------------------------

    /// Adds a member to the object of a slot, or replaces the value of the member.
    abstract void putMember(Slot<N> target, JsonString name, N value);

    /// Removes the member of a slot, which exists, and returns its value.
    abstract N removeMember(Slot<N> target, JsonString name);

    /// Inserts an element at the index of a slot.
    abstract void insertElement(Slot<N> target, N value);

    /// Replaces the element at the index of a slot.
    abstract void setElement(Slot<N> target, N value);

    /// Removes the element at the index of a slot, and returns it.
    abstract N removeElement(Slot<N> target);

    /// The value that `copy` adds: a value that shares no node with the source if necessary.
    abstract Maybe<N> duplicate(N value);

    /// Reverses the changes after an error, if the subclass changed the document.
    abstract void rollback();

    // -------------------------------------------------------------------------------------------
    // Operations (RFC 6902, Section 4)
    // -------------------------------------------------------------------------------------------

    final Result<N, PatchError> apply(List<Operation<P>> operations) {
        for (int index = 0; index < operations.size(); index++) {
            switch (apply(operations.get(index), index)) {
                case Maybe.None<PatchError>() -> {}
                case Maybe.Some<PatchError>(PatchError error) -> {
                    rollback();
                    return Result.err(error);
                }
            }
        }
        return Result.ok(root);
    }

    private Maybe<PatchError> apply(Operation<P> operation, int index) {
        return switch (operation) {
            case Operation.Add<P> add -> add(add, index);
            case Operation.Remove<P> remove -> remove(remove.path(), index).fold(removed -> Maybe.none(), Maybe::some);
            case Operation.Replace<P> replace -> replace(replace, index);
            case Operation.Move<P> move -> move(move, index);
            case Operation.Copy<P> copy -> copy(copy, index);
            case Operation.Test<P> test -> test(test, index);
        };
    }

    private Maybe<PatchError> add(Operation.Add<P> add, int index) {
        if (!(model.copyOf(patchModel, add.value()) instanceof Maybe.Some<N>(N copy))) {
            return Maybe.some(new PatchError.ValueNotRepresentable(index));
        }
        return insert(add.path(), copy, index);
    }

    private Result<N, PatchError> remove(JsonPointer path, int index) {
        if (path.isRoot()) {
            return Result.err(new PatchError.RootRemoved(index));
        }
        return slot(path, false).mapErr(error -> (PatchError) new PatchError.PathNotFound(index, error)).map(this::remove);
    }

    private Maybe<PatchError> replace(Operation.Replace<P> replace, int index) {
        if (!(model.copyOf(patchModel, replace.value()) instanceof Maybe.Some<N>(N copy))) {
            return Maybe.some(new PatchError.ValueNotRepresentable(index));
        }
        if (replace.path().isRoot()) {
            root = copy;
            return Maybe.none();
        }
        return switch (slot(replace.path(), false)) {
            case Result.Err<Slot<N>, PointerError>(PointerError error) -> Maybe.some(new PatchError.PathNotFound(index, error));
            case Result.Ok<Slot<N>, PointerError>(Slot<N> target) -> {
                JsonString name = target.name();
                if (name != null) {
                    putMember(target, name, copy);
                } else {
                    setElement(target, copy);
                }
                yield Maybe.none();
            }
        };
    }

    private Maybe<PatchError> move(Operation.Move<P> move, int index) {
        JsonPointer from = move.from();
        if (from.isRoot()) {
            // The root always exists. A move of the root into a child is not possible.
            return move.path().isRoot() ? Maybe.none() : Maybe.some(new PatchError.MoveIntoChild(index));
        }
        return switch (slot(from, false)) {
            case Result.Err<Slot<N>, PointerError>(PointerError error) -> Maybe.some(new PatchError.FromNotFound(index, error));
            case Result.Ok<Slot<N>, PointerError>(Slot<N> source) -> {
                if (from.equals(move.path())) {
                    yield Maybe.none();
                }
                if (from.isProperPrefixOf(move.path())) {
                    yield Maybe.some(new PatchError.MoveIntoChild(index));
                }
                // The path is evaluated after the removal (Section 4.4). The node moves without a copy.
                yield insert(move.path(), remove(source), index);
            }
        };
    }

    private Maybe<PatchError> copy(Operation.Copy<P> copy, int index) {
        return switch (copy.from().resolve(root, model)) {
            case Result.Err<N, PointerError>(PointerError error) -> Maybe.some(new PatchError.FromNotFound(index, error));
            case Result.Ok<N, PointerError>(N value) -> duplicate(value) instanceof Maybe.Some<N>(N duplicate)
                    ? insert(copy.path(), duplicate, index)
                    : Maybe.some(new PatchError.ValueNotRepresentable(index));
        };
    }

    private Maybe<PatchError> test(Operation.Test<P> test, int index) {
        return switch (test.path().resolve(root, model)) {
            case Result.Err<N, PointerError>(PointerError error) -> Maybe.some(new PatchError.PathNotFound(index, error));
            case Result.Ok<N, PointerError>(N value) -> JsonModel.equal(model, value, patchModel, test.value())
                    ? Maybe.none()
                    : Maybe.some(new PatchError.TestFailed(index));
        };
    }

    // -------------------------------------------------------------------------------------------
    // Locations
    // -------------------------------------------------------------------------------------------

    /// Adds a node at a location, as `add` does (Section 4.1).
    private Maybe<PatchError> insert(JsonPointer path, N node, int index) {
        if (path.isRoot()) {
            root = node;
            return Maybe.none();
        }
        return switch (slot(path, true)) {
            case Result.Err<Slot<N>, PointerError>(PointerError error) -> Maybe.some(new PatchError.PathNotFound(index, error));
            case Result.Ok<Slot<N>, PointerError>(Slot<N> target) -> {
                JsonString name = target.name();
                if (name != null) {
                    putMember(target, name, node);
                } else {
                    insertElement(target, node);
                }
                yield Maybe.none();
            }
        };
    }

    /// Removes the value at an existing location, and returns it.
    private N remove(Slot<N> target) {
        JsonString name = target.name();
        return name != null ? removeMember(target, name) : removeElement(target);
    }

    /// Finds the container and the last token of a path that is not the root.
    ///
    /// For `add`, a missing member is a valid location, and an array index can be the length of
    /// the array or `-` (Section 4.1). For the other operations, the location must exist.
    private Result<Slot<N>, PointerError> slot(JsonPointer path, boolean adding) {
        JsonPointer parentPath = path.parent().orElse(JsonPointer.root());
        String token = path.lastToken().orElse("");
        return parentPath.resolve(root, model).flatMap(parent -> switch (model.kind(parent)) {
            case OBJECT -> memberSlot(parent, parentPath, token, adding);
            case ARRAY -> elementSlot(parent, parentPath, token, adding);
            default -> Result.<Slot<N>, PointerError>err(new PointerError.NotAContainer(parentPath));
        });
    }

    private Result<Slot<N>, PointerError> memberSlot(N object, JsonPointer parentPath, String token, boolean adding) {
        JsonString name = JsonString.of(token);
        if (!adding && model.member(object, name).isNone()) {
            return Result.err(new PointerError.MemberNotFound(parentPath, token));
        }
        if (model.hasDuplicate(object, name)) {
            return Result.err(new PointerError.DuplicateName(parentPath, token));
        }
        return Result.ok(new Slot<>(object, parentPath, name, -1));
    }

    private Result<Slot<N>, PointerError> elementSlot(N array, JsonPointer parentPath, String token, boolean adding) {
        int length = model.arrayLength(array);
        // "-" is the position after the last element: valid only to add (Section 4.1).
        boolean append = adding && token.equals("-");
        if (!append && !JsonPointer.isArrayIndex(token)) {
            return Result.err(new PointerError.InvalidIndex(parentPath, token));
        }
        // An index larger than the largest int is valid, but outside each array.
        Maybe<Integer> position = append ? Maybe.some(length) : JsonPointer.arrayIndex(token);
        if (!(position instanceof Maybe.Some<Integer>(Integer index)) || index > (adding ? length : length - 1)) {
            return Result.err(new PointerError.IndexOutOfRange(parentPath, token));
        }
        return Result.ok(new Slot<>(array, parentPath, null, index));
    }
}
