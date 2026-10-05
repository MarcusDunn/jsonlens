/// The abstract syntax tree of a JSONPath query (RFC 9535).
///
/// The parser makes these trees from query text. A caller can also make them directly, and
/// [JsonPathQuery#toString()] writes a tree as query text.
///
/// ## Grammar and types
///
/// The types follow the ABNF grammar of RFC 9535. Where the grammar or the type system of Section
/// 2.4 restricts an expression, the Java types restrict it too. Thus many queries that are not
/// valid cannot be made at all.
///
/// | Grammar | Type |
/// |---|---|
/// | `jsonpath-query` | [JsonPathQuery]: `$` and zero or more [Segment]s |
/// | `child-segment`, `descendant-segment` | [Segment.Child], [Segment.Descendant] |
/// | `name-selector`, `wildcard-selector`, `index-selector`, `slice-selector`, `filter-selector` | [Selector.Name], [Selector.Wildcard], [Selector.Index], [Selector.Slice], [Selector.Filter] |
/// | `logical-expr` | [LogicalExpression] |
/// | `comparable` | [ComparableExpression]: a [Literal], a [SingularQuery], or a [FunctionCall.Value] |
/// | `filter-query` | [FilterQuery] |
/// | `function-expr` | [FunctionCall]: [FunctionCall.Value], [FunctionCall.Logical], or [FunctionCall.Nodes] |
/// | `function-argument` | [FunctionArgument]: one record for each declared parameter type |
///
/// For example, a side of a [LogicalExpression.Comparison] is a [ComparableExpression]. A query
/// that can select more than one node is not a [ComparableExpression], so it cannot be a side of a
/// comparison.
///
/// ## Make a query directly
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.CoreSnippets region=build-query}
///
/// The Java types cannot express all validity rules. For example, an index must be in the I-JSON
/// range, and a function call must agree with the signature of the function. The evaluator checks
/// these rules before it applies a query.
///
/// ## Safe query construction
///
/// [JsonPathQuery#toString()] escapes all names and strings. Thus a name from user input cannot
/// change the structure of a query (RFC 9535, Section 4.2):
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.CoreSnippets region=escape-names}
@NullMarked
package ca.marcusdunn.jsonlens.path.core.query;

import org.jspecify.annotations.NullMarked;
