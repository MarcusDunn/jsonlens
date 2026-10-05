/// The interfaces between jsonlens and the caller's JSON representation, and the result types.
///
/// jsonlens does not have its own JSON value types. The caller supplies a [JsonModel] for the
/// node type of their JSON library. The library then reads the caller's nodes directly. It does
/// not copy or convert them.
///
/// | Type | Purpose |
/// |---|---|
/// | [JsonModel] | read access to the JSON values of the caller |
/// | [JsonFactory] | builds new values in the representation of the caller |
/// | [JsonEditor] | changes the values of the caller in place |
/// | [JsonKind] | the seven kinds of JSON value |
/// | [Property] | a member of a JSON object: a name and a value |
/// | [JsonString], [JsonNumber], [JsonDecimal] | strings and numbers in the representation of the model, and exact decimals |
/// | [Result], [Maybe] | a value or an error, and a value that can be absent |
///
/// This model reads plain `Map` and `List` values:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.CustomModelSnippets region=model}
///
/// ## Errors are values
///
/// jsonlens does not use exceptions to report errors. An operation that can fail returns a
/// [Result], which is a success value or an error value:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.CoreSnippets region=result}
///
/// A value that can be absent is a [Maybe], not `null`:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.CoreSnippets region=maybe}
@NullMarked
package ca.marcusdunn.jsonlens.model;

import org.jspecify.annotations.NullMarked;
