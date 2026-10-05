package ca.marcusdunn.jsonlens.path.core.function;

/// The declared type of a function parameter or result (RFC 9535, Section 2.4.1).
public enum FunctionType {
    /// ValueType: a JSON value or Nothing.
    VALUE,
    /// LogicalType: LogicalTrue or LogicalFalse.
    LOGICAL,
    /// NodesType: a nodelist.
    NODES
}
