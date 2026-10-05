/// The RFC 9535 JSONPath parser: query text to a validated
/// [ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery].
///
/// ## Quick start
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.ParsingSnippets region=parse}
///
/// ## Errors
///
/// The parser does not throw exceptions. A query that is not well-formed or not valid gives a
/// [ParseError]. Match the errors that need special handling, and use [ParseError#message()] for
/// all others:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.ParsingSnippets region=errors}
///
/// ## Types
///
/// | Type | Purpose |
/// |---|---|
/// | [JsonPathParser] | parses query text or UTF-8 bytes |
/// | [ParseError] | the reason why a query is not well-formed or not valid |
/// | [FunctionRegistrationError] | the reason why a set of function extensions is not valid |
@NullMarked
package ca.marcusdunn.jsonlens.path.parser;

import org.jspecify.annotations.NullMarked;
