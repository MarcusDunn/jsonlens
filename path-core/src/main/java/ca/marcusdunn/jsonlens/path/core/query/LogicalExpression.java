package ca.marcusdunn.jsonlens.path.core.query;

import java.util.List;

/// An expression with a logical result: LogicalTrue or LogicalFalse (RFC 9535, Section 2.3.5).
///
/// | Expression | Query text | True if |
/// |---|---|---|
/// | [Or] | `@.a \|\| @.b` | an operand is true |
/// | [And] | `@.a && @.b` | all operands are true |
/// | [Not] | `!@.a`, `!(@.a == 1)` | the operand is false |
/// | [Comparison] | `@.price < 10` | the comparison is true (Section 2.3.5.2.2) |
/// | [FilterQuery] | `@.isbn`, `$.config.debug` | the query selects at least one node (existence test) |
/// | [FunctionCall.Logical] | `match(@.date, '1974-.*')` | the function gives LogicalTrue |
/// | [FunctionCall.Nodes] | `myNodes(@.*)` | the nodelist of the function is not empty (Section 2.4.2) |
///
/// The operator precedence is: parentheses and functions, then `!`, then comparisons, then `&&`,
/// then `||`. Thus the parser makes `@.a || @.b && @.c` into `Or[a, And[b, c]]`. A tree does not
/// keep parentheses: the structure of the tree gives the grouping.
///
/// An existence test is true also for a node with the value `null` or `false`. To test the value,
/// use a comparison, for example `@.debug == true`.
public sealed interface LogicalExpression
        permits LogicalExpression.Or,
                LogicalExpression.And,
                LogicalExpression.Not,
                LogicalExpression.Comparison,
                FilterQuery,
                FunctionCall.Logical,
                FunctionCall.Nodes {

    /// A disjunction: `a || b || ...`.
    ///
    /// @param operands two or more operands
    record Or(List<LogicalExpression> operands) implements LogicalExpression {
        /// Makes a disjunction.
        ///
        /// @param operands two or more operands
        public Or {
            operands = List.copyOf(operands);
        }
    }

    /// A conjunction: `a && b && ...`.
    ///
    /// @param operands two or more operands
    record And(List<LogicalExpression> operands) implements LogicalExpression {
        /// Makes a conjunction.
        ///
        /// @param operands two or more operands
        public And {
            operands = List.copyOf(operands);
        }
    }

    /// A negation: `!a`.
    ///
    /// @param operand the operand
    record Not(LogicalExpression operand) implements LogicalExpression {}

    /// A comparison: `left op right` (Section 2.3.5.2.2).
    ///
    /// @param left the left side
    /// @param operator the comparison operator
    /// @param right the right side
    record Comparison(ComparableExpression left, ComparisonOperator operator, ComparableExpression right)
            implements LogicalExpression {}
}
