package ca.marcusdunn.jsonlens.path.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.model.Maybe;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class NumberLiteralsTest {

    /// The reference: `new BigDecimal(text)`, or None where it throws.
    private static Maybe<BigDecimal> expected(String text) {
        try {
            return Maybe.some(new BigDecimal(text));
        } catch (NumberFormatException e) {
            return Maybe.none();
        }
    }

    private static void check(String text) {
        // Maybe.equals uses BigDecimal.equals, which also compares the scale.
        assertEquals(expected(text), NumberLiterals.value(text), text);
    }

    @Test
    void givesTheSameDigitsAndScaleAsBigDecimal() {
        for (String text : List.of("0", "-0", "7", "-7", "10", "1.50", "-0.000", "0.5", "12.5e-3", "1e3", "1E+3", "1e-3",
                "-1.5E+10", "1e007", "1.25e0", "123456789012345678901234567890", "0e5", "-0.0e-0")) {
            check(text);
        }
    }

    @Test
    void theScaleMustBeAnInt() {
        // The scale is the fraction digits minus the exponent: the limits of an int on both sides.
        for (String text : List.of("1e2147483647", "1e2147483648", "1e-2147483647", "1e-2147483648", "1.5e2147483648",
                "1.5e2147483649", "1.5e-2147483646", "1.5e-2147483647", "1e99999999999999999999", "1e-99999999999999999999",
                "0e99999999999", "1e00000000000000000000000000000001")) {
            check(text);
        }
    }

    @Test
    void longLiteralsHaveTheSameValue() {
        check("9".repeat(5000));
        check("-1." + "3".repeat(4000) + "e-12");
        check("1" + "0".repeat(2500) + "e" + "1".repeat(3));
    }

    @Test
    void randomLiteralsHaveTheSameValue() {
        Random random = new Random(9535);
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            StringBuilder text = new StringBuilder();
            if (random.nextBoolean()) {
                text.append('-');
            }
            int integer = random.nextInt(6);
            text.append(integer == 0 ? "0" : String.valueOf(1 + random.nextInt(9)));
            for (int d = 1; d < integer; d++) {
                text.append(random.nextInt(10));
            }
            if (random.nextBoolean()) {
                text.append('.');
                for (int d = 0; d <= random.nextInt(6); d++) {
                    text.append(random.nextInt(10));
                }
            }
            if (random.nextBoolean()) {
                text.append(random.nextBoolean() ? 'e' : 'E').append(List.of("", "+", "-").get(random.nextInt(3)));
                text.append(random.nextInt(12) == 0 ? "2147483648" : String.valueOf(random.nextInt(400)));
            }
            texts.add(text.toString());
        }
        texts.forEach(NumberLiteralsTest::check);
    }
}
