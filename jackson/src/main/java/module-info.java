/// A [ca.marcusdunn.jsonlens.model.JsonModel] for Jackson 3 `JsonNode` values:
/// [ca.marcusdunn.jsonlens.jackson.JacksonJsonModel].
///
/// The class [ca.marcusdunn.jsonlens.jackson.JacksonJsonModel] has a quick start.
///
/// ## Dependencies
///
/// At runtime, this module requires the model module and Jackson databind (`tools.jackson.databind`).
/// It does not require the parser or the evaluator.
module ca.marcusdunn.jsonlens.jackson {
    requires static transitive org.jspecify;
    requires transitive ca.marcusdunn.jsonlens.model;
    requires transitive tools.jackson.databind;

    exports ca.marcusdunn.jsonlens.jackson;
}
