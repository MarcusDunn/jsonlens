package ca.marcusdunn.jsonlens.path.evaluator;

/// An error in the function extensions given to [JsonPathEvaluator#withFunctions].
public sealed interface ExtensionError {

    /// Returns a description of the error.
    ///
    /// @return a description of the error
    default String message() {
        return switch (this) {
            case InvalidName(String name) ->
                    "The function name '" + name + "' is not valid. It must start with a lowercase letter, "
                            + "followed by lowercase letters, digits, and '_'.";
            case DuplicateName(String name) -> "More than one function has the name '" + name + "'.";
            case ValueParameter(String name) ->
                    "The read-only function '" + name + "' has a ValueType parameter. Use a BuildingFunctionExtension.";
        };
    }

    /// The name does not agree with the grammar rule `function-name`.
    ///
    /// @param name the name
    record InvalidName(String name) implements ExtensionError {}

    /// Two functions have the same name. This includes the name of a standard function.
    ///
    /// @param name the name
    record DuplicateName(String name) implements ExtensionError {}

    /// A read-only [FunctionExtension] has a ValueType parameter. A ValueType argument can be a
    /// literal, which is not a node of the query argument, and a read-only model cannot build a node
    /// for it. Use a [BuildingFunctionExtension] for such a function.
    ///
    /// @param name the function name
    record ValueParameter(String name) implements ExtensionError {}
}
