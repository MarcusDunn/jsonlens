package ca.marcusdunn.jsonlens.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/// An exact decimal number with no limit on its size or on its exponent.
///
/// Each JSON number has exactly one `JsonDecimal`. [JsonNumber#exactValue()] gives it, so that
/// two numbers in different representations can be compared exactly.
///
/// The value is `signum × significand × 10^exponent`:
///
/// | Number | [#signum()] | [#significand()] | [#exponent()] |
/// |---|---|---|---|
/// | `0` | 0 | `"0"` | 0 |
/// | `-12.50` | -1 | `"125"` | -1 |
/// | `1e400` | 1 | `"1"` | 400 |
/// | `1e99999999999` | 1 | `"1"` | 99999999999 |
///
/// The form is canonical: the significand has no leading zeros and no trailing zeros. Thus two
/// decimals are [equal][#equals(Object)] if and only if [#compareTo(JsonDecimal)] gives zero.
///
/// ```java
/// JsonDecimal a = JsonDecimal.of(new BigDecimal("1.50"));
/// JsonDecimal b = JsonDecimal.parse("15e-1").orElse(JsonDecimal.ZERO);
/// assert a.equals(b);
/// ```
public final class JsonDecimal implements Comparable<JsonDecimal> {

    /// The value zero.
    public static final JsonDecimal ZERO = new JsonDecimal(0, "0", BigInteger.ZERO);

    private final int signum;
    private final String significand;
    private final BigInteger exponent;

    private JsonDecimal(int signum, String significand, BigInteger exponent) {
        this.signum = signum;
        this.significand = significand;
        this.exponent = exponent;
    }

    /// Returns the decimal of a `long` value.
    ///
    /// @param value the value
    /// @return the decimal
    public static JsonDecimal of(long value) {
        return of(BigDecimal.valueOf(value));
    }

    /// Returns the decimal of a `BigInteger` value.
    ///
    /// @param value the value
    /// @return the decimal
    public static JsonDecimal of(BigInteger value) {
        return of(new BigDecimal(value));
    }

    /// Returns the decimal of a `BigDecimal` value. The scale of the value has no effect, so
    /// `1.50` and `1.5` give equal decimals.
    ///
    /// @param value the value
    /// @return the decimal
    public static JsonDecimal of(BigDecimal value) {
        // A zero becomes 0 with scale 0, so it is also canonical.
        BigDecimal stripped = value.stripTrailingZeros();
        return new JsonDecimal(
                stripped.signum(),
                stripped.unscaledValue().abs().toString(),
                BigInteger.valueOf(stripped.scale()).negate());
    }

    /// Reads the text of a JSON number (RFC 8259, Section 6).
    ///
    /// The exponent can have any number of digits. The text must contain only the number: no
    /// spaces, no leading `+`, and no leading zeros.
    ///
    /// @param text the text of a JSON number, for example `-12.5e-3`
    /// @return the decimal, or [Maybe.None] if the text is not a JSON number
    public static Maybe<JsonDecimal> parse(CharSequence text) {
        int length = text.length();
        int i = 0;
        int signum = 1;
        if (i < length && text.charAt(i) == '-') {
            signum = -1;
            i++;
        }
        StringBuilder digits = new StringBuilder();
        if (i < length && text.charAt(i) == '0') {
            i++;
        } else {
            int start = i;
            i = appendDigits(text, i, digits);
            if (i == start) {
                return Maybe.none();
            }
        }
        int integerDigits = digits.length();
        if (i < length && text.charAt(i) == '.') {
            int start = ++i;
            i = appendDigits(text, i, digits);
            if (i == start) {
                return Maybe.none();
            }
        }
        BigInteger exponent = BigInteger.valueOf(integerDigits - digits.length());
        if (i < length && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
            i++;
            boolean negative = i < length && text.charAt(i) == '-';
            if (i < length && (negative || text.charAt(i) == '+')) {
                i++;
            }
            StringBuilder written = new StringBuilder();
            int start = i;
            i = appendDigits(text, i, written);
            if (i == start) {
                return Maybe.none();
            }
            BigInteger value = DecimalDigits.parse(written);
            exponent = exponent.add(negative ? value.negate() : value);
        }
        if (i != length) {
            return Maybe.none();
        }
        return Maybe.some(canonical(signum, digits, exponent));
    }

    /// Appends the decimal digits at a position, and returns the position after them.
    private static int appendDigits(CharSequence text, int position, StringBuilder digits) {
        int i = position;
        while (i < text.length() && text.charAt(i) >= '0' && text.charAt(i) <= '9') {
            digits.append(text.charAt(i));
            i++;
        }
        return i;
    }

    /// Removes the leading and trailing zeros of the digits of `signum × digits × 10^exponent`.
    private static JsonDecimal canonical(int signum, CharSequence digits, BigInteger exponent) {
        int first = 0;
        while (first < digits.length() && digits.charAt(first) == '0') {
            first++;
        }
        if (first == digits.length()) {
            return ZERO;
        }
        int end = digits.length();
        while (digits.charAt(end - 1) == '0') {
            end--;
        }
        return new JsonDecimal(
                signum,
                digits.subSequence(first, end).toString(),
                exponent.add(BigInteger.valueOf(digits.length() - end)));
    }

    /// Returns the sign.
    ///
    /// @return -1, 0, or 1 if the value is negative, zero, or positive
    public int signum() {
        return signum;
    }

    /// Returns the digits of the significand.
    ///
    /// @return the decimal digits, without leading zeros and without trailing zeros. For zero,
    ///     the text is `"0"`.
    public String significand() {
        return significand;
    }

    /// Returns the exponent of ten.
    ///
    /// @return the exponent. For zero, the exponent is zero.
    public BigInteger exponent() {
        return exponent;
    }

    /// Returns the value as a `BigDecimal`, if a `BigDecimal` can hold it.
    ///
    /// @return the value, or [Maybe.None] if the exponent is outside the range of the scale of a
    ///     `BigDecimal` (an `int`)
    public Maybe<BigDecimal> toBigDecimal() {
        BigInteger scale = exponent.negate();
        if (scale.bitLength() >= Integer.SIZE) {
            return Maybe.none();
        }
        BigInteger digits = DecimalDigits.parse(significand);
        return Maybe.some(new BigDecimal(signum == -1 ? digits.negate() : digits, scale.intValue()));
    }

    /// Compares two decimals by their mathematical values.
    ///
    /// @param other another decimal
    /// @return -1, 0, or 1 if this value is less than, equal to, or greater than the other value
    @Override
    public int compareTo(JsonDecimal other) {
        if (signum != other.signum) {
            return Integer.compare(signum, other.signum);
        }
        return signum * compareMagnitudes(other);
    }

    private int compareMagnitudes(JsonDecimal other) {
        // The position of the first digit decides first. The significand of zero is "0" with
        // exponent 0, so it is correct for zero too.
        int byPosition = exponent.add(BigInteger.valueOf(significand.length()))
                .compareTo(other.exponent.add(BigInteger.valueOf(other.significand.length())));
        if (byPosition != 0) {
            return byPosition;
        }
        // Then the digits from the left. The significands have no trailing zeros, so if one is a
        // prefix of the other, the longer one is greater.
        return Integer.signum(significand.compareTo(other.significand));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JsonDecimal decimal
                && signum == decimal.signum
                && significand.equals(decimal.significand)
                && exponent.equals(decimal.exponent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signum, significand, exponent);
    }

    /// Returns the value as the text of a JSON number, for example `-125E-1`.
    ///
    /// @return the text. [#parse(CharSequence)] of the text gives an equal decimal.
    @Override
    public String toString() {
        String sign = signum < 0 ? "-" : "";
        return exponent.signum() == 0 ? sign + significand : sign + significand + "E" + exponent;
    }
}
