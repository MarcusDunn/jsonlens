package ca.marcusdunn.jsonlens.path.evaluator;

import ca.marcusdunn.jsonlens.path.core.function.FunctionType;

/// The reason why the evaluator cannot give a correct result.
///
/// A query that selects nothing is not an error: the result is an empty nodelist. An error occurs
/// only when the evaluator cannot give the correct nodelist.
///
/// | Error | Cause | Query from the parser |
/// |---|---|---|
/// | [NodelistTooLarge] | a nodelist has more nodes than [JsonPathEvaluator.Limits#maxNodes()] | can occur |
/// | [RegexTooComplex] | a regular expression of `match()` or `search()` is too large | can occur |
/// | [UnknownFunction] | the evaluator has no implementation for a function | no |
/// | [SignatureMismatch] | a call does not agree with the signature of its function | no |
/// | [IntegerOutOfRange] | an index or a slice parameter is outside the I-JSON range | no |
/// | [QueryTooDeep] | the query has more than [JsonPathEvaluator#MAX_QUERY_DEPTH] nested expressions | no |
/// | [ExtensionResultMismatch] | a function extension gives an instance of the wrong type | can occur |
///
/// The first two errors are the overflow indications of RFC 9535, Section 2.1.
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.EvaluationSnippets region=limits}
public sealed interface EvaluationError {

    /// Returns a description of the error.
    ///
    /// @return a description of the error
    default String message() {
        return switch (this) {
            case UnknownFunction(String name) -> "The evaluator has no function with the name '" + name + "'.";
            case SignatureMismatch(String name) ->
                    "The call of the function '" + name + "' does not agree with the signature of the function.";
            case IntegerOutOfRange e ->
                    "The integer " + e.value() + " is outside the range [-(2^53)+1, (2^53)-1].";
            case QueryTooDeep e -> "The query has more than " + e.limit() + " nested expressions.";
            case NodelistTooLarge e -> "Overflow: a nodelist has more than " + e.limit() + " nodes.";
            case RegexTooComplex e ->
                    "Overflow: the regular expression '" + e.pattern() + "' needs more than " + e.limit()
                            + " instructions or nested groups.";
            case ExtensionResultMismatch(String name, FunctionType expected) ->
                    "The function extension '" + name + "' did not return an instance of its result type "
                            + expected + ".";
        };
    }

    /// The query calls a function that the evaluator does not have.
    ///
    /// @param name the function name
    record UnknownFunction(String name) implements EvaluationError {}

    /// A function call has a result type, a number of arguments, or argument types that do not
    /// agree with the signature of the function.
    ///
    /// @param name the function name
    record SignatureMismatch(String name) implements EvaluationError {}

    /// An index or a slice parameter is outside the I-JSON range.
    ///
    /// @param value the integer
    record IntegerOutOfRange(long value) implements EvaluationError {}

    /// The query has more nested expressions than [JsonPathEvaluator#MAX_QUERY_DEPTH].
    ///
    /// @param limit the limit
    record QueryTooDeep(int limit) implements EvaluationError {}

    /// Overflow indication: a nodelist has more nodes than the limit (RFC 9535, Section 2.1).
    ///
    /// @param limit the limit
    record NodelistTooLarge(int limit) implements EvaluationError {}

    /// Overflow indication: a regular expression of match() or search() is valid, but too complex
    /// to evaluate in the resource limits (RFC 9485, Section 8).
    ///
    /// @param pattern the regular expression
    /// @param limit the limit
    record RegexTooComplex(String pattern, int limit) implements EvaluationError {}

    /// A function extension returned an instance of the wrong type.
    ///
    /// @param name the function name
    /// @param expected the declared result type
    record ExtensionResultMismatch(String name, FunctionType expected) implements EvaluationError {}
}
