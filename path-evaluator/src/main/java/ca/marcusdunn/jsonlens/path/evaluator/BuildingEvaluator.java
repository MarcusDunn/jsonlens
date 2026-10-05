package ca.marcusdunn.jsonlens.path.evaluator;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.function.FunctionSignature;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import java.util.List;

/// An evaluator with building function extensions. It accepts only models that implement both
/// [JsonModel] and [JsonFactory].
///
/// [JsonPathEvaluator#withFunctions(List, List)] makes a building evaluator. It works as a
/// [JsonPathEvaluator], but the compiler rejects a read-only model:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.ExtensionSnippets region=building-evaluate}
///
/// A building evaluator is immutable and safe for use by more than one thread.
public final class BuildingEvaluator {

    private final Functions functions;
    private final JsonPathEvaluator.Limits limits;

    BuildingEvaluator(Functions functions, JsonPathEvaluator.Limits limits) {
        this.functions = functions;
        this.limits = limits;
    }

    /// Returns an evaluator with the same functions and other limits.
    ///
    /// @param limits the limits
    /// @return the evaluator
    public BuildingEvaluator withLimits(JsonPathEvaluator.Limits limits) {
        return new BuildingEvaluator(functions, limits);
    }

    /// Returns the limits of this evaluator.
    ///
    /// @return the limits
    public JsonPathEvaluator.Limits limits() {
        return limits;
    }

    /// Returns the signatures of the functions that this evaluator has.
    ///
    /// @return the signatures, the standard functions first
    public List<FunctionSignature> functions() {
        return List.copyOf(functions.signatures().values());
    }

    /// Applies a query to a JSON value. See [JsonPathEvaluator#evaluate(JsonPathQuery, Object, JsonModel)].
    ///
    /// @param query the query
    /// @param root the query argument: the root node value
    /// @param model the model for the node type, which can also build nodes
    /// @param <N> the node type
    /// @param <M> the type of the model
    /// @return the nodelist, or an error
    public <N, M extends JsonModel<N> & JsonFactory<N>> Result<List<Node<N>>, EvaluationError> evaluate(
            JsonPathQuery query, N root, M model) {
        Building<N> building = new Building<>() {
            @Override
            public Instance<N> apply(BuildingFunctionExtension extension, List<Instance<N>> arguments) {
                return extension.apply(arguments, model);
            }

            @Override
            public N string(JsonString value) {
                return model.string(value);
            }

            @Override
            public Maybe<N> number(JsonNumber value) {
                return model.number(value);
            }

            @Override
            public N bool(boolean value) {
                return model.bool(value);
            }

            @Override
            public N nullValue() {
                return model.nullValue();
            }
        };
        return JsonPathEvaluator.run(query, root, model, building, functions, limits);
    }
}
