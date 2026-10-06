/// RFC 7396 JSON Merge Patch: a JSON document that describes changes to a target document.
///
/// [ca.marcusdunn.jsonlens.mergepatch.JsonMergePatch] reads a merge patch with any
/// [ca.marcusdunn.jsonlens.model.JsonModel]. It merges the patch in place into a document of a
/// model that also implements [ca.marcusdunn.jsonlens.model.JsonFactory] and
/// [ca.marcusdunn.jsonlens.model.JsonEditor], or into a copy with only a `JsonFactory`.
///
/// ## Dependencies
///
/// At runtime, this module requires only `java.base` and the model module. The JSpecify nullness
/// annotations (`requires static`) are necessary only at compile time. All exported packages are
/// `@NullMarked`: a type is not nullable unless it has `@Nullable`.
module ca.marcusdunn.jsonlens.mergepatch {
    requires static transitive org.jspecify;
    requires transitive ca.marcusdunn.jsonlens.model;

    exports ca.marcusdunn.jsonlens.mergepatch;
}
