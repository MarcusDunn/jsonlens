package ca.marcusdunn.jsonlens.path.evaluator;

import ca.marcusdunn.jsonlens.path.core.path.NormalizedPath;

/// A node: a JSON value and its location in the query argument (RFC 9535, Section 1.1).
///
/// The value is the caller's own node instance, not a copy. The path is the one Normalized Path of
/// the location. The same node can occur more than one time in a nodelist, for example for the
/// query `$[0, 0]`.
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.EvaluationSnippets region=evaluate}
///
/// @param value the JSON value, as the caller's own node instance
/// @param path the Normalized Path of the location
/// @param <N> the node type of the JSON model
public record Node<N>(N value, NormalizedPath path) {}
