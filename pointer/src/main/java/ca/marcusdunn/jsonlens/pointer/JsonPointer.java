package ca.marcusdunn.jsonlens.pointer;

import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;
import org.jspecify.annotations.Nullable;

/// A JSON Pointer (RFC 6901): a sequence of reference tokens that identifies a value in a JSON
/// document.
///
/// A pointer has two text forms. The JSON string form (Section 5) is `/` followed by each token,
/// with `~` written as `~0` and `/` written as `~1`. The URI fragment form (Section 6) is `#`
/// followed by the UTF-8 bytes of the string form, with percent-encoding.
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.PointerSnippets region=resolve}
///
/// [#resolve(Object, JsonModel)] reads the caller's nodes through a [JsonModel], so it works with
/// any JSON library and makes no copy of a value. An error is a [PointerError] value, not an
/// exception.
///
/// Two pointers are equal if they have the same tokens.
public final class JsonPointer {

    private static final JsonPointer ROOT = new JsonPointer(List.of());

    /// The characters that a URI fragment permits without percent-encoding (RFC 3986, Section
    /// 3.5): unreserved characters, sub-delimiters, `:`, `@`, `/`, and `?`.
    private static final String FRAGMENT_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~!$&'()*+,;=:@/?";

    private static final String HEX = "0123456789ABCDEF";

    private final List<String> tokens;

    private JsonPointer(List<String> tokens) {
        this.tokens = tokens;
    }

    /// Returns the empty pointer, which references the whole document.
    ///
    /// @return the pointer `""`
    public static JsonPointer root() {
        return ROOT;
    }

    /// Returns the pointer with the given reference tokens. A token can be any string.
    ///
    /// @param tokens the tokens, without escapes. No token is `null`.
    /// @return the pointer
    public static JsonPointer of(List<String> tokens) {
        return new JsonPointer(List.copyOf(tokens));
    }

    /// Parses the JSON string form of a pointer (RFC 6901, Sections 3 and 5).
    ///
    /// @param text the pointer, for example `/a~1b/0`. JSON escapes such as `\"` must already be
    ///     decoded.
    /// @return the pointer, or a [PointerError.MissingSlash] or [PointerError.InvalidEscape]
    public static Result<JsonPointer, PointerError> parse(String text) {
        return parse(JsonString.of(text));
    }

    /// Parses the JSON string form of a pointer from a string of a model, for example the `path`
    /// of a JSON Patch operation. It reads the string once and makes no other copy of it.
    ///
    /// @param text the pointer
    /// @return the pointer, or a [PointerError.MissingSlash] or [PointerError.InvalidEscape]
    public static Result<JsonPointer, PointerError> parse(JsonString text) {
        PrimitiveIterator.OfInt characters = text.scalarValues();
        if (!characters.hasNext()) {
            return Result.ok(ROOT);
        }
        if (characters.nextInt() != '/') {
            return Result.err(new PointerError.MissingSlash());
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        for (int position = 1; characters.hasNext(); position++) {
            int c = characters.nextInt();
            if (c == '/') {
                tokens.add(token.toString());
                token.setLength(0);
            } else if (c == '~') {
                // One pass decodes "~01" as "~1": the order of Section 4 is automatic.
                int escaped = characters.hasNext() ? characters.nextInt() : -1;
                if (escaped == '0') {
                    token.append('~');
                } else if (escaped == '1') {
                    token.append('/');
                } else {
                    return Result.err(new PointerError.InvalidEscape(position));
                }
                position++;
            } else {
                token.appendCodePoint(c);
            }
        }
        tokens.add(token.toString());
        return Result.ok(new JsonPointer(List.copyOf(tokens)));
    }

    /// Parses the URI fragment form of a pointer (RFC 6901, Section 6), for example `#/c%25d`.
    ///
    /// @param fragment the fragment identifier, with its `#`
    /// @return the pointer, or a [PointerError]
    public static Result<JsonPointer, PointerError> parseFragment(String fragment) {
        if (fragment.isEmpty() || fragment.charAt(0) != '#') {
            return Result.err(new PointerError.InvalidFragment(0));
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // The position in the fragment of the first character of each byte.
        List<Integer> sources = new ArrayList<>();
        for (int i = 1; i < fragment.length(); i++) {
            char c = fragment.charAt(i);
            sources.add(i);
            if (c == '%') {
                int high = i + 1 < fragment.length() ? hexDigit(fragment.charAt(i + 1)) : -1;
                int low = i + 2 < fragment.length() ? hexDigit(fragment.charAt(i + 2)) : -1;
                if (high < 0 || low < 0) {
                    return Result.err(new PointerError.InvalidFragment(i));
                }
                bytes.write(high * 16 + low);
                i += 2;
            } else if (FRAGMENT_CHARACTERS.indexOf(c) >= 0) {
                bytes.write(c);
            } else {
                return Result.err(new PointerError.InvalidFragment(i));
            }
        }
        ByteBuffer input = ByteBuffer.wrap(bytes.toByteArray());
        CharBuffer output = CharBuffer.allocate(input.remaining());
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CoderResult result = decoder.decode(input, output, true);
        if (result.isError()) {
            return Result.err(new PointerError.InvalidFragment(sources.get(input.position())));
        }
        return parse(output.flip().toString());
    }

    /// Returns the reference tokens, without escapes.
    ///
    /// @return the tokens, in order. The list cannot be changed.
    public List<String> tokens() {
        return tokens;
    }

    /// Tells if this is the empty pointer, which references the whole document.
    ///
    /// @return `true` if the pointer has no tokens
    public boolean isRoot() {
        return tokens.isEmpty();
    }

    /// Returns the pointer to the parent: all tokens except the last.
    ///
    /// @return the parent, or [Maybe.None] for the empty pointer
    public Maybe<JsonPointer> parent() {
        return isRoot() ? Maybe.none() : Maybe.some(new JsonPointer(tokens.subList(0, tokens.size() - 1)));
    }

    /// Returns the last reference token.
    ///
    /// @return the last token, or [Maybe.None] for the empty pointer
    public Maybe<String> lastToken() {
        return isRoot() ? Maybe.none() : Maybe.some(tokens.getLast());
    }

    /// Returns this pointer with one more token.
    ///
    /// @param token the token, without escapes
    /// @return the longer pointer
    public JsonPointer append(String token) {
        List<String> longer = new ArrayList<>(tokens);
        longer.add(token);
        return new JsonPointer(List.copyOf(longer));
    }

    /// Tells if this pointer is a proper prefix of another pointer: the other pointer references a
    /// value inside the value of this pointer. For example, `/a` is a proper prefix of `/a/b`, but
    /// not of `/a` or `/ab`.
    ///
    /// @param other another pointer
    /// @return `true` if this pointer is a proper prefix of the other pointer
    public boolean isProperPrefixOf(JsonPointer other) {
        return tokens.size() < other.tokens.size() && other.tokens.subList(0, tokens.size()).equals(tokens);
    }

    /// Tells if a token has the syntax of an array index (RFC 6901, Section 4): `0`, or ASCII
    /// digits without a leading zero. The index can be larger than any array.
    ///
    /// @param token a reference token
    /// @return `true` if the token is an array index
    public static boolean isArrayIndex(String token) {
        int length = token.length();
        if (length == 0 || (token.charAt(0) == '0' && length > 1)) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = token.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /// Reads an array index token (RFC 6901, Section 4): `0`, or digits without a leading zero.
    ///
    /// @param token a reference token
    /// @return the index, or [Maybe.None] if the token is not an array index (see
    ///     [#isArrayIndex(String)]), or if the index is larger than the largest `int`. No array has
    ///     an element at such an index.
    public static Maybe<Integer> arrayIndex(String token) {
        if (!isArrayIndex(token) || token.length() > 10) {
            return Maybe.none();
        }
        long index = Long.parseLong(token);
        return index <= Integer.MAX_VALUE ? Maybe.some((int) index) : Maybe.none();
    }

    /// Resolves this pointer against a document (RFC 6901, Section 4).
    ///
    /// Each token selects a member of an object, by equal Unicode code points without
    /// normalization, or an element of an array, by its index. The method reads the caller's
    /// nodes directly and makes no copy.
    ///
    /// @param root the root value of the document
    /// @param model the model of the document
    /// @param <N> the node type of the model
    /// @return the referenced value, or the [PointerError] of the first token that does not
    ///     resolve
    public <N> Result<N, PointerError> resolve(N root, JsonModel<N> model) {
        N current = root;
        for (int depth = 0; depth < tokens.size(); depth++) {
            switch (child(model, current, depth)) {
                case Result.Ok<N, PointerError>(N child) -> current = child;
                case Result.Err<N, PointerError> error -> {
                    return error;
                }
            }
        }
        return Result.ok(current);
    }

    /// Resolves this pointer, and returns each value along the path: the root, the value of the
    /// first token, and so on to the referenced value.
    ///
    /// A caller that builds a changed copy of a document needs the containers on the path. The
    /// method reads the caller's nodes directly and makes no copy of a value.
    ///
    /// @param root the root value of the document
    /// @param model the model of the document
    /// @param <N> the node type of the model
    /// @return the values, one more than the tokens, or the [PointerError] of the first token that
    ///     does not resolve
    public <N> Result<List<N>, PointerError> resolvePath(N root, JsonModel<N> model) {
        List<N> values = new ArrayList<>(tokens.size() + 1);
        values.add(root);
        for (int depth = 0; depth < tokens.size(); depth++) {
            switch (child(model, values.getLast(), depth)) {
                case Result.Ok<N, PointerError>(N child) -> values.add(child);
                case Result.Err<N, PointerError>(PointerError error) -> {
                    return Result.err(error);
                }
            }
        }
        return Result.ok(List.copyOf(values));
    }

    /// Resolves one token: the child of a value.
    private <N> Result<N, PointerError> child(JsonModel<N> model, N value, int depth) {
        String token = tokens.get(depth);
        return switch (model.kind(value)) {
            case OBJECT -> member(model, value, depth, token);
            case ARRAY -> element(model, value, depth, token);
            default -> Result.err(new PointerError.NotAContainer(prefix(depth)));
        };
    }

    /// The pointer to the value at a depth. The method makes it only for an error.
    private JsonPointer prefix(int depth) {
        return new JsonPointer(tokens.subList(0, depth));
    }

    private <N> Result<N, PointerError> member(JsonModel<N> model, N object, int depth, String token) {
        JsonString name = JsonString.of(token);
        if (!(model.member(object, name) instanceof Maybe.Some<N>(N member))) {
            return Result.err(new PointerError.MemberNotFound(prefix(depth), token));
        }
        if (model.hasDuplicate(object, name)) {
            return Result.err(new PointerError.DuplicateName(prefix(depth), token));
        }
        return Result.ok(member);
    }

    private <N> Result<N, PointerError> element(JsonModel<N> model, N array, int depth, String token) {
        if (!isArrayIndex(token)) {
            return Result.err(new PointerError.InvalidIndex(prefix(depth), token));
        }
        // An index larger than the largest int is valid, but no array has an element there.
        if (arrayIndex(token) instanceof Maybe.Some<Integer>(Integer index)
                && model.element(array, index) instanceof Maybe.Some<N>(N element)) {
            return Result.ok(element);
        }
        return Result.err(new PointerError.IndexOutOfRange(prefix(depth), token));
    }

    /// The value of an ASCII hexadecimal digit (RFC 3986, Section 2.1: HEXDIG).
    ///
    /// @return the value, or -1 if the character is not an ASCII hexadecimal digit
    private static int hexDigit(char c) {
        int index = "0123456789ABCDEFabcdef".indexOf(c);
        return index < 16 ? index : index - 6;
    }

    /// Returns the URI fragment form (RFC 6901, Section 6): `#` and the UTF-8 bytes of the string
    /// form, with percent-encoding for each byte that a fragment does not permit.
    ///
    /// UTF-8 encodes only Unicode scalar values. A token can also hold an unpaired surrogate, for
    /// example from the JSON escape `\ud800`. Such a pointer has no fragment form.
    ///
    /// @return the fragment identifier, for example `#/c%25d`, or [Maybe.None] if a token has an
    ///     unpaired surrogate
    public Maybe<String> toFragment() {
        String text = toString();
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(text)) {
            return Maybe.none();
        }
        StringBuilder fragment = new StringBuilder("#");
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            int value = b & 0xFF;
            if (FRAGMENT_CHARACTERS.indexOf(value) >= 0) {
                fragment.append((char) value);
            } else {
                fragment.append('%').append(HEX.charAt(value >> 4)).append(HEX.charAt(value & 0xF));
            }
        }
        return Maybe.some(fragment.toString());
    }

    /// Returns the JSON string form (RFC 6901, Section 5): `/` before each token, with `~` as `~0`
    /// and `/` as `~1`.
    ///
    /// @return the pointer, for example `/a~1b/0`
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (String token : tokens) {
            text.append('/').append(token.replace("~", "~0").replace("/", "~1"));
        }
        return text.toString();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof JsonPointer pointer && tokens.equals(pointer.tokens);
    }

    @Override
    public int hashCode() {
        return tokens.hashCode();
    }
}
