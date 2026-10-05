package ca.marcusdunn.jsonlens.path.evaluator;

import ca.marcusdunn.jsonlens.path.core.function.FunctionSignature;
import ca.marcusdunn.jsonlens.model.JsonModel;
import java.util.List;

/// A read-only function extension (RFC 9535, Section 2.4). It runs with each [JsonModel], also
/// with a read-only model such as a model over a memory-mapped file.
///
/// A function extension adds a function to filter expressions. It has two parts:
///
/// 1. A [FunctionSignature] for the parser, which checks that each call is well-typed.
/// 2. This implementation for the evaluator, which applies the function.
///
/// ## Example
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.ExtensionSnippets region=extension}
///
/// Give the signature to the parser and the implementation to the evaluator:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.ExtensionSnippets region=register}
///
/// ## Arguments and results
///
/// A read-only extension cannot make new JSON values, because a read-only model cannot build
/// nodes. So its signature has these limits:
///
/// | | Permitted |
/// |---|---|
/// | Parameters | NodesType ([Instance.NodesInstance]) and LogicalType ([Instance.LogicalInstance]) |
/// | Result | LogicalType, NodesType, or ValueType ([Instance.ValueInstance]) |
///
/// A ValueType result must be a node of the query argument, for example a node of an argument
/// nodelist, or Nothing. A NodesType result must have nodes of the query argument with their
/// paths.
///
/// [JsonPathEvaluator#withFunctions(List)] rejects a read-only extension with a ValueType
/// parameter: a ValueType argument can be a literal, which is not a node of the query argument.
/// For such functions, use a [BuildingFunctionExtension].
///
/// A result of the wrong type gives [EvaluationError.ExtensionResultMismatch].
///
/// ## Rules
///
/// An implementation must have no side effects: the same arguments must always give the same
/// result. It must not throw an exception. It reads node values only through the [JsonModel] that
/// it receives.
public interface FunctionExtension {

    /// Returns the signature. The parser that makes the queries must know the same signature.
    ///
    /// @return the signature
    FunctionSignature signature();

    /// Applies the function.
    ///
    /// @param arguments one instance for each parameter, of the declared type of the parameter
    /// @param model the model of the query argument, to read node values
    /// @param <N> the node type of the JSON model
    /// @return the result, an instance of the declared result type
    <N> Instance<N> apply(List<Instance<N>> arguments, JsonModel<N> model);
}
