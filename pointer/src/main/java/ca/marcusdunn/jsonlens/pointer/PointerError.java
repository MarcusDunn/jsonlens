package ca.marcusdunn.jsonlens.pointer;

/// An error of a JSON Pointer (RFC 6901, Section 7): invalid syntax, or a pointer that does not
/// reference a value.
///
/// | Error | Cause |
/// |---|---|
/// | [MissingSlash] | a pointer that is not empty does not start with `/` |
/// | [InvalidEscape] | a `~` is not followed by `0` or `1` |
/// | [InvalidFragment] | a URI fragment identifier is not valid, or its bytes are not UTF-8 |
/// | [MemberNotFound] | an object has no member with the name |
/// | [DuplicateName] | an object has more than one member with the name |
/// | [InvalidIndex] | a token for an array is not an array index, for example `01`, `x`, or `-` |
/// | [IndexOutOfRange] | an array index is not less than the length of the array |
/// | [NotAContainer] | a token follows a value that is not an object or an array |
public sealed interface PointerError {

    /// Returns a description of the error for people.
    ///
    /// @return the description
    default String message() {
        return switch (this) {
            case MissingSlash e -> "A JSON Pointer that is not empty must start with '/'.";
            case InvalidEscape e -> "At position " + e.position() + ", '~' is not followed by '0' or '1'.";
            case InvalidFragment e ->
                    "At position " + e.position() + ", the URI fragment identifier is not a valid JSON Pointer.";
            case MemberNotFound e -> "The object at '" + e.parent() + "' has no member '" + e.token() + "'.";
            case DuplicateName e -> "The object at '" + e.parent() + "' has more than one member '" + e.token() + "'.";
            case InvalidIndex e -> "'" + e.token() + "' is not an index of the array at '" + e.parent() + "'.";
            case IndexOutOfRange e -> "The array at '" + e.parent() + "' has no element " + e.token() + ".";
            case NotAContainer e -> "The value at '" + e.parent() + "' is not an object or an array.";
        };
    }

    /// A pointer that is not empty does not start with `/` (RFC 6901, Section 3).
    record MissingSlash() implements PointerError {}

    /// A `~` is not followed by `0` or `1` (RFC 6901, Section 3).
    ///
    /// @param position the position of the `~`, in Unicode code points from the start
    record InvalidEscape(int position) implements PointerError {}

    /// A URI fragment identifier is not a valid JSON Pointer (RFC 6901, Section 6): it does not
    /// start with `#`, it has a character that a fragment does not permit, a percent-encoding is
    /// not complete, or the bytes are not UTF-8.
    ///
    /// @param position the position of the problem, in characters from the start
    record InvalidFragment(int position) implements PointerError {}

    /// An object has no member with the name of a token.
    ///
    /// @param parent the pointer to the object
    /// @param token the member name
    record MemberNotFound(JsonPointer parent, String token) implements PointerError {}

    /// An object has more than one member with the name of a token. The referenced member is not
    /// defined, so the evaluation fails (RFC 6901, Section 4).
    ///
    /// @param parent the pointer to the object
    /// @param token the member name
    record DuplicateName(JsonPointer parent, String token) implements PointerError {}

    /// A token for an array is not an array index: digits without a leading zero (RFC 6901,
    /// Section 4). The token `-` is also in this group, because it references the nonexistent
    /// element after the last element.
    ///
    /// @param parent the pointer to the array
    /// @param token the token
    record InvalidIndex(JsonPointer parent, String token) implements PointerError {}

    /// An array index is not less than the length of the array.
    ///
    /// @param parent the pointer to the array
    /// @param token the index
    record IndexOutOfRange(JsonPointer parent, String token) implements PointerError {}

    /// A token follows a value that is not an object or an array.
    ///
    /// @param parent the pointer to the value
    record NotAContainer(JsonPointer parent) implements PointerError {}
}
