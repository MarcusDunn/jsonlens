package ca.marcusdunn.jsonlens.model;

/// Changes the caller's JSON values in place.
///
/// A [JsonModel] reads values and a [JsonFactory] builds new values. A model that also implements
/// `JsonEditor` can change its objects and arrays without a copy. JSON Patch (RFC 6902) applies
/// its operations through this interface. A model for immutable values, for example
/// kotlinx.serialization, or for read-only values, for example a memory-mapped file, does not
/// implement it.
///
/// ## Contract
///
/// - **No exceptions.** For calls that obey the preconditions below, no method throws an
///   exception.
/// - **Preconditions.** The caller gives an object to the member methods and an array to the
///   element methods, and an index in the stated range. The values that the caller inserts are
///   nodes of the same model that are not already in the document (for example, from
///   [JsonFactory#copyOf(JsonModel, Object)], or a node that the caller removed).
/// - **Results.** Each method returns the value that it replaced or removed, so that the caller
///   can reverse the change. After a change, the read methods of the model give the new values.
/// - **Unique names.** An object has at most one member with a name. [#putMember] replaces the
///   value of an existing member.
///
/// The order of the members of an object is not significant (RFC 8259, Section 4). A model can
/// add a new member at any position.
///
/// @param <N> the node type of the caller's JSON representation
public interface JsonEditor<N> {

    /// Sets the value of a member. If the object has a member with the name, the method replaces
    /// its value. Otherwise, it adds a member.
    ///
    /// @param object a node of kind [JsonKind#OBJECT]
    /// @param name the member name
    /// @param value the new value
    /// @return the previous value, or [Maybe.None] if the object had no member with the name
    Maybe<N> putMember(N object, JsonString name, N value);

    /// Removes a member.
    ///
    /// @param object a node of kind [JsonKind#OBJECT]
    /// @param name the member name
    /// @return the removed value, or [Maybe.None] if the object had no member with the name
    Maybe<N> removeMember(N object, JsonString name);

    /// Inserts an element. The elements at and after the index move one position to the right.
    ///
    /// @param array a node of kind [JsonKind#ARRAY]
    /// @param index the index of the new element: from 0 to the length of the array. The length
    ///     appends the element.
    /// @param value the new element
    void insertElement(N array, int index, N value);

    /// Replaces an element.
    ///
    /// @param array a node of kind [JsonKind#ARRAY]
    /// @param index the index of the element: from 0 to the length of the array minus 1
    /// @param value the new element
    /// @return the previous element
    N setElement(N array, int index, N value);

    /// Removes an element. The elements after the index move one position to the left.
    ///
    /// @param array a node of kind [JsonKind#ARRAY]
    /// @param index the index of the element: from 0 to the length of the array minus 1
    /// @return the removed element
    N removeElement(N array, int index);
}
