package ca.marcusdunn.jsonlens.path.core.query;

/// A side of a comparison, or a ValueType function argument: a literal, a singular query, or a
/// function with the result type ValueType (RFC 9535, Sections 2.3.5.1 and 2.4.3).
public sealed interface ComparableExpression permits Literal, SingularQuery, FunctionCall.Value {}
