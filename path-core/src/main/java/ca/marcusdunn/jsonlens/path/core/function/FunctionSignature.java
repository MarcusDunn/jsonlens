package ca.marcusdunn.jsonlens.path.core.function;

import java.util.List;

/// The name and the declared types of a function extension (RFC 9535, Section 2.4).
///
/// The parser uses the signatures to check that a query is well-typed. For example, a function
/// with the result type [FunctionType#LOGICAL] cannot be a side of a comparison.
///
/// ## Standard functions
///
/// | Constant | Signature | Result |
/// |---|---|---|
/// | [#LENGTH] | `length(ValueType): ValueType` | the length of a string, an array, or an object |
/// | [#COUNT] | `count(NodesType): ValueType` | the number of nodes in a nodelist |
/// | [#MATCH] | `match(ValueType, ValueType): LogicalType` | true if the complete string matches a regular expression |
/// | [#SEARCH] | `search(ValueType, ValueType): LogicalType` | true if a substring matches a regular expression |
/// | [#VALUE] | `value(NodesType): ValueType` | the value of a nodelist with one node |
///
/// ## Function extensions
///
/// Declare a signature, and give it to the parser. The evaluator needs an implementation with the
/// same signature:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.ExtensionSnippets region=extension}
///
/// A valid name starts with a lowercase letter, followed by lowercase letters, digits, and
/// "_". The parser checks the name when it registers the signature. A function extension cannot
/// have the name of a standard function:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.ExtensionSnippets region=duplicate}
///
/// @param name the function name
/// @param resultType the declared type of the result
/// @param parameterTypes the declared types of the parameters, in order
public record FunctionSignature(String name, FunctionType resultType, List<FunctionType> parameterTypes) {

    /// `length(ValueType): ValueType` (RFC 9535, Section 2.4.4).
    public static final FunctionSignature LENGTH =
            new FunctionSignature("length", FunctionType.VALUE, List.of(FunctionType.VALUE));

    /// `count(NodesType): ValueType` (RFC 9535, Section 2.4.5).
    public static final FunctionSignature COUNT =
            new FunctionSignature("count", FunctionType.VALUE, List.of(FunctionType.NODES));

    /// `match(ValueType, ValueType): LogicalType` (RFC 9535, Section 2.4.6).
    public static final FunctionSignature MATCH = new FunctionSignature(
            "match", FunctionType.LOGICAL, List.of(FunctionType.VALUE, FunctionType.VALUE));

    /// `search(ValueType, ValueType): LogicalType` (RFC 9535, Section 2.4.7).
    public static final FunctionSignature SEARCH = new FunctionSignature(
            "search", FunctionType.LOGICAL, List.of(FunctionType.VALUE, FunctionType.VALUE));

    /// `value(NodesType): ValueType` (RFC 9535, Section 2.4.8).
    public static final FunctionSignature VALUE =
            new FunctionSignature("value", FunctionType.VALUE, List.of(FunctionType.NODES));

    /// The functions of the IANA "Function Extensions" subregistry (RFC 9535, Section 3.2).
    public static final List<FunctionSignature> STANDARD = List.of(LENGTH, COUNT, MATCH, SEARCH, VALUE);

    /// Makes a signature.
    ///
    /// @param name the function name
    /// @param resultType the declared type of the result
    /// @param parameterTypes the declared types of the parameters, in order
    public FunctionSignature {
        parameterTypes = List.copyOf(parameterTypes);
    }

    /// Returns true if a name agrees with the grammar rule `function-name`.
    ///
    /// @param name a name
    /// @return true for a lowercase letter followed by lowercase letters, digits, and "_"
    public static boolean isValidName(String name) {
        if (name.isEmpty() || !isLowercase(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!isLowercase(c) && !(c >= '0' && c <= '9') && c != '_') {
                return false;
            }
        }
        return true;
    }

    private static boolean isLowercase(char c) {
        return c >= 'a' && c <= 'z';
    }
}
