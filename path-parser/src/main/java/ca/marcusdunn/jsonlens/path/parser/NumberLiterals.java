package ca.marcusdunn.jsonlens.path.parser;

import ca.marcusdunn.jsonlens.model.Maybe;
import java.math.BigDecimal;
import java.math.BigInteger;

/// The value of a number literal: the same `BigDecimal` as `new BigDecimal(text)`, with the same
/// digits and the same scale, but in less than quadratic time for a long literal.
interface NumberLiterals {

    /// Returns the value of a number literal.
    ///
    /// @param text a number that agrees with the grammar of RFC 9535, Section 2.3.5.1: the parser
    ///     checked it
    /// @return the value, or [Maybe.None] if its scale is outside the range of an `int`, where
    ///     `new BigDecimal(text)` throws
    static Maybe<BigDecimal> value(String text) {
        boolean negative = text.charAt(0) == '-';
        StringBuilder digits = new StringBuilder(text.length());
        int fractionDigits = 0;
        boolean fraction = false;
        int i = negative ? 1 : 0;
        for (; i < text.length() && text.charAt(i) != 'e' && text.charAt(i) != 'E'; i++) {
            char c = text.charAt(i);
            if (c == '.') {
                fraction = true;
            } else {
                digits.append(c);
                fractionDigits += fraction ? 1 : 0;
            }
        }
        BigInteger scale = BigInteger.valueOf(fractionDigits);
        if (i < text.length()) {
            // The exponent: an optional sign and one or more digits.
            int start = i + 1;
            char sign = text.charAt(start);
            int exponentDigits = sign == '+' || sign == '-' ? start + 1 : start;
            BigInteger exponent = DecimalDigits.parse(text.substring(exponentDigits));
            scale = sign == '-' ? scale.add(exponent) : scale.subtract(exponent);
        }
        if (scale.bitLength() >= Integer.SIZE) {
            return Maybe.none();
        }
        BigInteger unscaled = DecimalDigits.parse(digits);
        return Maybe.some(new BigDecimal(negative ? unscaled.negate() : unscaled, scale.intValue()));
    }
}
