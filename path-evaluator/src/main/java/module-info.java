/// The RFC 9535 JSONPath evaluator: applies a [ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery] to a
/// JSON value through a [ca.marcusdunn.jsonlens.model.JsonModel].
///
/// The evaluator reads the caller's own JSON values. It does not copy them. The result is a
/// nodelist: each [ca.marcusdunn.jsonlens.path.evaluator.Node] has the caller's node instance and its
/// [ca.marcusdunn.jsonlens.path.core.path.NormalizedPath].
///
/// Start with [ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator]. The package
/// [ca.marcusdunn.jsonlens.path.evaluator] has a quick start.
///
/// ## Use without the parser
///
/// This module does not require the parser. An application can make queries directly, or with its
/// own parser. The evaluator checks the validity rules that the Java types cannot express, for
/// example the I-JSON range of an index.
///
/// ## Dependencies
///
/// At runtime, this module requires only `java.base` and the core module. The JSpecify nullness
/// annotations are necessary only at compile time.
module ca.marcusdunn.jsonlens.path.evaluator {
    requires static transitive org.jspecify;
    requires transitive ca.marcusdunn.jsonlens.path.core;

    exports ca.marcusdunn.jsonlens.path.evaluator;
}
