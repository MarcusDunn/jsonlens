package ca.marcusdunn.jsonlens.path.core.query;

/// A segment of a singular query (RFC 9535, Section 2.3.5.1).
public sealed interface SingularSegment {

    /// A name segment: `['name']` or `.name`.
    ///
    /// @param name the member name
    record Name(String name) implements SingularSegment {}

    /// An index segment: `[index]`.
    ///
    /// @param index the index
    record Index(long index) implements SingularSegment {}
}
