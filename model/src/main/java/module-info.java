/// The JSON model of jsonlens: the interfaces between the library and the JSON values of the
/// caller, and the result types.
///
/// jsonlens has no JSON value types. A caller supplies a
/// [ca.marcusdunn.jsonlens.model.JsonModel] for the node type of their JSON library, and the
/// library reads the caller's nodes directly. A JSON library adapter, the JSONPath modules, and the
/// JSON Pointer and JSON Patch modules all require this module.
///
/// ## Packages
///
/// | Package | Contents |
/// |---|---|
/// | [ca.marcusdunn.jsonlens.model] | [ca.marcusdunn.jsonlens.model.JsonModel] and the other model interfaces, the JSON string and number types, and [ca.marcusdunn.jsonlens.model.Result] and [ca.marcusdunn.jsonlens.model.Maybe] |
///
/// ## Errors are values
///
/// jsonlens does not use exceptions to report errors. An operation that can fail returns a
/// [ca.marcusdunn.jsonlens.model.Result], which you examine with a `switch`. A value that can be
/// absent is a [ca.marcusdunn.jsonlens.model.Maybe], not `null`.
///
/// ## Dependencies
///
/// At runtime, this module requires only `java.base`. The JSpecify nullness annotations
/// (`requires static`) are necessary only at compile time. All exported packages are
/// `@NullMarked`: a type is not nullable unless it has `@Nullable`.
module ca.marcusdunn.jsonlens.model {
    requires static transitive org.jspecify;

    exports ca.marcusdunn.jsonlens.model;
}
