/// The RFC 9535 JSONPath parser: query text to a validated
/// [ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery].
///
/// The parser checks that a query is _well-formed_ (it agrees with the ABNF grammar of RFC 9535)
/// and _valid_ (its integers are in the I-JSON range, and its function expressions are
/// well-typed). It reports the first problem as a [ca.marcusdunn.jsonlens.path.parser.ParseError].
///
/// Start with [ca.marcusdunn.jsonlens.path.parser.JsonPathParser]. The package
/// [ca.marcusdunn.jsonlens.path.parser] has a quick start.
///
/// ## Use without the evaluator
///
/// This module does not require the evaluator. A tool that only validates queries, for example a
/// schema checker or a linter, can use only this module.
///
/// ## Dependencies
///
/// At runtime, this module requires only `java.base` and the core module. The JSpecify nullness
/// annotations are necessary only at compile time.
module ca.marcusdunn.jsonlens.path.parser {
    requires static transitive org.jspecify;
    requires transitive ca.marcusdunn.jsonlens.path.core;

    exports ca.marcusdunn.jsonlens.path.parser;
}
