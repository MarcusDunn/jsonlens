package ca.marcusdunn.jsonlens.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import java.math.BigInteger;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Shows that the JsonModel interface can be implemented for Map and List values. */
class JsonModelTest {

    private final JsonModel<Object> model = JavaCollectionsModel.INSTANCE;

    private static <T> T present(Maybe<T> maybe) {
        return switch (maybe) {
            case Maybe.Some<T>(T value) -> value;
            case Maybe.None<T>() -> fail("expected a present value");
        };
    }

    @Test
    @Requirement("1.3/json-value-kinds")
    void classifiesTheSevenKinds() {
        assertEquals(JsonKind.OBJECT, model.kind(Map.of()));
        assertEquals(JsonKind.ARRAY, model.kind(List.of()));
        assertEquals(JsonKind.STRING, model.kind("a"));
        assertEquals(JsonKind.NUMBER, model.kind(1.5));
        assertEquals(JsonKind.TRUE, model.kind(true));
        assertEquals(JsonKind.FALSE, model.kind(false));
        assertEquals(JsonKind.NULL, model.kind(JavaCollectionsModel.NULL));
        for (JsonKind kind : JsonKind.values()) {
            assertTrue(kind.isStructured() != kind.isPrimitive());
        }
        assertTrue(JsonKind.OBJECT.isStructured() && JsonKind.ARRAY.isStructured());
        assertFalse(JsonKind.STRING.isStructured());
        assertFalse(JsonKind.NULL.isStructured());
        assertTrue(JsonKind.TRUE.isPrimitive());
    }

    @Test
    @Requirement("lib/model-zero-copy")
    void givesTheCallersOwnNodes() {
        List<Object> inner = new ArrayList<>(List.of(1, 2));
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("a", inner);
        List<Object> array = List.of(object);

        assertSame(object, present(model.element(array, 0)));
        assertSame(inner, present(model.member(object, JsonString.of("a"))));
        assertSame(inner, model.members(object).findFirst().orElseThrow().value());
    }

    @Test
    @Requirement("lib/model-explicit-absence")
    void absenceIsEmptyAndNullIsAValue() {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("n", null);
        List<Object> array = Arrays.asList(1, null);

        assertEquals(Maybe.none(), model.member(object, JsonString.of("missing")));
        assertEquals(JsonKind.NULL, model.kind(present(model.member(object, JsonString.of("n")))));
        assertEquals(Maybe.none(), model.element(array, 2));
        assertEquals(Maybe.none(), model.element(array, -1));
        assertEquals(JsonKind.NULL, model.kind(present(model.element(array, 1))));
    }

    @Test
    void readsPrimitiveValuesAndSizes() {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("x", 1);
        object.put("y", 2);
        assertEquals(2, model.memberCount(object));
        assertEquals(List.of("x", "y"), model.members(object).map(property -> JsonString.copyOf(property.name())).toList());
        assertEquals(3, model.arrayLength(List.of(1, 2, 3)));
        assertEquals("text", JsonString.copyOf(model.stringValue("text")));
        assertEquals(exact("0.1"), model.numberValue(0.1).exactValue());
        assertEquals(exact("1e400"), model.numberValue(new BigDecimal("1e400")).exactValue());
    }

    @Test
    @Requirement("lib/model-number-representation")
    void numbersInTheRepresentationOfTheModel() {
        assertEquals(exact("7"), model.numberValue(7).exactValue());
        assertEquals(exact("7"), model.numberValue(7L).exactValue());
        assertEquals(exact("12345678901234567890"), model.numberValue(new BigInteger("12345678901234567890")).exactValue());
        assertEquals(exact("1.5"), model.numberValue(1.5f).exactValue());
        JsonNumber custom = () -> JsonDecimal.ZERO;
        assertEquals(JsonKind.NUMBER, model.kind(custom));
        assertSame(custom, model.numberValue(custom));
    }

    @Test
    @Requirement("lib/model-factory")
    void factoryBuildsNodesThatGiveTheirValuesAgain() {
        JavaCollectionsModel factory = JavaCollectionsModel.INSTANCE;
        Object number = present(factory.number(JsonNumber.of(new BigDecimal("2.50"))));
        assertEquals(JsonKind.NUMBER, factory.kind(number));
        assertEquals(exact("2.50"), factory.numberValue(number).exactValue());
        assertEquals("x\u00e9", JsonString.copyOf(factory.stringValue(factory.string(JsonString.of("x\u00e9")))));
        assertEquals(JsonKind.TRUE, factory.kind(factory.bool(true)));
        assertEquals(JsonKind.FALSE, factory.kind(factory.bool(false)));
        assertEquals(JsonKind.NULL, factory.kind(factory.nullValue()));
        Object array = factory.array(List.of(number, factory.nullValue()));
        assertEquals(2, factory.arrayLength(array));
        Object object = factory.object(List.of(new Property<>(JsonString.of("b"), array), new Property<>(JsonString.of("a"), number)));
        assertEquals(List.of("b", "a"), factory.members(object).map(property -> JsonString.copyOf(property.name())).toList());
        assertSame(array, present(factory.member(object, JsonString.of("b"))));
    }

    private static JsonDecimal exact(String text) {
        return JsonDecimal.of(new BigDecimal(text));
    }
}
