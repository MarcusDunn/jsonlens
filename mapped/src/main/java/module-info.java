/// A read-only [ca.marcusdunn.jsonlens.model.JsonModel] over UTF-8 JSON bytes, for example a
/// memory-mapped file: [ca.marcusdunn.jsonlens.mapped.MappedJson].
///
/// The model does not copy the JSON text. It reads strings and numbers from the bytes only when
/// the evaluator reads them. It does not implement
/// [ca.marcusdunn.jsonlens.model.JsonFactory], so it runs the standard functions and read-only
/// function extensions.
///
/// ## Dependencies
///
/// At runtime, this module requires only `java.base` and the model module. It does not require the
/// parser or the evaluator.
module ca.marcusdunn.jsonlens.mapped {
    requires static transitive org.jspecify;
    requires transitive ca.marcusdunn.jsonlens.model;

    exports ca.marcusdunn.jsonlens.mapped;
}
