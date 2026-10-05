package ca.marcusdunn.jsonlens.path.evaluator;

import ca.marcusdunn.jsonlens.path.core.function.FunctionSignature;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonModel;
import java.util.List;

/// A function extension that can make new JSON values (RFC 9535, Section 2.4). It runs only with
/// a model that also implements [JsonFactory].
///
/// A building extension can have each signature. Each ValueType argument is a node: the evaluator
/// builds a literal of the query, for example `'abc'`, with the factory of the model. A ValueType
/// result can be a new node from the factory, for example a new array.
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.ExtensionSnippets region=building}
///
/// [JsonPathEvaluator#withFunctions(List, List)] registers building extensions. The result is a
/// [BuildingEvaluator], which accepts only models that implement [JsonModel] and [JsonFactory].
///
/// A NodesType result must have nodes of the query argument with their paths, not built nodes.
/// The rules of [FunctionExtension] also apply.
public interface BuildingFunctionExtension {

    /// Returns the signature. The parser that makes the queries must know the same signature.
    ///
    /// @return the signature
    FunctionSignature signature();

    /// Applies the function.
    ///
    /// @param arguments one instance for each parameter, of the declared type of the parameter
    /// @param model the model of the query argument, to read and build nodes
    /// @param <N> the node type of the JSON model
    /// @param <M> the type of the model
    /// @return the result, an instance of the declared result type
    <N, M extends JsonModel<N> & JsonFactory<N>> Instance<N> apply(List<Instance<N>> arguments, M model);
}
