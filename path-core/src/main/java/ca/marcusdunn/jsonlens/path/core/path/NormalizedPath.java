package ca.marcusdunn.jsonlens.path.core.path;

import ca.marcusdunn.jsonlens.model.JsonString;
import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;
import org.jspecify.annotations.Nullable;

/// The location of a node in a JSON value, as RFC 9535, Section 2.7 defines it.
///
/// Each node in a JSON value has exactly one Normalized Path. The evaluator gives the path of each
/// result node, in the method `path()` of its `Node`.
///
/// ## Structure
///
/// A path is the [Root], or a parent path followed by a [Member] name or an [Element] index. The
/// records share their parents, so a long path costs one small object for each segment:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.CoreSnippets region=normalized-path}
///
/// ## Text
///
/// The method `toString()` gives the canonical text: bracket notation, names in single quotes,
/// and non-negative decimal indexes. Only these characters of a name have an escape:
///
/// | Character | Escape |
/// |---|---|
/// | backspace, form feed, line feed, carriage return, tab | `\b`, `\f`, `\n`, `\r`, `\t` |
/// | apostrophe `'` and backslash | `\'` and two backslashes |
/// | other characters U+0000 to U+001F | a backslash, `u00`, and two lowercase hexadecimal digits |
///
/// ## Equality
///
/// Two paths are equal if they identify the same location. Names are equal if they have the same
/// Unicode scalar values, also in different representations. The methods `equals`, `hashCode`, and
/// `toString` do not use recursion, so a very deep path does not cause a stack overflow.
public sealed interface NormalizedPath {

    /// Returns the path of the root node.
    ///
    /// @return the path `$`
    static Root root() {
        return Root.INSTANCE;
    }

    /// Returns the path of a member value of the node at this path.
    ///
    /// @param name the member name
    /// @return the path of the member value
    default Member member(String name) {
        return new Member(this, JsonString.of(name));
    }

    /// Returns the path of a member value of the node at this path, with a name in the
    /// representation of a model.
    ///
    /// @param name the member name
    /// @return the path of the member value
    default Member member(JsonString name) {
        return new Member(this, name);
    }

    /// Returns the path of an array element of the node at this path.
    ///
    /// @param index the zero-based index of the element. It must not be negative.
    /// @return the path of the element
    default Element element(int index) {
        return new Element(this, index);
    }

    /// The path of the root node: `$`.
    record Root() implements NormalizedPath {
        private static final Root INSTANCE = new Root();

        @Override
        public String toString() {
            return "$";
        }
    }

    /// The path of a member value: `parent['name']`.
    ///
    /// @param parent the path of the object
    /// The name is in the representation of the model that gave it, for example bytes of a mapped
    /// file. It is decoded only when a caller reads it, for example in `toString()`. Use
    /// [JsonString#copyOf(JsonString)] to get a `String`.
    ///
    /// @param name the member name
    record Member(NormalizedPath parent, JsonString name) implements NormalizedPath {
        @Override
        public boolean equals(@Nullable Object other) {
            return NormalizedPath.equal(this, other);
        }

        @Override
        public int hashCode() {
            return NormalizedPath.hash(this);
        }

        @Override
        public String toString() {
            return NormalizedPath.render(this);
        }
    }

    /// The path of an array element: `parent[index]`.
    ///
    /// @param parent the path of the array
    /// @param index the zero-based index of the element
    record Element(NormalizedPath parent, int index) implements NormalizedPath {
        @Override
        public boolean equals(@Nullable Object other) {
            return NormalizedPath.equal(this, other);
        }

        @Override
        public int hashCode() {
            return NormalizedPath.hash(this);
        }

        @Override
        public String toString() {
            return NormalizedPath.render(this);
        }
    }

    // The reference comparison is a fast path: paths often share their parent instances.
    @SuppressWarnings("ReferenceEquality")
    private static boolean equal(NormalizedPath path, @Nullable Object other) {
        NormalizedPath a = path;
        @Nullable Object b = other;
        while (a != b) {
            switch (a) {
                case Root root -> {
                    return b instanceof Root;
                }
                case Member member -> {
                    if (!(b instanceof Member m) || !JsonString.equal(member.name(), m.name())) {
                        return false;
                    }
                    a = member.parent();
                    b = m.parent();
                }
                case Element element -> {
                    if (!(b instanceof Element e) || element.index() != e.index()) {
                        return false;
                    }
                    a = element.parent();
                    b = e.parent();
                }
            }
        }
        return true;
    }

    private static int hash(NormalizedPath path) {
        int hash = 1;
        for (NormalizedPath p = path; ; ) {
            switch (p) {
                case Root root -> {
                    return hash;
                }
                case Member member -> {
                    hash = 31 * hash + JsonString.hash(member.name());
                    p = member.parent();
                }
                case Element element -> {
                    hash = 31 * hash + ~element.index();
                    p = element.parent();
                }
            }
        }
    }

    /** Writes the path. It collects the segments from the end to the root, then writes them in order. */
    private static String render(NormalizedPath path) {
        List<String> segments = new ArrayList<>();
        for (NormalizedPath p = path; ; ) {
            switch (p) {
                case Root root -> {
                    StringBuilder text = new StringBuilder("$");
                    for (int i = segments.size() - 1; i >= 0; i--) {
                        text.append(segments.get(i));
                    }
                    return text.toString();
                }
                case Member member -> {
                    StringBuilder segment = new StringBuilder();
                    appendName(segment, member.name());
                    segments.add(segment.toString());
                    p = member.parent();
                }
                case Element element -> {
                    segments.add("[" + element.index() + "]");
                    p = element.parent();
                }
            }
        }
    }

    /// Writes a name as RFC 9535, Section 2.7 specifies: normal-name-selector.
    private static void appendName(StringBuilder text, JsonString name) {
        final String hex = "0123456789abcdef";
        text.append("['");
        for (PrimitiveIterator.OfInt values = name.scalarValues(); values.hasNext(); ) {
            int c = values.nextInt();
            switch (c) {
                case '\b' -> text.append("\\b");
                case '\f' -> text.append("\\f");
                case '\n' -> text.append("\\n");
                case '\r' -> text.append("\\r");
                case '\t' -> text.append("\\t");
                case '\'' -> text.append("\\'");
                case '\\' -> text.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        text.append("\\u00").append(hex.charAt(c >> 4)).append(hex.charAt(c & 0xF));
                    } else {
                        text.appendCodePoint(c);
                    }
                }
            }
        }
        text.append("']");
    }
}
