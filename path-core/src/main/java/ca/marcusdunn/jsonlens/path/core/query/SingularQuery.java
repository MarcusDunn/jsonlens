package ca.marcusdunn.jsonlens.path.core.query;

import java.util.List;

/// A singular query: a query that selects at most one node (RFC 9535, Section 2.3.5.1). It has
/// only name segments and index segments.
///
/// @param identifier the start of the query
/// @param segments the segments, in order
public record SingularQuery(Identifier identifier, List<SingularSegment> segments)
        implements ComparableExpression {

    /// Makes a singular query.
    ///
    /// @param identifier the start of the query
    /// @param segments the segments, in order
    public SingularQuery {
        segments = List.copyOf(segments);
    }
}
