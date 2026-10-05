package ca.marcusdunn.jsonlens.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.JsonDecimal;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Property;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests that a model that also implements {@link JsonFactory} is well-formed: each built node has
 * the correct kind, and the read methods give the exact value again.
 *
 * @param <N> the node type of the model
 * @param <M> the type of the model
 */
public abstract class JsonFactoryContract<N, M extends JsonModel<N> & JsonFactory<N>> extends JsonModelContract<N> {

    /// Makes the contract test. A subclass gives the model and a parser.
    protected JsonFactoryContract() {}

    /**
     * Returns the model under test.
     *
     * @return the model under test
     */
    protected abstract M factory();

    @Override
    protected JsonModel<N> model() {
        return factory();
    }

    private static <T> T present(Maybe<T> maybe) {
        return maybe.orElseGet(() -> org.junit.jupiter.api.Assertions.fail("expected a present value"));
    }

    private void assertNumber(JsonNumber number) {
        N node = present(factory().number(number));
        assertEquals(JsonKind.NUMBER, factory().kind(node));
        JsonNumber built = factory().numberValue(node);
        assertEquals(number.exactValue(), built.exactValue());
        assertEquals(0, factory().compareNumbers(number, built), number.exactValue()::toString);
        assertEquals(0, factory().compareNumbers(built, number), number.exactValue()::toString);
    }

    @Test
    void buildsNumbersWithTheirExactValue() {
        assertNumber(JsonNumber.of(0));
        assertNumber(JsonNumber.of(-7));
        assertNumber(JsonNumber.of(Long.MAX_VALUE));
        assertNumber(JsonNumber.of(new BigInteger("123456789012345678901234567890")));
        assertNumber(JsonNumber.of(new BigDecimal("0.1")));
        assertNumber(JsonNumber.of(new BigDecimal("1e400")));
        assertNumber(present(JsonNumber.of(1.5)));
    }

    @Test
    void buildsStringsWithTheirScalarValues() {
        for (String value : List.of("", "a", "é中", "😀", "\n\"\\")) {
            N node = factory().string(JsonString.of(value));
            assertEquals(JsonKind.STRING, factory().kind(node));
            assertEquals(value, JsonString.copyOf(factory().stringValue(node)));
        }
    }

    @Test
    void buildsLiterals() {
        assertEquals(JsonKind.TRUE, factory().kind(factory().bool(true)));
        assertEquals(JsonKind.FALSE, factory().kind(factory().bool(false)));
        assertEquals(JsonKind.NULL, factory().kind(factory().nullValue()));
    }

    @Test
    void buildsArraysInOrder() {
        N one = present(factory().number(JsonNumber.of(1)));
        N text = factory().string(JsonString.of("x"));
        N array = factory().array(List.of(one, text, factory().nullValue()));
        assertEquals(JsonKind.ARRAY, factory().kind(array));
        assertEquals(3, factory().arrayLength(array));
        assertEquals(JsonKind.NUMBER, factory().kind(present(factory().element(array, 0))));
        assertEquals(JsonKind.STRING, factory().kind(present(factory().element(array, 1))));
        assertEquals(JsonKind.NULL, factory().kind(present(factory().element(array, 2))));
        assertEquals(0, factory().arrayLength(factory().array(List.of())));
    }

    @Test
    void buildsObjects() {
        N object = factory().object(List.of(
                new Property<>(JsonString.of("b"), factory().bool(true)), new Property<>(JsonString.of("a"), factory().array(List.of()))));
        assertEquals(JsonKind.OBJECT, factory().kind(object));
        assertEquals(2, factory().memberCount(object));
        assertEquals(JsonKind.TRUE, factory().kind(present(factory().member(object, JsonString.of("b")))));
        assertEquals(JsonKind.ARRAY, factory().kind(present(factory().member(object, JsonString.of("a")))));
        assertTrue(factory().member(object, JsonString.of("c")).isNone());
        assertEquals(List.of("b", "a"), factory().members(object).map(property -> JsonString.copyOf(property.name())).toList());
    }
    @Test
    void numbersAreExactOrNone() {
        // A representation can have a limit, but it never builds a different value.
        JsonNumber huge = JsonNumber.of(present(JsonDecimal.parse("-1e99999999999")));
        if (factory().number(huge) instanceof Maybe.Some<N>(N node)) {
            assertEquals(huge.exactValue(), factory().numberValue(node).exactValue());
        }
    }

    @Test
    void copiesValuesOfThisModel() {
        for (String json : List.of(
                "null", "true", "false", "\"aé\"", "-12.5e-3", "[]", "{}",
                "[1, [2, [3, []]], {\"a\": {\"b\": [null, true]}}]",
                "{\"x\": [1, 2, {\"y\": \"z\"}], \"\": {}, \"n\": -0.0}")) {
            N source = parse(json);
            N copy = present(factory().copyOf(factory(), source));
            assertTrue(factory().equal(source, copy), json);
            assertTrue(factory().equal(copy, source), json);
        }
    }

    @Test
    void copiesShareNoContainerWithTheSource() {
        N source = parse("{\"a\": [1, {\"b\": 2}]}");
        N copy = present(factory().copyOf(factory(), source));
        assertNotSame(source, copy);
        N sourceArray = present(factory().member(source, JsonString.of("a")));
        N copyArray = present(factory().member(copy, JsonString.of("a")));
        assertNotSame(sourceArray, copyArray);
        assertNotSame(present(factory().element(sourceArray, 1)), present(factory().element(copyArray, 1)));
    }

    @Test
    void copiesValuesOfAnotherModel() {
        // The other model reads UTF-8 bytes.
        MappedJson source = MappedJson.of(ByteBuffer.wrap("{\"a\": [1, \"x\", true, null, 2.50], \"b\": {}}".getBytes(StandardCharsets.UTF_8)))
                .fold(json -> json, error -> org.junit.jupiter.api.Assertions.fail(error.toString()));
        N copy = present(factory().copyOf(source.model(), source.root()));
        assertTrue(factory().equal(copy, parse("{\"b\": {}, \"a\": [1, \"x\", true, null, 2.5]}")));
        assertEquals(List.of("a", "b"),
                factory().members(copy).map(property -> JsonString.copyOf(property.name())).toList());
    }

    /// Runs the JSON Patch test suite on the model, to a copy.
    ///
    /// @return a test for each test of the suite
    @TestFactory
    Stream<DynamicTest> jsonPatchSuiteToCopy() {
        return ComplianceKit.jsonPatchToCopy(factory(), this::parse);
    }
}
