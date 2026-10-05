/// The RFC 9535 JSONPath evaluator: applies a query to a JSON value through a
/// [ca.marcusdunn.jsonlens.model.JsonModel].
///
/// ## Quick start
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.QuickStartSnippets region=quick-start}
///
/// ## Filters and functions
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.EvaluationSnippets region=filters}
///
/// ## Types
///
/// | Type | Purpose |
/// |---|---|
/// | [JsonPathEvaluator] | applies a query to a JSON value |
/// | [Node] | a result node: the value and its Normalized Path |
/// | [EvaluationError] | the reason why the evaluator cannot give a correct result |
/// | [JsonPathEvaluator.Limits] | the resource limits of an evaluation |
/// | [FunctionExtension] | a read-only function extension: it runs with each model |
/// | [BuildingFunctionExtension] | a function extension that can make new values |
/// | [BuildingEvaluator] | an evaluator with building extensions: it needs a model that can build nodes |
/// | [Instance] | the arguments and results of functions: nodes, logical values, and nodelists |
/// | [ExtensionError] | the reason why a set of function extensions is not valid |
@NullMarked
package ca.marcusdunn.jsonlens.path.evaluator;

import org.jspecify.annotations.NullMarked;
