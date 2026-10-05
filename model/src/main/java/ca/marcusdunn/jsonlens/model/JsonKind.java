package ca.marcusdunn.jsonlens.model;

/// The kind of a JSON value, as RFC 8259 defines it.
///
/// The two boolean values are separate kinds, so that one call to
/// [JsonModel#kind(Object)] gives the full value of a boolean node.
public enum JsonKind {
    /// An unordered collection of members (name/value pairs).
    OBJECT,
    /// An ordered sequence of elements.
    ARRAY,
    /// A sequence of Unicode scalar values.
    STRING,
    /// A decimal number.
    NUMBER,
    /// The literal `true`.
    TRUE,
    /// The literal `false`.
    FALSE,
    /// The literal `null`.
    NULL;

    /// Returns true for an object or an array.
    ///
    /// @return true if values of this kind can have children
    public boolean isStructured() {
        return this == OBJECT || this == ARRAY;
    }

    /// Returns true for a string, number, true, false, or null.
    ///
    /// @return true if values of this kind have no children
    public boolean isPrimitive() {
        return !isStructured();
    }
}
