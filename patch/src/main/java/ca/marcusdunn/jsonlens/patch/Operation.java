package ca.marcusdunn.jsonlens.patch;

import ca.marcusdunn.jsonlens.pointer.JsonPointer;

/// An operation of a JSON Patch (RFC 6902, Section 4).
///
/// A value of an operation is a node of the patch document, in the model of the patch document
/// (`P`). The patch does not copy it when it reads the document. [JsonPatch#apply] copies it once
/// into the target document.
///
/// @param <P> the node type of the model of the patch document
public sealed interface Operation<P> {

    /// The target location of the operation: the `path` member.
    ///
    /// @return the pointer to the target location
    JsonPointer path();

    /// `add` (Section 4.1): adds a member, inserts an array element, or replaces the whole
    /// document.
    ///
    /// @param path the target location
    /// @param value the value to add
    /// @param <P> the node type of the model of the patch document
    record Add<P>(JsonPointer path, P value) implements Operation<P> {}

    /// `remove` (Section 4.2): removes the value at the target location.
    ///
    /// @param path the target location
    /// @param <P> the node type of the model of the patch document
    record Remove<P>(JsonPointer path) implements Operation<P> {}

    /// `replace` (Section 4.3): replaces the value at the target location.
    ///
    /// @param path the target location
    /// @param value the new value
    /// @param <P> the node type of the model of the patch document
    record Replace<P>(JsonPointer path, P value) implements Operation<P> {}

    /// `move` (Section 4.4): removes the value at `from` and adds it at the target location.
    ///
    /// @param from the location of the value to move
    /// @param path the target location
    /// @param <P> the node type of the model of the patch document
    record Move<P>(JsonPointer from, JsonPointer path) implements Operation<P> {}

    /// `copy` (Section 4.5): adds a copy of the value at `from` at the target location.
    ///
    /// @param from the location of the value to copy
    /// @param path the target location
    /// @param <P> the node type of the model of the patch document
    record Copy<P>(JsonPointer from, JsonPointer path) implements Operation<P> {}

    /// `test` (Section 4.6): tests that the value at the target location is equal to a value.
    ///
    /// @param path the target location
    /// @param value the expected value
    /// @param <P> the node type of the model of the patch document
    record Test<P>(JsonPointer path, P value) implements Operation<P> {}
}
