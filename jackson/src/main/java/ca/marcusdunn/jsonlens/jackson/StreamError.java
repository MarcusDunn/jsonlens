package ca.marcusdunn.jsonlens.jackson;

/// An error of a [JacksonStream]: the parser could not give a JSON text.
///
/// | Error | Cause |
/// |---|---|
/// | [Empty] | the input has no value |
/// | [ReadFailed] | the parser failed, for example on text that is not JSON or at the end of the input inside a value |
/// | [NotJson] | the parser gave a token that is not a JSON value, for example an embedded binary object |
/// | [TrailingContent] | [JacksonStream#readFully] found more tokens after the root value |
public sealed interface StreamError {

    /// Returns a description of the error for people.
    ///
    /// @return the description
    default String message() {
        return switch (this) {
            case Empty e -> "The input has no JSON value.";
            case ReadFailed e -> "The parser failed: " + e.reason();
            case NotJson e -> "The parser gave the token " + e.token() + ", which is not a JSON value.";
            case TrailingContent e -> "The input has more content after the root value.";
        };
    }

    /// The input has no value.
    record Empty() implements StreamError {}

    /// The parser failed.
    ///
    /// @param reason the message of the parser
    record ReadFailed(String reason) implements StreamError {}

    /// The parser gave a token that is not a JSON value.
    ///
    /// @param token the name of the token
    record NotJson(String token) implements StreamError {}

    /// The input has more tokens after the root value.
    record TrailingContent() implements StreamError {}
}
