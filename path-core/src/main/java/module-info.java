/// The JSONPath types of jsonlens: the query syntax tree, function signatures, and Normalized
/// Paths.
///
/// Both the parser and the evaluator require this module. It requires the JSON model module
/// (`ca.marcusdunn.jsonlens.model`) transitively, so a module that reads this module also reads the
/// model and the result types.
///
/// ## Packages
///
/// | Package | Contents |
/// |---|---|
/// | [ca.marcusdunn.jsonlens.path.core.query] | [ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery] and the other types of the query syntax tree |
/// | [ca.marcusdunn.jsonlens.path.core.function] | [ca.marcusdunn.jsonlens.path.core.function.FunctionSignature]: the declared types of a function extension |
/// | [ca.marcusdunn.jsonlens.path.core.path] | [ca.marcusdunn.jsonlens.path.core.path.NormalizedPath]: the unique location of a node |
///
/// ## Dependencies
///
/// At runtime, this module requires only `java.base` and the model module. The JSpecify nullness
/// annotations (`requires static`) are necessary only at compile time. All exported packages are
/// `@NullMarked`: a type is not nullable unless it has `@Nullable`.
module ca.marcusdunn.jsonlens.path.core {
    requires static transitive org.jspecify;
    requires transitive ca.marcusdunn.jsonlens.model;

    exports ca.marcusdunn.jsonlens.path.core.function;
    exports ca.marcusdunn.jsonlens.path.core.path;
    exports ca.marcusdunn.jsonlens.path.core.query;
}
