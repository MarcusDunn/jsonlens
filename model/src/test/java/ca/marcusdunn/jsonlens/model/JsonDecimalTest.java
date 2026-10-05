package ca.marcusdunn.jsonlens.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

@Requirement("lib/model-number-representation")
class JsonDecimalTest {

    private static JsonDecimal parse(String text) {
        return switch (JsonDecimal.parse(text)) {
            case Maybe.Some<JsonDecimal>(JsonDecimal value) -> value;
            case Maybe.None<JsonDecimal>() -> fail("not a JSON number: " + text);
        };
    }

    private static void assertForm(int signum, String significand, String exponent, JsonDecimal decimal) {
        assertEquals(signum, decimal.signum(), decimal::toString);
        assertEquals(significand, decimal.significand(), decimal::toString);
        assertEquals(new BigInteger(exponent), decimal.exponent(), decimal::toString);
    }

    @Test
    void parsesJsonNumbersToTheCanonicalForm() {
        assertForm(0, "0", "0", parse("0"));
        assertForm(0, "0", "0", parse("-0"));
        assertForm(0, "0", "0", parse("0.000"));
        assertForm(0, "0", "0", parse("-0.0e5"));
        assertForm(0, "0", "0", parse("0e99999999999999999999"));
        assertForm(1, "1", "0", parse("1"));
        assertForm(1, "1", "2", parse("100"));
        assertForm(-1, "125", "-1", parse("-12.50"));
        assertForm(1, "1", "-3", parse("0.001"));
        assertForm(1, "1001", "-1", parse("10.010e1"));
        assertForm(1, "125", "-4", parse("12.5e-3"));
        assertForm(1, "1", "2", parse("1E+2"));
        assertForm(1, "1", "2", parse("1e2"));
        assertForm(1, "1", "-2", parse("1e-2"));
        assertForm(1, "1", "99999999999999999999", parse("1e99999999999999999999"));
        assertForm(-1, "9223372036854775808", "0", parse("-9223372036854775808"));
    }

    @Test
    void rejectsTextThatIsNotAJsonNumber() {
        for (String text : List.of(
                "", "-", "+1", "01", "-01", "00", "1.", ".5", "1.e5", "1e", "1e+", "1e-", "1E5e", "1 ", " 1",
                "1.5.", "1x", "e5", "-a", "0x10", "1/", "1:", "/", ":", "1./", "1.:", "1e/", "1e:", "NaN")) {
            assertEquals(Maybe.none(), JsonDecimal.parse(text), text);
        }
    }

    @Test
    void factoriesGiveTheCanonicalForm() {
        assertSame(JsonDecimal.ZERO, JsonDecimal.ZERO);
        assertEquals(JsonDecimal.ZERO, JsonDecimal.of(0));
        assertEquals(JsonDecimal.ZERO, JsonDecimal.of(new BigDecimal("0.000")));
        assertEquals(JsonDecimal.ZERO, JsonDecimal.of(new BigDecimal("0E+5")));
        assertEquals(parse("1.5"), JsonDecimal.of(new BigDecimal("1.50")));
        assertEquals(parse("-15e3"), JsonDecimal.of(new BigDecimal("-1.5E+4")));
        assertEquals(parse("-9223372036854775808"), JsonDecimal.of(Long.MIN_VALUE));
        assertEquals(parse("1e2"), JsonDecimal.of(100));
        assertEquals(parse("98765432109876543210"), JsonDecimal.of(new BigInteger("98765432109876543210")));
    }

    @Test
    void convertsToBigDecimalInTheRangeOfTheScale() {
        assertEquals(Maybe.some(BigDecimal.ZERO), JsonDecimal.ZERO.toBigDecimal());
        assertEquals(Maybe.some(new BigDecimal("-12.5")), parse("-12.5").toBigDecimal());
        assertEquals(Maybe.some(new BigDecimal("1e400")), parse("1e400").toBigDecimal());
        // The scale is the negated exponent, and it must be an int.
        assertEquals(Maybe.some(new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE)), parse("1e2147483648").toBigDecimal());
        assertEquals(Maybe.none(), parse("1e2147483649").toBigDecimal());
        assertEquals(Maybe.some(new BigDecimal(BigInteger.ONE, Integer.MAX_VALUE)), parse("1e-2147483647").toBigDecimal());
        assertEquals(Maybe.none(), parse("1e-2147483648").toBigDecimal());
    }

    @Test
    void comparesMathematicalValues() {
        List<String> ascending = List.of(
                "-1e99999999999", "-1e400", "-100", "-99", "-19", "-12.5", "-12", "-1.25", "-1.2", "-1e-400",
                "0", "1e-99999999999", "1e-400", "0.1", "1.2", "1.25", "12", "12.5", "19", "99", "100", "1e400",
                "1e99999999999");
        for (int i = 0; i < ascending.size(); i++) {
            for (int j = 0; j < ascending.size(); j++) {
                JsonDecimal a = parse(ascending.get(i));
                JsonDecimal b = parse(ascending.get(j));
                assertEquals(Integer.compare(i, j), a.compareTo(b), a + " <=> " + b);
                assertEquals(Integer.compare(i, j), a.compareTo(parse(ascending.get(j))), a + " <=> " + b);
            }
        }
        assertEquals(0, parse("12.50").compareTo(parse("125e-1")));
    }

    @Test
    void equalityMatchesComparison() {
        assertEquals(parse("12.50"), parse("125e-1"));
        assertEquals(parse("12.50").hashCode(), parse("125e-1").hashCode());
        assertNotEquals(parse("12"), parse("13"));
        assertNotEquals(parse("1"), parse("10"));
        assertNotEquals(parse("1"), parse("-1"));
        assertNotEquals(parse("1"), parse("0"));
        assertNotEquals(parse("1"), (Object) "1");
        assertNotEquals(0, parse("1").hashCode());
        assertNotEquals(parse("1").hashCode(), parse("-1").hashCode());
    }

    @Test
    void textIsAJsonNumber() {
        assertEquals("0", JsonDecimal.ZERO.toString());
        assertEquals("125", parse("125").toString());
        assertEquals("-125E-1", parse("-12.5").toString());
        assertEquals("1E400", parse("1e400").toString());
        for (String text : List.of("0", "-12.5", "1e400", "-1e-99999999999", "123")) {
            assertEquals(parse(text), parse(parse(text).toString()), text);
        }
    }
}
