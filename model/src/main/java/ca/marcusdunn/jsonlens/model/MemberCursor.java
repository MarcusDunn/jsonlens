package ca.marcusdunn.jsonlens.model;

/// Walks the members of one object, in order: the result of [JsonModel#memberCursor(Object)].
///
/// A cursor is for one walk, by one thread. It makes no list of the members and no [Property]
/// for each member, so a walk of a large object allocates almost nothing:
///
/// ```java
/// for (MemberCursor<N> cursor = model.memberCursor(object); cursor.next(); ) {
///     use(cursor.name(), cursor.value());
/// }
/// ```
///
/// @param <N> the node type of the model
public interface MemberCursor<N> {

    /// Moves to the next member.
    ///
    /// @return `true` if the cursor is on a member; `false` at the end, and for each later call
    boolean next();

    /// Returns the name of the current member. Call it only after [#next()] returned `true`.
    ///
    /// The caller can keep the name after the cursor moves, for example in a Normalized Path, so a
    /// model must not change a name that it gave.
    ///
    /// @return the member name
    JsonString name();

    /// Returns the value of the current member. Call it only after [#next()] returned `true`.
    ///
    /// @return the member value
    N value();

    /// Returns a cursor over a sequence of properties.
    ///
    /// Use it for a model whose objects are few or small, for example in a test. A model of large
    /// objects walks its own representation instead, so that a walk makes no [Property] for each
    /// member.
    ///
    /// @param properties the members, in order
    /// @param <N> the node type of the model
    /// @return a new cursor before the first property
    static <N> MemberCursor<N> of(Iterable<Property<N>> properties) {
        return new PropertyCursor<>(properties.iterator());
    }
}
