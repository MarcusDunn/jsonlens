package ca.marcusdunn.jsonlens.patch;

import ca.marcusdunn.jsonlens.pointer.PointerError;

/// An error of a JSON Patch: a patch document that violates RFC 6902, or an operation that is not
/// successful (RFC 6902, Section 5).
///
/// Each error, except [NotAnArray], has the zero-based index of its operation in the patch.
///
/// | Error | Cause |
/// |---|---|
/// | [NotAnArray] | the patch document is not an array (Section 3) |
/// | [NotAnObject] | an operation is not an object (Section 3) |
/// | [MissingMember] | an operation has no `op`, `path`, `from`, or `value` that it needs |
/// | [DuplicateMember] | an operation has a member more than once, for example two `op` members (Section 4) |
/// | [NotAString] | `op`, `path`, or `from` is not a string |
/// | [UnknownOperation] | `op` is not `add`, `remove`, `replace`, `move`, `copy`, or `test` |
/// | [InvalidPointer] | `path` or `from` is not a valid JSON Pointer |
/// | [PathNotFound] | the target location, or its parent for `add`, does not exist |
/// | [FromNotFound] | the `from` location does not exist |
/// | [RootRemoved] | `remove` has the whole document as its target |
/// | [MoveIntoChild] | `from` is a proper prefix of `path` (Section 4.4) |
/// | [TestFailed] | the values of a `test` are not equal (Section 4.6) |
/// | [ValueNotRepresentable] | the target model cannot hold a number of the value, or the value has an object with duplicate member names |
/// | [CopyLimitExceeded] | the `copy` operations add more nodes than [JsonPatch.Limits#maxCopiedNodes()] |
public sealed interface PatchError {

    /// Returns a description of the error for people.
    ///
    /// @return the description
    default String message() {
        return switch (this) {
            case NotAnArray e -> "A JSON Patch document must be an array of operations.";
            case NotAnObject e -> "Operation " + e.operation() + " is not an object.";
            case MissingMember e -> "Operation " + e.operation() + " has no '" + e.member() + "' member.";
            case DuplicateMember e -> "Operation " + e.operation() + " has more than one '" + e.member() + "' member.";
            case NotAString e -> "The '" + e.member() + "' member of operation " + e.operation() + " is not a string.";
            case UnknownOperation e -> "Operation " + e.operation() + " has the unknown op '" + e.op() + "'.";
            case InvalidPointer e -> "The '" + e.member() + "' member of operation " + e.operation()
                    + " is not a JSON Pointer: " + e.error().message();
            case PathNotFound e -> "The path of operation " + e.operation() + " does not exist: " + e.error().message();
            case FromNotFound e -> "The from location of operation " + e.operation() + " does not exist: "
                    + e.error().message();
            case RootRemoved e -> "Operation " + e.operation() + " removes the whole document.";
            case MoveIntoChild e -> "Operation " + e.operation() + " moves a value into one of its children.";
            case TestFailed e -> "The test of operation " + e.operation() + " failed: the values are not equal.";
            case ValueNotRepresentable e -> "The value of operation " + e.operation()
                    + " cannot be added to the target document: a number is too large for it, or an object has duplicate"
                    + " member names.";
            case CopyLimitExceeded e -> "Operation " + e.operation() + " copies too many nodes: the copy operations of the"
                    + " patch add more than " + e.limit() + " nodes.";
        };
    }

    /// The patch document is not an array (RFC 6902, Section 3).
    record NotAnArray() implements PatchError {}

    /// An operation is not an object (RFC 6902, Section 3).
    ///
    /// @param operation the index of the operation
    record NotAnObject(int operation) implements PatchError {}

    /// An operation has no member that it needs: `op` and `path` (Section 4), `value` for `add`,
    /// `replace`, and `test`, or `from` for `move` and `copy`.
    ///
    /// @param operation the index of the operation
    /// @param member the name of the missing member
    record MissingMember(int operation, String member) implements PatchError {}

    /// An operation has a member more than once. An operation must have exactly one `op` and one
    /// `path` (Section 4). The value of other duplicate members is not defined, so they are errors
    /// too.
    ///
    /// @param operation the index of the operation
    /// @param member the duplicate name
    record DuplicateMember(int operation, String member) implements PatchError {}

    /// The `op`, `path`, or `from` member of an operation is not a string.
    ///
    /// @param operation the index of the operation
    /// @param member the name of the member
    record NotAString(int operation, String member) implements PatchError {}

    /// The `op` of an operation is not one of the six operations (Section 4).
    ///
    /// @param operation the index of the operation
    /// @param op the value of `op`
    record UnknownOperation(int operation, String op) implements PatchError {}

    /// The `path` or `from` member of an operation is not a valid JSON Pointer.
    ///
    /// @param operation the index of the operation
    /// @param member `path` or `from`
    /// @param error the pointer error
    record InvalidPointer(int operation, String member, PointerError error) implements PatchError {}

    /// The target location does not exist. For `add`, its parent does not exist, or the last token
    /// is not a valid member name or array position (Section 4.1).
    ///
    /// @param operation the index of the operation
    /// @param error the reason, as a pointer error
    record PathNotFound(int operation, PointerError error) implements PatchError {}

    /// The `from` location of a `move` or a `copy` does not exist (Sections 4.4 and 4.5).
    ///
    /// @param operation the index of the operation
    /// @param error the reason, as a pointer error
    record FromNotFound(int operation, PointerError error) implements PatchError {}

    /// A `remove` has the whole document as its target. The document cannot be absent.
    ///
    /// @param operation the index of the operation
    record RootRemoved(int operation) implements PatchError {}

    /// The `from` location of a `move` is a proper prefix of its `path` (Section 4.4).
    ///
    /// @param operation the index of the operation
    record MoveIntoChild(int operation) implements PatchError {}

    /// The value at the target location of a `test` is not equal to its value (Section 4.6).
    ///
    /// @param operation the index of the operation
    record TestFailed(int operation) implements PatchError {}

    /// The value of an operation cannot be added to the target document: the target model cannot
    /// hold one of its numbers, for example `1e99999999999` in a Jackson tree, or an object of the
    /// value has duplicate member names, which have no defined value (RFC 8259, Section 4).
    ///
    /// @param operation the index of the operation
    record ValueNotRepresentable(int operation) implements PatchError {}

    /// A `copy` operation passes [JsonPatch.Limits#maxCopiedNodes()]: together with the earlier
    /// `copy` operations, it adds more nodes than the limit. The document has its original value.
    ///
    /// @param operation the index of the operation
    /// @param limit the limit
    record CopyLimitExceeded(int operation, int limit) implements PatchError {}
}
