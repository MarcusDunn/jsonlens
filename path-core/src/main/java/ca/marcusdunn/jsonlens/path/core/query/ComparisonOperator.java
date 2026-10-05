package ca.marcusdunn.jsonlens.path.core.query;

/// A comparison operator (RFC 9535, Section 2.3.5.1).
public enum ComparisonOperator {
    /// `==`
    EQUAL("=="),
    /// `!=`
    NOT_EQUAL("!="),
    /// `<`
    LESS("<"),
    /// `<=`
    LESS_OR_EQUAL("<="),
    /// `>`
    GREATER(">"),
    /// `>=`
    GREATER_OR_EQUAL(">=");

    private final String symbol;

    ComparisonOperator(String symbol) {
        this.symbol = symbol;
    }

    /// Returns the operator text.
    ///
    /// @return the operator text, for example `"<="`
    public String symbol() {
        return symbol;
    }
}
