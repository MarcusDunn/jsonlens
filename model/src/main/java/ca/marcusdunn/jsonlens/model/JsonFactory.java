package ca.marcusdunn.jsonlens.model;

import java.util.List;

/// Builds nodes of the caller's JSON representation.
///
/// A [JsonModel] gives read access, and it is enough for all standard functions of RFC 9535. A
/// model that also implements `JsonFactory` can run function extensions that make new values,
/// for example a function that returns a new array. A read-only model, for example a model over
/// a memory-mapped file, does not implement this interface.
///
/// ## Contract
///
/// - **No exceptions.** No method throws an exception.
/// - **Exact values.** For a node that a method builds, the read methods of the model give the
///   same value again. For example, [JsonModel#numberValue(Object)] of `number(x)` has the same
///   exact value as `x`, and [JsonModel#kind(Object)] of `bool(true)` is [JsonKind#TRUE].
/// - **New nodes.** A built node is not part of the query argument, so it has no location.
///
/// jsonlens calls [#object(List)] only with unique member names. A function extension must do
/// the same.
///
/// @param <N> the node type of the caller's JSON representation
public interface JsonFactory<N> {

    /// Builds a string.
    ///
    /// @param value the value
    /// @return a node of kind [JsonKind#STRING]
    N string(JsonString value);

    /// Builds a number.
    ///
    /// A representation can have a limit. For example, a `BigDecimal` cannot hold a number with an
    /// exponent outside the range of `int`. Then the result is [Maybe.None]: the factory never
    /// builds a number with a different value.
    ///
    /// @param value the value
    /// @return a node of kind [JsonKind#NUMBER] with the exact value, or [Maybe.None] if the
    ///     representation cannot hold the value
    Maybe<N> number(JsonNumber value);

    /// Builds `true` or `false`.
    ///
    /// @param value the value
    /// @return a node of kind [JsonKind#TRUE] or [JsonKind#FALSE]
    N bool(boolean value);

    /// Builds `null`.
    ///
    /// @return a node of kind [JsonKind#NULL]
    N nullValue();

    /// Builds an array.
    ///
    /// @param elements the elements, in order
    /// @return a node of kind [JsonKind#ARRAY]
    N array(List<N> elements);

    /// Builds an object.
    ///
    /// @param members the members, in order. The names are unique.
    /// @return a node of kind [JsonKind#OBJECT]
    N object(List<Property<N>> members);
    /// Builds a deep copy of a value of any model, in the representation of this factory.
    ///
    /// The source can be a value of another JSON library, for example a value of a memory-mapped
    /// file copied into a Jackson tree. It can also be a value of this model: then the copy shares
    /// no node with the source, so a later change of the copy does not change the source. Strings
    /// and numbers go to [#string(JsonString)] and [#number(JsonNumber)] in the representation
    /// of the source, so the factory decides if it must convert them.
    ///
    /// The method uses no recursion, so a deep value cannot overflow the stack. It never gives
    /// duplicate member names to [#object(List)]: the copy of an object with duplicate names (see
    /// [JsonModel#hasDuplicate(Object, JsonString)]) fails, because its value is not defined (RFC
    /// 8259, Section 4).
    ///
    /// @param model the model of the source value
    /// @param value the source value
    /// @param <S> the node type of the source model
    /// @return the copy, or [Maybe.None] if this representation cannot hold a number of the value,
    ///     or if an object of the value has duplicate member names, or if the source model is not
    ///     well-formed (it gives no element below the length of an array)
    default <S> Maybe<N> copyOf(JsonModel<S> model, S value) {
        return new Copier<>(model, this).copy(value);
    }
}
