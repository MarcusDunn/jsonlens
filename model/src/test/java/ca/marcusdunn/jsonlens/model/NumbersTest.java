package ca.marcusdunn.jsonlens.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.testsupport.Requirement;
import org.junit.jupiter.api.Test;

@Requirement("lib/model-number-comparison")
class NumbersTest {

    private static final long EXACT_DOUBLE = 1L << 53;

    private static JsonDecimal exact(String text) {
        return JsonDecimal.parse(text).orElseGet(() -> fail("not a JSON number: " + text));
    }

    private static DecimalNumber decimal(String text) {
        return DecimalNumber.of(exact(text));
    }

    private static JsonNumber finite(double value) {
        return JsonNumber.of(value).orElseGet(() -> fail("not finite: " + value));
    }

    /// A probe: its long value is not its exact value (zero). The result of a comparison shows if
    /// the comparison used the long value (a fast path) or the exact value.
    private static DecimalNumber probe(long longValue) {
        return new DecimalNumber(JsonDecimal.ZERO, true, longValue);
    }

    @Test
    void decimalsKnowIfTheyFitInALong() {
        assertEquals(new DecimalNumber(JsonDecimal.ZERO, true, 0), decimal("0"));
        assertEquals(new DecimalNumber(exact("5"), true, 5), decimal("5"));
        assertEquals(new DecimalNumber(exact("-7"), true, -7), decimal("-7"));
        assertEquals(new DecimalNumber(exact("120"), true, 120), decimal("120"));
        assertEquals(new DecimalNumber(exact("1e17"), true, 100_000_000_000_000_000L), decimal("1e17"));
        assertEquals(new DecimalNumber(exact("-999999999999999999"), true, -999_999_999_999_999_999L),
                decimal("-999999999999999999"));
        assertFalse(decimal("1e18").isLong());
        assertFalse(decimal("1234567890123456789").isLong());
        assertFalse(decimal("1.5").isLong());
        assertFalse(decimal("1e99999999999").isLong());
        assertEquals(0, decimal("1.5").longValue());
    }

    @Test
    void longsCompareByTheirLongValues() {
        assertEquals(1, Numbers.compare(probe(7), JsonNumber.of(6)));
        assertEquals(-1, Numbers.compare(JsonNumber.of(6), probe(7)));
        assertEquals(0, Numbers.compare(probe(7), probe(7)));
        // A decimal that does not fit in a long uses its exact value.
        assertEquals(-1, Numbers.compare(new DecimalNumber(JsonDecimal.ZERO, false, 7), JsonNumber.of(6)));
        assertEquals(1, Numbers.compare(JsonNumber.of(6), new DecimalNumber(JsonDecimal.ZERO, false, 7)));
    }

    @Test
    void longsAndDoublesCompareAsDoublesUpTo2To53() {
        double limit = EXACT_DOUBLE;
        assertEquals(0, Numbers.compare(probe(EXACT_DOUBLE), finite(limit)));
        assertEquals(0, Numbers.compare(finite(limit), probe(EXACT_DOUBLE)));
        assertEquals(0, Numbers.compare(probe(-EXACT_DOUBLE), finite(-limit)));
        assertEquals(0, Numbers.compare(finite(-limit), probe(-EXACT_DOUBLE)));
        assertEquals(1, Numbers.compare(probe(3), finite(2.5)));
        assertEquals(-1, Numbers.compare(finite(2.5), probe(3)));
        // Above 2^53, a long is not always a double: the exact values decide.
        assertEquals(1, Numbers.compare(JsonNumber.of(EXACT_DOUBLE + 1), finite(limit)));
        assertEquals(-1, Numbers.compare(finite(limit), JsonNumber.of(EXACT_DOUBLE + 1)));
        assertEquals(-1, Numbers.compare(JsonNumber.of(-EXACT_DOUBLE - 1), finite(-limit)));
        assertEquals(1, Numbers.compare(finite(-limit), JsonNumber.of(-EXACT_DOUBLE - 1)));
    }

    @Test
    void doublesCompareAsDoubles() {
        assertEquals(-1, Numbers.compare(finite(1.5), finite(2.5)));
        assertEquals(1, Numbers.compare(finite(2.5), finite(1.5)));
        assertEquals(0, Numbers.compare(finite(1.5), finite(1.5)));
        assertEquals(0, Numbers.compare(finite(-0.0), finite(0.0)));
    }

    @Test
    void otherNumbersCompareByTheirExactValues() {
        JsonNumber custom = () -> exact("2.5");
        assertEquals(1, Numbers.compare(custom, JsonNumber.of(2)));
        assertEquals(-1, Numbers.compare(JsonNumber.of(2), custom));
        assertEquals(0, Numbers.compare(custom, finite(2.5)));
        assertEquals(0, Numbers.compare(finite(2.5), custom));
        assertEquals(-1, Numbers.compare(finite(2.5), JsonNumber.of(exact("2.75"))));
        assertTrue(Numbers.compare(JsonNumber.of(exact("1e99999999999")), JsonNumber.of(Long.MAX_VALUE)) > 0);
    }
}
