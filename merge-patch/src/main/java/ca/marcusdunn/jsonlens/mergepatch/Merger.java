package ca.marcusdunn.jsonlens.mergepatch;

import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Property;
import ca.marcusdunn.jsonlens.model.Result;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Merges a patch into a target: the function `MergePatch(Target, Patch)` of RFC 7396, Section 2.
///
/// The RFC defines the function with recursion. This class uses a stack of frames, so a deep
/// document cannot overflow the stack. Each frame is an object of the patch and the object of the
/// target that it merges into. The class first finds all changes and copies all values. It makes
/// the changes only when no error can occur, so an error never leaves a part of the changes.
///
/// This class decides each change. A subclass makes the changes: [InPlaceMerger] changes the
/// objects of the target through a `JsonEditor`, and [CopyingMerger] builds new objects through
/// a [JsonFactory].
///
/// @param <P> the node type of the model of the patch document
/// @param <N> the node type of the target document
/// @param <M> the type of the target model
abstract sealed class Merger<P, N, M extends JsonModel<N> & JsonFactory<N>> permits InPlaceMerger, CopyingMerger {

    private final JsonModel<P> patchModel;
    final M model;

    Merger(JsonModel<P> patchModel, M model) {
        this.patchModel = patchModel;
        this.model = model;
    }

    /// An object of the patch, the object of the target that it merges into, and the changes.
    final class Frame {

        /// The object of the target, or [Maybe.None] if the target has no object at this location.
        /// Then the patch merges into an empty object (Section 2).
        final Maybe<N> target;
        /// The members of the object of the patch.
        final MemberCursor<P> patch;
        /// The location of the two objects.
        final Location at;
        /// The new members, by name, in the order of the patch.
        final Map<String, Property<N>> puts = new LinkedHashMap<>();
        /// The names of the members to remove. Each name is a member of the target.
        final Set<String> removals = new LinkedHashSet<>();

        Frame(Maybe<N> target, P patch, Location at) {
            this.target = target;
            this.patch = patchModel.memberCursor(patch);
            this.at = at;
        }

        void put(JsonString name, N value) {
            puts.put(JsonString.copyOf(name), new Property<>(name, value));
        }
    }

    // -------------------------------------------------------------------------------------------
    // Changes: the subclasses make them
    // -------------------------------------------------------------------------------------------

    /// Completes the merge into an object of the target.
    ///
    /// @param object the object of the target
    /// @param frame the frame of the object, with all its changes
    /// @return a new object that replaces the object in its parent, or [Maybe.None] if the parent
    ///     keeps the object
    abstract Maybe<N> complete(N object, Frame frame);

    /// Makes the changes. The merge calls it only after it found all changes without an error.
    abstract void commit();

    // -------------------------------------------------------------------------------------------
    // MergePatch(Target, Patch) (Section 2)
    // -------------------------------------------------------------------------------------------

    /// Merges a patch into a target.
    ///
    /// @param target the root of the target
    /// @param patch the root of the patch document
    /// @return the root of the result, or the first error
    final Result<N, MergePatchError> merge(N target, P patch) {
        if (patchModel.kind(patch) != JsonKind.OBJECT) {
            // A patch that is not an object replaces the whole target.
            return copy(patch, Location.ROOT);
        }
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(object(target), patch, Location.ROOT));
        while (true) {
            // The loop returns when it removes the root frame, so there is always a frame.
            Frame top = Objects.requireNonNull(stack.peek());
            if (top.patch.next()) {
                switch (member(stack, top)) {
                    case Maybe.Some<MergePatchError>(MergePatchError error) -> {
                        return Result.err(error);
                    }
                    case Maybe.None<MergePatchError>() -> {
                        continue;
                    }
                }
            }
            stack.pop();
            Maybe<N> built = switch (top.target) {
                // A target that is not an object becomes an empty object: the result has only the
                // members of the patch.
                case Maybe.None<N>() -> Maybe.some(model.object(new ArrayList<>(top.puts.values())));
                case Maybe.Some<N>(N object) -> complete(object, top);
            };
            Frame parent = stack.peek();
            if (parent == null) {
                commit();
                return Result.ok(built.orElse(target));
            }
            if (built instanceof Maybe.Some<N>(N value)) {
                parent.put(top.at.token(), value);
            }
        }
    }

    /// Merges the current member of the object of the patch of a frame.
    private Maybe<MergePatchError> member(Deque<Frame> stack, Frame frame) {
        JsonString name = frame.patch.name();
        P value = frame.patch.value();
        if (frame.target instanceof Maybe.Some<N>(N object) && model.hasDuplicate(object, name)) {
            return Maybe.some(new MergePatchError.TargetDuplicateName(frame.at.tokens(), JsonString.copyOf(name)));
        }
        Maybe<N> current = frame.target.flatMap(object -> model.member(object, name));
        Location at = frame.at.child(name);
        switch (patchModel.kind(value)) {
            case NULL -> {
                // null removes the member. A member that is not in the target needs no change.
                if (current.isSome()) {
                    frame.removals.add(JsonString.copyOf(name));
                }
            }
            // An object merges into the member. A member that is not an object becomes an empty
            // object, so the null members of the patch object do not get into the result.
            case OBJECT -> stack.push(new Frame(current.flatMap(this::object), value, at));
            // Other values replace the member. An array replaces the member as a whole.
            default -> {
                switch (copy(value, at)) {
                    case Result.Ok<N, MergePatchError>(N copy) -> frame.put(name, copy);
                    case Result.Err<N, MergePatchError>(MergePatchError error) -> {
                        return Maybe.some(error);
                    }
                }
            }
        }
        return Maybe.none();
    }

    /// The node, if it is an object.
    private Maybe<N> object(N node) {
        return model.kind(node) == JsonKind.OBJECT ? Maybe.some(node) : Maybe.none();
    }

    /// A copy of a value of the patch in the target model.
    private Result<N, MergePatchError> copy(P value, Location at) {
        return model.copyOf(patchModel, value) instanceof Maybe.Some<N>(N copy)
                ? Result.ok(copy)
                : Result.err(new MergePatchError.ValueNotRepresentable(at.tokens()));
    }
}
