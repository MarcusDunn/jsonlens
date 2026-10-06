package ca.marcusdunn.jsonlens.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DecimalDigitsTest {

    private static String digits(Random random, int length) {
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append((char) ('0' + random.nextInt(10)));
        }
        return text.toString();
    }

    @Test
    void readsTheSameValueAsBigIntegerAtEachChunkBoundary() {
        Random random = new Random(1);
        for (int length : new int[] {1, 2, 999, 1000, 1001, 1999, 2000, 2001, 2999, 3000, 3001, 4001, 5000, 7001}) {
            String text = digits(random, length);
            assertEquals(new BigInteger(text), DecimalDigits.parse(text), "length " + length);
        }
    }

    @Test
    void readsTheSameValueAsBigIntegerForRandomLengths() {
        Random random = new Random(2);
        for (int i = 0; i < 40; i++) {
            String text = digits(random, 1 + random.nextInt(12_000));
            assertEquals(new BigInteger(text), DecimalDigits.parse(text), text.length() + " digits");
        }
    }

    @Test
    void keepsLeadingZerosAndZeroChunks() {
        String text = "0".repeat(1500) + "7" + "0".repeat(2500);
        assertEquals(new BigInteger(text), DecimalDigits.parse(text));
        assertEquals(BigInteger.ZERO, DecimalDigits.parse("0".repeat(3000)));
    }
}
