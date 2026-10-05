package ca.marcusdunn.jsonlens.path.core.query;

/// The start of a query in a filter expression (RFC 9535, Sections 2.2 and 2.3.5).
public enum Identifier {
    /// The root node identifier `$`.
    ROOT,
    /// The current node identifier `@`.
    CURRENT
}
