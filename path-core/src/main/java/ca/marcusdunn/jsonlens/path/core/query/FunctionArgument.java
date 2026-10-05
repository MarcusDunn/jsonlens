package ca.marcusdunn.jsonlens.path.core.query;

import ca.marcusdunn.jsonlens.path.core.function.FunctionType;

/// A function argument, with the declared type of its parameter (RFC 9535, Section 2.4.3).
public sealed interface FunctionArgument {

    /// Returns the declared type of the parameter.
    ///
    /// @return the declared type of the parameter
    default FunctionType type() {
        return switch (this) {
            case Value value -> FunctionType.VALUE;
            case Logical logical -> FunctionType.LOGICAL;
            case Nodes nodes -> FunctionType.NODES;
        };
    }

    /// An argument for a ValueType parameter.
    ///
    /// @param expression a literal, a singular query, or a ValueType function
    record Value(ComparableExpression expression) implements FunctionArgument {}

    /// An argument for a LogicalType parameter.
    ///
    /// @param expression a logical expression
    record Logical(LogicalExpression expression) implements FunctionArgument {}

    /// An argument for a NodesType parameter.
    ///
    /// @param expression a query or a NodesType function
    record Nodes(NodesExpression expression) implements FunctionArgument {}
}
