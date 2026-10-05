package ca.marcusdunn.jsonlens.model;

import java.math.BigInteger;

/// A number with a [JsonDecimal] value.
///
/// @param value the exact value
/// @param isLong if the value is an integer in the range of `long`
/// @param longValue the value as a `long`, if `isLong`; otherwise zero
record DecimalNumber(JsonDecimal value, boolean isLong, long longValue) implements JsonNumber {

    /// The number of decimal digits that always fit in a `long`.
    private static final int LONG_DIGITS = 18;

    /// Returns the number of a decimal. It finds out once if the value fits in a `long`, so that a
    /// comparison with a `long` needs no conversion.
    static DecimalNumber of(JsonDecimal value) {
        // A canonical decimal with a negative exponent has a fraction.
        if (value.exponent().signum() < 0
                || value.exponent().compareTo(BigInteger.valueOf(LONG_DIGITS - value.significand().length())) > 0) {
            return new DecimalNumber(value, false, 0);
        }
        int exponent = value.exponent().intValue();
        long magnitude = Long.parseLong(value.significand());
        for (int i = 0; i < exponent; i++) {
            magnitude *= 10;
        }
        return new DecimalNumber(value, true, value.signum() * magnitude);
    }

    @Override
    public JsonDecimal exactValue() {
        return value;
    }
}
