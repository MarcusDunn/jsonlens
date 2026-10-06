package ca.marcusdunn.jsonlens.mergepatch;

import java.util.List;

/// An error of a JSON Merge Patch.
///
/// RFC 7396 defines a result for each pair of a target and a patch, so a merge patch has no errors
/// of its own. These errors come from limits of the models, and from duplicate member names, which
/// have no defined value (RFC 8259, Section 4).
///
/// Each error has a path: the reference tokens of a location, as in a JSON Pointer (RFC 6901).
/// A token is a member name, or an array index in decimal. The root has no tokens.
///
/// | Error | Cause |
/// |---|---|
/// | [DuplicateName] | an object of the patch document has a member name more than once |
/// | [TargetDuplicateName] | an object of the target has a member name more than once, and the patch changes that member |
/// | [ValueNotRepresentable] | the target model cannot hold a number of a value of the patch |
public sealed interface MergePatchError {

    /// Returns a description of the error for people.
    ///
    /// @return the description
    default String message() {
        return switch (this) {
            case DuplicateName e -> "The object at '" + pointer(e.path()) + "' of the merge patch has more than one '"
                    + e.name() + "' member.";
            case TargetDuplicateName e -> "The object at '" + pointer(e.path()) + "' of the target has more than one '"
                    + e.name() + "' member, and the merge patch changes it.";
            case ValueNotRepresentable e -> "The value at '" + pointer(e.path())
                    + "' of the merge patch cannot be added to the target: a number is too large for the target model.";
        };
    }

    /// Writes reference tokens as a JSON Pointer (RFC 6901, Section 3).
    private static String pointer(List<String> path) {
        StringBuilder pointer = new StringBuilder();
        for (String token : path) {
            pointer.append('/').append(token.replace("~", "~0").replace("/", "~1"));
        }
        return pointer.toString();
    }

    /// An object of the patch document has a member name more than once.
    ///
    /// RFC 7396 does not tell which of the values applies, and RFC 8259 (Section 4) does not
    /// define the meaning of duplicate names. Thus [JsonMergePatch#parse] rejects the patch
    /// document. The check includes the objects in arrays, which a merge copies to the target.
    ///
    /// @param path the location of the object in the patch document
    /// @param name the duplicate name
    record DuplicateName(List<String> path, String name) implements MergePatchError {

        /// Makes the error.
        ///
        /// @param path the location of the object in the patch document
        /// @param name the duplicate name
        public DuplicateName {
            path = List.copyOf(path);
        }
    }

    /// An object of the target has a member name more than once, and the patch has a member with
    /// that name for the object. The target member to change is not defined.
    ///
    /// A model that implements `JsonEditor` has unique names, so only [JsonMergePatch#applyToCopy]
    /// can give this error.
    ///
    /// @param path the location of the object in the target. It is also the location of the
    ///     object of the patch that merges into it.
    /// @param name the duplicate name
    record TargetDuplicateName(List<String> path, String name) implements MergePatchError {

        /// Makes the error.
        ///
        /// @param path the location of the object in the target
        /// @param name the duplicate name
        public TargetDuplicateName {
            path = List.copyOf(path);
        }
    }

    /// The target model cannot hold a number of a value of the patch, for example `1e99999999999`
    /// in a Jackson tree.
    ///
    /// @param path the location of the value in the patch document. The number can be in the
    ///     value, for example in an array.
    record ValueNotRepresentable(List<String> path) implements MergePatchError {

        /// Makes the error.
        ///
        /// @param path the location of the value in the patch document
        public ValueNotRepresentable {
            path = List.copyOf(path);
        }
    }
}
