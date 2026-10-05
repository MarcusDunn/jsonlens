package ca.marcusdunn.jsonlens.path.parser;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.function.FunctionSignature;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Makes a [JsonPathQuery] from query text (RFC 9535).
///
/// The parser checks that the query is _well-formed_ (it agrees with the ABNF grammar) and
/// _valid_ (its integers are in the I-JSON range and its function expressions are well-typed).
/// It reports the first problem as a [ParseError]. It never throws an exception for a
/// non-null argument.
///
/// A parser is immutable and safe for use by more than one thread. Keep one instance and use it
/// again.
///
/// ## Parse a query
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.ParsingSnippets region=parse}
///
/// ## Handle errors
///
/// [ParseError] is a sealed interface of records. Each error has a position and a message:
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.ParsingSnippets region=errors}
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.ParsingSnippets region=messages}
///
/// ## What the parser checks
///
/// | Rule | Example of an error | Error |
/// |---|---|---|
/// | The grammar of RFC 9535 | `$.a.`, `$[?@.a = 1]`, `$ ` | [ParseError.UnexpectedCharacter], [ParseError.UnexpectedEnd] |
/// | Integers in the I-JSON range | `$[9007199254740992]` | [ParseError.IntegerOutOfRange] |
/// | Integers without leading zeros | `$[01]`, `$[-0]` | [ParseError.InvalidInteger] |
/// | Only known functions | `$[?foo(@)]` | [ParseError.UnknownFunction] |
/// | Well-typed function calls | `$[?length(@.*) > 1]`, `$[?count(@) == true && match(@)]` | [ParseError.NonSingularQuery], [ParseError.WrongArgumentCount], [ParseError.ArgumentTypeMismatch], [ParseError.NotComparable], [ParseError.ValueTypeInTest] |
/// | Unicode scalar values only | a string with an unpaired surrogate | [ParseError.UnpairedSurrogate] |
/// | A limit on nesting | 300 nested parentheses | [ParseError.NestingTooDeep] |
///
/// ## Function extensions
///
/// A parser from [#withFunctions(List)] also knows the function extensions that you give. See
/// [FunctionSignature].
public final class JsonPathParser {

    /// The maximum number of nested filter selectors, parentheses, and function calls.
    ///
    /// A deeper query gives [ParseError.NestingTooDeep]. The limit prevents a stack overflow from
    /// a query that an attacker makes (RFC 9535, Section 4.1).
    public static final int MAX_NESTING_DEPTH = 256;

    private static final JsonPathParser STANDARD = new JsonPathParser(index(FunctionSignature.STANDARD));

    private final Map<String, FunctionSignature> functions;

    private JsonPathParser(Map<String, FunctionSignature> functions) {
        this.functions = functions;
    }

    /// Returns a parser that knows the standard functions: length, count, match, search, and value.
    ///
    /// @return a parser for the standard functions
    public static JsonPathParser standard() {
        return STANDARD;
    }

    /// Returns a parser that knows the standard functions and more function extensions.
    ///
    /// The evaluator that applies the queries must have an implementation for each signature.
    ///
    /// {@snippet class=ca.marcusdunn.jsonlens.docs.ExtensionSnippets region=register}
    ///
    /// A name that is not valid gives [FunctionRegistrationError.InvalidName]. A name that is
    /// used more than once, or that is the name of a standard function, gives
    /// [FunctionRegistrationError.DuplicateName]:
    ///
    /// {@snippet class=ca.marcusdunn.jsonlens.docs.ExtensionSnippets region=duplicate}
    ///
    /// @param extensions the signatures of the function extensions
    /// @return the parser, or an error if a name is not valid or is used more than once
    public static Result<JsonPathParser, FunctionRegistrationError> withFunctions(
            List<FunctionSignature> extensions) {
        List<FunctionSignature> all = new ArrayList<>(FunctionSignature.STANDARD);
        all.addAll(extensions);
        Map<String, FunctionSignature> functions = new LinkedHashMap<>();
        for (FunctionSignature signature : all) {
            if (!FunctionSignature.isValidName(signature.name())) {
                return Result.err(new FunctionRegistrationError.InvalidName(signature.name()));
            }
            if (functions.putIfAbsent(signature.name(), signature) != null) {
                return Result.err(new FunctionRegistrationError.DuplicateName(signature.name()));
            }
        }
        return Result.ok(new JsonPathParser(Collections.unmodifiableMap(functions)));
    }

    /// Returns the functions that this parser knows.
    ///
    /// @return the function signatures, the standard functions first
    public List<FunctionSignature> functions() {
        return List.copyOf(functions.values());
    }

    /// Parses query text.
    ///
    /// The text must be the complete query. Blank space before `$` or after the last segment is
    /// not permitted. Shorthand forms become their bracket equivalents:
    ///
    /// {@snippet class=ca.marcusdunn.jsonlens.docs.ParsingSnippets region=parse}
    ///
    /// @param query the query text
    /// @return the query, or the first error
    public Result<JsonPathQuery, ParseError> parse(String query) {
        return new Parser(query, functions, MAX_NESTING_DEPTH).parse();
    }

    /// Parses query text in UTF-8 (RFC 9535, Section 2.1).
    ///
    /// Use this method for a query that comes from a network or a file. The decoder does not
    /// replace bytes that are not valid. It gives an error with the position of the first such
    /// byte:
    ///
    /// {@snippet class=ca.marcusdunn.jsonlens.docs.ParsingSnippets region=utf8}
    ///
    /// @param utf8 the query text in UTF-8
    /// @return the query, or the first error. Bytes that are not well-formed UTF-8 give
    /// [ParseError.InvalidUtf8].
    public Result<JsonPathQuery, ParseError> parse(byte[] utf8) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer in = ByteBuffer.wrap(utf8);
        CharBuffer out = CharBuffer.allocate(utf8.length);
        CoderResult result = decoder.decode(in, out, true);
        if (result.isError()) {
            return Result.err(new ParseError.InvalidUtf8(in.position()));
        }
        // The UTF-8 decoder keeps no state after the end of the input, so flush() cannot fail.
        decoder.flush(out);
        return parse(out.flip().toString());
    }

    private static Map<String, FunctionSignature> index(List<FunctionSignature> signatures) {
        Map<String, FunctionSignature> functions = new LinkedHashMap<>();
        signatures.forEach(s -> functions.put(s.name(), s));
        return Collections.unmodifiableMap(functions);
    }
}
