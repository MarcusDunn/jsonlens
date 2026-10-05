package ca.marcusdunn.jsonlens.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.JsonDecimal;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.util.JsonParserDelegate;
import tools.jackson.databind.json.JsonMapper;

class JacksonStreamTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /// A parser that counts its tokens, and can give one other token at a position.
    private static final class Probe extends JsonParserDelegate {
        int tokens;
        private final int replaceAt;
        private final @Nullable JsonToken replacement;

        Probe(String json, int replaceAt, @Nullable JsonToken replacement) {
            super(MAPPER.createParser(json));
            this.replaceAt = replaceAt;
            this.replacement = replacement;
        }

        Probe(String json) {
            this(json, -1, JsonToken.NOT_AVAILABLE);
        }

        boolean failStrings;

        @Override
        public String getString() {
            if (failStrings) {
                throw new tools.jackson.core.exc.StreamReadException(this, "no string");
            }
            return super.getString();
        }

        @Override
        public @Nullable JsonToken nextToken() {
            JsonToken token = super.nextToken();
            return tokens++ == replaceAt ? replacement : token;
        }
    }

    private static JacksonStream open(JsonParser parser) {
        return switch (JacksonStream.open(parser)) {
            case Result.Ok<JacksonStream, StreamError>(JacksonStream stream) -> stream;
            case Result.Err<JacksonStream, StreamError>(StreamError error) -> fail(error.message());
        };
    }

    private static JacksonStream open(String json) {
        return open(MAPPER.createParser(json));
    }

    private static <T> T present(Maybe<T> maybe) {
        return maybe.orElseGet(() -> fail("expected a present value"));
    }

    private static JacksonStreamNode at(JacksonStream stream, String... names) {
        JacksonStreamNode node = stream.root();
        for (String name : names) {
            node = present(stream.model().member(node, JsonString.of(name)));
        }
        return node;
    }

    @Test
    @Requirement("lib/jackson-stream-on-demand")
    void readsOnlyTheTokensThatAQueryNeeds() {
        Probe parser = new Probe("{\"a\": 1, \"b\": [1, 2, 3, {\"c\": 4}], \"d\": 5}");
        JacksonStream stream = open(parser);
        JsonModel<JacksonStreamNode> model = stream.model();
        assertEquals(1, parser.tokens);
        // "a" is the first member: 2 more tokens.
        assertEquals(JsonKind.NUMBER, model.kind(at(stream, "a")));
        assertEquals(3, parser.tokens);
        // The first element of "b" does not read the next elements.
        JacksonStreamNode b = at(stream, "b");
        assertEquals(JsonKind.NUMBER, model.kind(present(model.element(b, 0))));
        assertEquals(6, parser.tokens);
        // A second visit reads nothing.
        assertSame(at(stream, "a"), at(stream, "a"));
        assertSame(present(model.element(b, 0)), present(model.element(b, 0)));
        assertEquals(6, parser.tokens);
        // The length of "b" reads it to its end.
        assertEquals(4, model.arrayLength(b));
        assertEquals(13, parser.tokens);
        assertEquals(Maybe.none(), model.element(b, 4));
        assertEquals(Maybe.none(), model.element(b, -1));
        assertEquals(Maybe.none(), model.member(stream.root(), JsonString.of("missing")));
        assertEquals(3, model.memberCount(stream.root()));
        assertEquals(Maybe.none(), stream.failure());
    }

    @Test
    @Requirement("lib/jackson-stream-on-demand")
    void keepsDuplicateNames() {
        JacksonStream stream = open("{\"a\": 1, \"b\": 2, \"a\": 3}");
        JsonModel<JacksonStreamNode> model = stream.model();
        assertEquals(JsonDecimal.of(1), model.numberValue(at(stream, "a")).exactValue());
        assertTrue(model.hasDuplicate(stream.root(), JsonString.of("a")));
        assertFalse(model.hasDuplicate(stream.root(), JsonString.of("b")));
        assertEquals(List.of("a", "b", "a"),
                model.members(stream.root()).map(property -> JsonString.copyOf(property.name())).toList());
    }

    @Test
    @Requirement("lib/jackson-stream-on-demand")
    void numbersAreExact() {
        JacksonStream stream = open("[9223372036854775807, 92233720368547758070, 0.1, 1e999999999, -12.5e-3]");
        JsonModel<JacksonStreamNode> model = stream.model();
        List<String> expected = List.of("9223372036854775807", "92233720368547758070", "0.1", "1e999999999", "-12.5e-3");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(JsonDecimal.parse(expected.get(i)), Maybe.some(model.numberValue(present(model.element(stream.root(), i))).exactValue()));
        }
        // NaN, from a parser that permits it, is not a JSON number: it is a string.
        JsonMapper lenient = JsonMapper.builder().enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS).build();
        JacksonStream special = open(lenient.createParser("[NaN]"));
        JacksonStreamNode nan = present(special.model().element(special.root(), 0));
        assertEquals(JsonKind.STRING, special.model().kind(nan));
        assertEquals("NaN", JsonString.copyOf(special.model().stringValue(nan)));
    }

    @Test
    @Requirement("lib/jackson-stream-errors")
    void anErrorDuringAQueryEndsTheDocumentAndIsKept() {
        // Text that is not JSON after the first element.
        JacksonStream broken = open("[1, }");
        JsonModel<JacksonStreamNode> model = broken.model();
        assertEquals(1, model.arrayLength(broken.root()));
        assertTrue(broken.failure().isSome());
        // The end of the input inside a value.
        JacksonStream truncated = open("{\"a\": [1, 2");
        assertEquals(Maybe.none(), truncated.model().member(truncated.root(), JsonString.of("b")));
        assertTrue(truncated.failure() instanceof Maybe.Some<StreamError>(StreamError.ReadFailed error));
        // A parser that stops inside a value, with no error of its own.
        JacksonStream stopped = open(new Probe("[1, 2, 3]", 2, null));
        assertEquals(1, stopped.model().arrayLength(stopped.root()));
        assertEquals(Maybe.some(new StreamError.ReadFailed("The input ends inside a value.")), stopped.failure());
        // A token that is not a JSON value. The first error is kept.
        JacksonStream embedded = open(new Probe("[1, 2, 3]", 2, JsonToken.VALUE_EMBEDDED_OBJECT));
        assertEquals(1, embedded.model().arrayLength(embedded.root()));
        assertEquals(Maybe.some(new StreamError.NotJson("VALUE_EMBEDDED_OBJECT")), embedded.failure());
        // An accessor of the parser that fails.
        Probe strings = new Probe("[\"a\", \"b\"]");
        JacksonStream failing = open(strings);
        strings.failStrings = true;
        assertEquals(0, failing.model().arrayLength(failing.root()));
        assertEquals(Maybe.some(new StreamError.ReadFailed("no string")), failing.failure());
        assertEquals(1, embedded.model().arrayLength(embedded.root()));
    }

    @Test
    @Requirement("lib/jackson-stream-errors")
    void openAndReadFullyCheckTheInput() {
        assertEquals(Result.err(new StreamError.Empty()), JacksonStream.open(MAPPER.createParser("")));
        assertTrue(JacksonStream.open(MAPPER.createParser("]")).isErr());
        assertEquals(Result.err(new StreamError.NotJson("NOT_AVAILABLE")), JacksonStream.open(new Probe("[1]", 0, JsonToken.NOT_AVAILABLE)));
        assertTrue(JacksonStream.readFully(MAPPER.createParser("{\"a\": [1, {\"b\": null}]}")).isOk());
        assertTrue(JacksonStream.readFully(MAPPER.createParser("7")).isOk());
        assertEquals(Result.err(new StreamError.TrailingContent()), JacksonStream.readFully(MAPPER.createParser("[1] [2]")));
        assertTrue(JacksonStream.readFully(MAPPER.createParser("[1, }")).isErr());
        assertTrue(JacksonStream.readFully(MAPPER.createParser("[1] }")).isErr());
    }

    @Test
    @Requirement("lib/jackson-stream-errors")
    void errorsDescribeTheirCause() {
        assertEquals("The input has no JSON value.", new StreamError.Empty().message());
        assertEquals("The parser failed: x", new StreamError.ReadFailed("x").message());
        assertEquals("The parser gave the token T, which is not a JSON value.", new StreamError.NotJson("T").message());
        assertEquals("The input has more content after the root value.", new StreamError.TrailingContent().message());
    }
}
