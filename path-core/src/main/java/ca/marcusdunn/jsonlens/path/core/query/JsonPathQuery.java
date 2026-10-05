package ca.marcusdunn.jsonlens.path.core.query;

import java.util.List;

/// A JSONPath query: the root identifier `$` followed by zero or more segments
/// (RFC 9535, Section 2.1.1).
///
/// Each segment operates on the nodelist of the previous segment. The first segment operates on
/// the root node. The parser makes a query from text, and keeps shorthand forms as their bracket
/// equivalents:
///
/// | Query text | Segments |
/// |---|---|
/// | `$` | none |
/// | `$.store` | `[Child[Name[store]]]` |
/// | `$.store.*` | `[Child[Name[store]], Child[Wildcard[]]]` |
/// | `$..book[0, 'x']` | `[Descendant[Name[book]], Child[Index[0], Name[x]]]` |
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.ParsingSnippets region=tree}
///
/// @param segments the segments, in order
public record JsonPathQuery(List<Segment> segments) {

    /// Makes a query.
    ///
    /// @param segments the segments, in order
    public JsonPathQuery {
        segments = List.copyOf(segments);
    }

    /// Writes the query as query text in bracket notation.
    ///
    /// Names and strings are in single quotes, with all necessary escapes. Parentheses occur only
    /// where the precedence of the operators makes them necessary. The parser makes an equal query
    /// from this text:
    ///
    /// {@snippet class=ca.marcusdunn.jsonlens.docs.CoreSnippets region=build-query}
    ///
    /// @return the query text
    @Override
    public String toString() {
        return QueryWriter.write(this);
    }
}
