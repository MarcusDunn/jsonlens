package ca.marcusdunn.jsonlens.path.core.query;

import java.math.BigDecimal;

/// A literal value in a filter expression (RFC 9535, Section 2.3.5.1).
public sealed interface Literal extends ComparableExpression {

    /// A string literal.
    ///
    /// @param value the string, with all escapes replaced
    record StringLiteral(String value) implements Literal {}

    /// A number literal.
    ///
    /// @param value the exact value
    record NumberLiteral(BigDecimal value) implements Literal {}

    /// The literal `true` or `false`.
    ///
    /// @param value the value
    record BooleanLiteral(boolean value) implements Literal {}

    /// The literal `null`.
    record NullLiteral() implements Literal {}
}
