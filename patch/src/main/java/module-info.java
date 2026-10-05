/// RFC 6902 JSON Patch: a sequence of operations that changes a JSON document.
///
/// [ca.marcusdunn.jsonlens.patch.JsonPatch] reads a patch document with any
/// [ca.marcusdunn.jsonlens.model.JsonModel], and applies it in place to a document of a model that
/// also implements [ca.marcusdunn.jsonlens.model.JsonFactory] and
/// [ca.marcusdunn.jsonlens.model.JsonEditor]. A failed patch leaves the document with its original
/// value.
///
/// ## Dependencies
///
/// At runtime, this module requires only `java.base`, the model module, and the pointer module.
/// The JSpecify nullness annotations (`requires static`) are necessary only at compile time. All
/// exported packages are `@NullMarked`: a type is not nullable unless it has `@Nullable`.
module ca.marcusdunn.jsonlens.patch {
    requires static transitive org.jspecify;
    requires transitive ca.marcusdunn.jsonlens.model;
    requires transitive ca.marcusdunn.jsonlens.pointer;

    exports ca.marcusdunn.jsonlens.patch;
}
