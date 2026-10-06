package ca.marcusdunn.jsonlens.mergepatch;

import ca.marcusdunn.jsonlens.model.JsonString;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// A location in a JSON document: the reference tokens from the root, as in a JSON Pointer.
///
/// Each location refers to its parent. Thus a child location costs one object, also in a deep
/// document. The tokens become `String` values only for an error.
///
/// @param parent the location of the parent container, or `null` for the root
/// @param token the member name or the array index in the parent. The root has an empty token.
record Location(@Nullable Location parent, JsonString token) {

    /// The root of a document.
    static final Location ROOT = new Location(null, JsonString.of(""));

    /// Returns the location of a child.
    ///
    /// @param name the member name, or the array index in decimal
    /// @return the location of the child
    Location child(JsonString name) {
        return new Location(this, name);
    }

    /// Returns the reference tokens, from the root.
    ///
    /// @return the tokens. The root has no tokens.
    List<String> tokens() {
        List<String> tokens = new ArrayList<>();
        Location at = this;
        Location parent = at.parent();
        while (parent != null) {
            tokens.add(JsonString.copyOf(at.token()));
            at = parent;
            parent = at.parent();
        }
        return List.copyOf(tokens.reversed());
    }
}
