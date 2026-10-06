package ca.marcusdunn.jsonlens.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/// Reads a text of decimal digits as a `BigInteger`, in less than quadratic time.
///
/// `new BigInteger(String)` takes time quadratic in the number of digits: about 10 s for a million
/// digits. This routine parses chunks of [#CHUNK] digits, and joins them in pairs, level by level,
/// with the fast multiplication of `BigInteger`: about 0.3 s for a million digits.
interface DecimalDigits {

    /// The number of digits that one `new BigInteger(String)` reads.
    int CHUNK = 1000;

    /// Reads decimal digits.
    ///
    /// @param digits one or more characters from `0` to `9`
    /// @return the value of the digits
    static BigInteger parse(CharSequence digits) {
        String text = digits.toString();
        int length = text.length();
        // The chunks align at the end of the text: only the first chunk can be shorter.
        int first = length % CHUNK == 0 ? CHUNK : length % CHUNK;
        List<BigInteger> values = new ArrayList<>();
        values.add(new BigInteger(text.substring(0, first)));
        for (int at = first; at < length; at += CHUNK) {
            values.add(new BigInteger(text.substring(at, at + CHUNK)));
        }
        // Join the values in pairs. The right value of each pair has the full width, so the left
        // value moves up by 10^width. If the number of values is odd, the first stays alone.
        BigInteger shift = BigInteger.TEN.pow(CHUNK);
        while (values.size() > 1) {
            List<BigInteger> joined = new ArrayList<>();
            int i = values.size() % 2;
            if (i == 1) {
                joined.add(values.getFirst());
            }
            for (; i < values.size(); i += 2) {
                joined.add(values.get(i).multiply(shift).add(values.get(i + 1)));
            }
            values = joined;
            shift = shift.multiply(shift);
        }
        return values.getFirst();
    }
}
