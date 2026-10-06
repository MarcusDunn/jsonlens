package ca.marcusdunn.jsonlens.mergepatch;

import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Finds an object with duplicate member names in a patch document, without recursion.
///
/// The finder visits each value of the document one time. For each object, it keeps the names in
/// buckets by hash code. Thus it compares the names by their scalar values, without a decoded
/// copy, and the time is linear in the number of members.
///
/// @param <P> the node type of the model of the document
final class DuplicateFinder<P> {

    private final JsonModel<P> model;
    /// The values that the finder must visit.
    private final Deque<Visit<P>> pending = new ArrayDeque<>();

    DuplicateFinder(JsonModel<P> model) {
        this.model = model;
    }

    /// A value that the finder must visit, and its location.
    private record Visit<P>(P node, Location at) {}

    /// Finds an object with duplicate member names.
    ///
    /// @param root the root of the document
    /// @return the [MergePatchError.DuplicateName] of the first object with a duplicate name that the
    ///     finder visits, or [Maybe.None] if all names are unique
    Maybe<MergePatchError> find(P root) {
        pending.push(new Visit<>(root, Location.ROOT));
        while (!pending.isEmpty()) {
            Visit<P> visit = pending.pop();
            JsonKind kind = model.kind(visit.node());
            if (kind == JsonKind.OBJECT) {
                Maybe<MergePatchError> duplicate = object(visit);
                if (duplicate.isSome()) {
                    return duplicate;
                }
            } else if (kind == JsonKind.ARRAY) {
                // A well-formed model gives each element below the length, and no other element.
                // For another model, the copy of the array fails later, with an error value.
                for (int index = 0; model.element(visit.node(), index) instanceof Maybe.Some<P>(P element); index++) {
                    pending.push(new Visit<>(element, visit.at().child(JsonString.of(Integer.toString(index)))));
                }
            }
        }
        return Maybe.none();
    }

    /// Finds a duplicate name in an object, and adds the members to the values to visit.
    private Maybe<MergePatchError> object(Visit<P> visit) {
        Map<Integer, List<JsonString>> names = new HashMap<>();
        for (MemberCursor<P> cursor = model.memberCursor(visit.node()); cursor.next(); ) {
            JsonString name = cursor.name();
            List<JsonString> bucket = names.computeIfAbsent(JsonString.hash(name), hash -> new ArrayList<>());
            for (JsonString earlier : bucket) {
                if (JsonString.equal(earlier, name)) {
                    return Maybe.some(new MergePatchError.DuplicateName(visit.at().tokens(), JsonString.copyOf(name)));
                }
            }
            bucket.add(name);
            pending.push(new Visit<>(cursor.value(), visit.at().child(name)));
        }
        return Maybe.none();
    }
}
