package ca.marcusdunn.jsonlens.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

@Requirement("lib/model-number-representation")
class JsonNumberTest {

    private static final JsonModel<Object> MODEL = JavaCollectionsModel.INSTANCE;

    private static JsonDecimal exact(String text) {
        return JsonDecimal.parse(text).orElseGet(() -> fail("not a JSON number: " + text));
    }

    private static JsonNumber finite(double value) {
        return JsonNumber.of(value).orElseGet(() -> fail("not finite: " + value));
    }

    @Test
    void factoriesGiveExactValues() {
        assertEquals(exact("-3"), JsonNumber.of(-3).exactValue());
        assertEquals(exact("-9223372036854775808"), JsonNumber.of(Long.MIN_VALUE).exactValue());
        assertEquals(exact("98765432109876543210"), JsonNumber.of(new BigInteger("98765432109876543210")).exactValue());
        assertEquals(exact("1.1"), JsonNumber.of(new BigDecimal("1.10")).exactValue());
        JsonDecimal decimal = exact("1e99999999999");
        assertSame(decimal, JsonNumber.of(decimal).exactValue());
        // The shortest decimal of the double, not its exact binary value.
        assertEquals(exact("1.1"), finite(1.1).exactValue());
        assertEquals(JsonDecimal.ZERO, finite(-0.0).exactValue());
    }

    @Test
    void nonFiniteDoublesAreNotJsonNumbers() {
        assertEquals(Maybe.none(), JsonNumber.of(Double.NaN));
        assertEquals(Maybe.none(), JsonNumber.of(Double.POSITIVE_INFINITY));
        assertEquals(Maybe.none(), JsonNumber.of(Double.NEGATIVE_INFINITY));
    }

    @Test
    @Requirement("lib/model-number-comparison")
    void defaultComparisonUsesTheMathematicalValues() {
        List<List<JsonNumber>> ascending = List.of(
                List.of(JsonNumber.of(Long.MIN_VALUE)),
                List.of(finite(-1.5), JsonNumber.of(new BigDecimal("-1.50"))),
                List.of(JsonNumber.of(0), finite(-0.0), finite(0.0), JsonNumber.of(BigDecimal.ZERO)),
                List.of(finite(1.1), JsonNumber.of(new BigDecimal("1.1")), JsonNumber.of(exact("11e-1"))),
                List.of(JsonNumber.of(2L), finite(2.0), JsonNumber.of(BigInteger.TWO)),
                List.of(JsonNumber.of(Long.MAX_VALUE), JsonNumber.of(BigInteger.valueOf(Long.MAX_VALUE))),
                List.of(JsonNumber.of(new BigInteger("9223372036854775808")), (JsonNumber) () -> exact("9223372036854775808")),
                List.of(JsonNumber.of(exact("1e99999999999"))));
        for (int i = 0; i < ascending.size(); i++) {
            for (int j = 0; j < ascending.size(); j++) {
                for (JsonNumber a : ascending.get(i)) {
                    for (JsonNumber b : ascending.get(j)) {
                        int expected = Integer.compare(i, j);
                        assertEquals(expected, Integer.signum(MODEL.compareNumbers(a, b)), a + " <=> " + b);
                        assertEquals(expected, a.exactValue().compareTo(b.exactValue()), a + " <=> " + b);
                    }
                }
            }
        }
    }

    @Test
    @Requirement("lib/model-number-comparison")
    void fastPathsGiveTheSignOnly() {
        assertEquals(-1, MODEL.compareNumbers(JsonNumber.of(1), JsonNumber.of(2)));
        assertEquals(1, MODEL.compareNumbers(JsonNumber.of(Long.MAX_VALUE), JsonNumber.of(Long.MIN_VALUE)));
        assertEquals(0, MODEL.compareNumbers(JsonNumber.of(5), JsonNumber.of(5L)));
        assertEquals(-1, MODEL.compareNumbers(finite(1.5), finite(2.5)));
        assertEquals(1, MODEL.compareNumbers(finite(2.5), finite(1.5)));
        assertEquals(0, MODEL.compareNumbers(finite(1.5), finite(1.5)));
        assertEquals(0, MODEL.compareNumbers(finite(-0.0), finite(0.0)));
        assertEquals(0, MODEL.compareNumbers(finite(0.0), finite(-0.0)));
    }
}
