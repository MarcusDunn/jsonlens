/// RFC 6901 JSON Pointer: a string that identifies a value in a JSON document.
///
/// [ca.marcusdunn.jsonlens.pointer.JsonPointer] parses and writes pointers, in the JSON string form
/// and in the URI fragment form, and resolves them against the JSON values of any
/// [ca.marcusdunn.jsonlens.model.JsonModel]. It reads the caller's nodes directly and does not copy
/// them.
///
/// ## Dependencies
///
/// At runtime, this module requires only `java.base` and the model module. The JSpecify nullness
/// annotations (`requires static`) are necessary only at compile time. All exported packages are
/// `@NullMarked`: a type is not nullable unless it has `@Nullable`.
module ca.marcusdunn.jsonlens.pointer {
    requires static transitive org.jspecify;
    requires transitive ca.marcusdunn.jsonlens.model;

    exports ca.marcusdunn.jsonlens.pointer;
}
