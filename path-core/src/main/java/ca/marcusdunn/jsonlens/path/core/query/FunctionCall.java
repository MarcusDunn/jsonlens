package ca.marcusdunn.jsonlens.path.core.query;

import ca.marcusdunn.jsonlens.path.core.function.FunctionType;
import java.util.List;

/// A function expression (RFC 9535, Section 2.4). The record type gives the declared result type
/// of the function, so that the expression can only occur where that type is well-typed.
public sealed interface FunctionCall {

    /// Returns the function name.
    ///
    /// @return the function name
    String name();

    /// Returns the arguments.
    ///
    /// @return the arguments, in order
    List<FunctionArgument> arguments();

    /// Returns the declared result type of the function.
    ///
    /// @return the declared result type
    default FunctionType resultType() {
        return switch (this) {
            case Value value -> FunctionType.VALUE;
            case Logical logical -> FunctionType.LOGICAL;
            case Nodes nodes -> FunctionType.NODES;
        };
    }

    /// A call of a function with the result type ValueType.
    ///
    /// @param name the function name
    /// @param arguments the arguments, in order
    record Value(String name, List<FunctionArgument> arguments) implements FunctionCall, ComparableExpression {
        /// Makes a call.
        ///
        /// @param name the function name
        /// @param arguments the arguments, in order
        public Value {
            arguments = List.copyOf(arguments);
        }
    }

    /// A call of a function with the result type LogicalType.
    ///
    /// @param name the function name
    /// @param arguments the arguments, in order
    record Logical(String name, List<FunctionArgument> arguments) implements FunctionCall, LogicalExpression {
        /// Makes a call.
        ///
        /// @param name the function name
        /// @param arguments the arguments, in order
        public Logical {
            arguments = List.copyOf(arguments);
        }
    }

    /// A call of a function with the result type NodesType.
    ///
    /// @param name the function name
    /// @param arguments the arguments, in order
    record Nodes(String name, List<FunctionArgument> arguments)
            implements FunctionCall, LogicalExpression, NodesExpression {
        /// Makes a call.
        ///
        /// @param name the function name
        /// @param arguments the arguments, in order
        public Nodes {
            arguments = List.copyOf(arguments);
        }
    }
}
