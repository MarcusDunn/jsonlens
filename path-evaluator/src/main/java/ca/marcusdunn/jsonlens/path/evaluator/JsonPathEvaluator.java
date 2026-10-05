package ca.marcusdunn.jsonlens.path.evaluator;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.function.FunctionSignature;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Applies a [JsonPathQuery] to a JSON value (RFC 9535).
///
/// The evaluator reads the JSON value through a [JsonModel]. It does not copy the value.
/// The result is a nodelist: each [Node] has the caller's own node instance and its
/// Normalized Path.
///
/// For a well-formed [JsonModel] and non-null arguments, the evaluator never throws an
/// exception. It never calls a model method with a `null` argument, and it calls a method
/// that is specific to a kind only for a node of that kind.
///
/// An evaluator is immutable and safe for use by more than one thread. Keep one instance and use
/// it again.
///
/// ## Evaluate a query
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.EvaluationSnippets region=evaluate}
///
/// The result nodes are in the order that RFC 9535 specifies. Array elements are in array order.
/// Object members are in the order of [JsonModel#memberCursor(Object)].
///
/// ## Filters and functions
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.EvaluationSnippets region=filters}
///
/// ## Errors
///
/// A query from the parser is valid, so for such a query only the overflow indications of
/// [Limits] can occur. A query that a caller makes directly can also give a validity error:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.EvaluationSnippets region=hand-built-error}
///
/// ## Function extensions
///
/// An evaluator from [#withFunctions(List)] also has the read-only function extensions that you give.
/// It still accepts each [JsonModel]. See [FunctionExtension].
///
/// An extension that makes new JSON values is a [BuildingFunctionExtension]. Register it with
/// [#withFunctions(List, List)], which gives a [BuildingEvaluator]. A building evaluator accepts only a
/// model that also implements [JsonFactory].
public final class JsonPathEvaluator {

    /// The maximum number of nested expressions, filters, and function calls in a query. A query
    /// from the parser is always below this limit. A deeper query gives
    /// [EvaluationError.QueryTooDeep].
    public static final int MAX_QUERY_DEPTH = 1024;

    /// Resource limits. When an evaluation passes a limit, the result is an overflow indication
    /// (RFC 9535, Section 2.1), not a wrong result.
    ///
    /// | Limit | Error when the evaluation passes it |
    /// |---|---|
    /// | [#maxNodes()] | [EvaluationError.NodelistTooLarge] |
    /// | [#maxRegexSize()] | [EvaluationError.RegexTooComplex] |
    ///
    /// {@snippet class=ca.marcusdunn.jsonlens.docs.EvaluationSnippets region=limits}
    ///
    /// @param maxNodes the maximum number of nodes in a nodelist
    /// @param maxRegexSize the maximum number of program instructions for a regular expression
    public record Limits(int maxNodes, int maxRegexSize) {

        /// 10,000,000 nodes and 10,000 regular expression instructions.
        public static final Limits DEFAULT = new Limits(10_000_000, 10_000);
    }

    private static final JsonPathEvaluator STANDARD = new JsonPathEvaluator(Functions.STANDARD, Limits.DEFAULT);

    private final Functions functions;
    private final Limits limits;

    private JsonPathEvaluator(Functions functions, Limits limits) {
        this.functions = functions;
        this.limits = limits;
    }

    /// Returns an evaluator with the standard functions (length, count, match, search, and value)
    /// and the default limits.
    ///
    /// @return an evaluator with the standard functions
    public static JsonPathEvaluator standard() {
        return STANDARD;
    }

    /// Returns an evaluator with the standard functions and more function extensions.
    ///
    /// The parser that makes the queries must know the same signatures:
    ///
    /// {@snippet class=ca.marcusdunn.jsonlens.docs.ExtensionSnippets region=register}
    ///
    /// @param extensions the function extensions
    /// @return the evaluator, or an error if a name is not valid or is used more than once, or if a
    ///     read-only extension has a ValueType parameter
    public static Result<JsonPathEvaluator, ExtensionError> withFunctions(List<FunctionExtension> extensions) {
        return Functions.of(extensions, List.of()).map(functions -> new JsonPathEvaluator(functions, Limits.DEFAULT));
    }

    /// Returns an evaluator with the standard functions, read-only function extensions, and
    /// building function extensions.
    ///
    /// A building extension can make new JSON values, so the evaluator accepts only models that
    /// also implement [JsonFactory]. See [BuildingFunctionExtension].
    ///
    /// @param readOnly the read-only function extensions
    /// @param building the building function extensions
    /// @return the evaluator, or an error if a name is not valid or is used more than once, or if a
    ///     read-only extension has a ValueType parameter
    public static Result<BuildingEvaluator, ExtensionError> withFunctions(
            List<FunctionExtension> readOnly, List<BuildingFunctionExtension> building) {
        return Functions.of(readOnly, building).map(functions -> new BuildingEvaluator(functions, Limits.DEFAULT));
    }

    /// Returns an evaluator with the same functions and other limits.
    ///
    /// @param limits the limits
    /// @return the evaluator
    public JsonPathEvaluator withLimits(Limits limits) {
        return new JsonPathEvaluator(functions, limits);
    }

    /// Returns the limits of this evaluator.
    ///
    /// @return the limits
    public Limits limits() {
        return limits;
    }

    /// Returns the signatures of the functions that this evaluator has.
    ///
    /// @return the signatures, the standard functions first
    public List<FunctionSignature> functions() {
        return List.copyOf(functions.signatures().values());
    }

    /// Applies a query to a JSON value.
    ///
    /// The evaluator first checks the validity rules that the Java types of a query cannot express:
    /// the I-JSON range of integers, the signatures of function calls, and the nesting depth. A
    /// query from the parser always obeys these rules. Then the evaluator applies the segments of
    /// the query, one after the other, to the root node.
    ///
    /// {@snippet class=ca.marcusdunn.jsonlens.docs.EvaluationSnippets region=evaluate}
    ///
    /// | Result | When |
    /// |---|---|
    /// | `Ok` with an empty list | the query selects nothing. This is not an error. |
    /// | `Ok` with nodes | the query selects nodes |
    /// | `Err` with [EvaluationError.NodelistTooLarge] or [EvaluationError.RegexTooComplex] | a limit of [Limits] is passed |
    /// | `Err` with another [EvaluationError] | the query is not valid, or a function extension does not obey its signature |
    ///
    /// @param query the query
    /// @param root the query argument: the root node value
    /// @param model the model for the node type
    /// @param <N> the node type
    /// @return the nodelist, or an error
    public <N> Result<List<Node<N>>, EvaluationError> evaluate(JsonPathQuery query, N root, JsonModel<N> model) {
        return run(query, root, model, null, functions, limits);
    }

    /** Validates and evaluates a query. */
    static <N> Result<List<Node<N>>, EvaluationError> run(
            JsonPathQuery query,
            N root,
            JsonModel<N> model,
            @Nullable Building<N> building,
            Functions functions,
            Limits limits) {
        EvaluationError invalid = QueryValidator.validate(query, functions.signatures(), MAX_QUERY_DEPTH);
        if (invalid != null) {
            return Result.err(invalid);
        }
        return new Evaluation<>(model, building, root, functions, limits).run(query);
    }
}
