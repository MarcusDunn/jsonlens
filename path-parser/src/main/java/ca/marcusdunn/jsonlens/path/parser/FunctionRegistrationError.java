package ca.marcusdunn.jsonlens.path.parser;

/// An error in the function signatures given to [JsonPathParser#withFunctions].
public sealed interface FunctionRegistrationError {

    /// Returns a description of the error.
    ///
    /// @return a description of the error
    default String message() {
        return switch (this) {
            case InvalidName(String name) ->
                    "The function name '" + name + "' is not valid. It must start with a lowercase letter, "
                            + "followed by lowercase letters, digits, and '_'.";
            case DuplicateName(String name) -> "More than one function has the name '" + name + "'.";
        };
    }

    /// The name does not agree with the grammar rule `function-name`.
    ///
    /// @param name the name
    record InvalidName(String name) implements FunctionRegistrationError {}

    /// Two functions have the same name. This includes the name of a standard function.
    ///
    /// @param name the name
    record DuplicateName(String name) implements FunctionRegistrationError {}
}
