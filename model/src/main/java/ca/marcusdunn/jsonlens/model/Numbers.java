package ca.marcusdunn.jsonlens.model;

/// The default comparison of numbers, with fast paths that need no conversion.
interface Numbers {

    /// The largest magnitude up to which each `long` is exactly a `double`.
    long EXACT_DOUBLE = 1L << 53;

    /// Compares two numbers. The result has the sign of the comparison of the exact values.
    static int compare(JsonNumber a, JsonNumber b) {
        if (isLong(a)) {
            if (isLong(b)) {
                return Long.compare(longValue(a), longValue(b));
            }
            if (b instanceof DoubleNumber y && isExactDouble(longValue(a))) {
                return compareDoubles((double) longValue(a), y.value());
            }
        } else if (a instanceof DoubleNumber x) {
            if (b instanceof DoubleNumber y) {
                return compareDoubles(x.value(), y.value());
            }
            if (isLong(b) && isExactDouble(longValue(b))) {
                return compareDoubles(x.value(), (double) longValue(b));
            }
        }
        return a.exactValue().compareTo(b.exactValue());
    }

    private static boolean isLong(JsonNumber number) {
        return number instanceof LongNumber || (number instanceof DecimalNumber decimal && decimal.isLong());
    }

    private static long longValue(JsonNumber number) {
        return number instanceof LongNumber value ? value.value() : ((DecimalNumber) number).longValue();
    }

    private static boolean isExactDouble(long value) {
        return -EXACT_DOUBLE <= value && value <= EXACT_DOUBLE;
    }

    /// Compares two doubles as numbers.
    ///
    /// Not `Double.compare`: -0.0 and 0.0 are equal. The order is the order of the exact values,
    /// because the exact value of a `double` is a decimal that rounds to it: it is in the rounding
    /// interval of the `double`, and these intervals do not overlap. A `long` in the range
    /// ±2^53 is exactly a `double`, so it is its own exact value.
    private static int compareDoubles(double a, double b) {
        return a < b ? -1 : a > b ? 1 : 0;
    }
}
