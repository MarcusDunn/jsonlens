package ca.marcusdunn.jsonlens.path.core.query;

import java.util.List;

/// A query in a filter expression: `@` or `$` followed by zero or more segments
/// (RFC 9535, Section 2.3.5.1).
///
/// As a logical expression, it is an existence test (Section 2.3.5.2.1). As a NodesType
/// argument, it gives its nodelist.
///
/// @param identifier the start of the query
/// @param segments the segments, in order
public record FilterQuery(Identifier identifier, List<Segment> segments)
        implements LogicalExpression, NodesExpression {

    /// Makes a filter query.
    ///
    /// @param identifier the start of the query
    /// @param segments the segments, in order
    public FilterQuery {
        segments = List.copyOf(segments);
    }
}
