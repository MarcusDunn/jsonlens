package ca.marcusdunn.jsonlens.path.core.query;

/// A NodesType function argument: a query or a function with the result type NodesType
/// (RFC 9535, Section 2.4.3).
public sealed interface NodesExpression permits FilterQuery, FunctionCall.Nodes {}
