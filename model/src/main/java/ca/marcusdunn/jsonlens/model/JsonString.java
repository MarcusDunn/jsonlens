package ca.marcusdunn.jsonlens.model;

import java.util.PrimitiveIterator;

/// A JSON string in a representation that the model chooses.
///
/// [JsonModel#stringValue(Object)] returns a `JsonString`. The evaluator reads a string only
/// from start to end, one Unicode scalar value at a time: to compare strings, to count them for
/// `length()`, and to match regexps. So a model over UTF-8 bytes can give its strings without a
/// copy.
///
/// For a Java `String`, use [#of(String)]. It does not copy the string:
///
/// ```java
/// JsonString name = JsonString.of("Wilhelm");
/// ```
public interface JsonString {

    /// Returns the Unicode scalar values of the string, in order.
    ///
    /// Each call returns a new iterator that starts at the first scalar value.
    ///
    /// @return the scalar values
    PrimitiveIterator.OfInt scalarValues();

    /// Returns a string with the value of a Java `String`, without a copy.
    ///
    /// @param value the value
    /// @return the string
    static JsonString of(String value) {
        return new TextString(value);
    }

    /// Returns the value of a string as a Java `String`.
    ///
    /// For a string from [#of(String)], this returns the same `String`. For other strings, it
    /// makes a copy.
    ///
    /// @param string a string
    /// @return the value as a `String`
    static String copyOf(JsonString string) {
        if (string instanceof TextString text) {
            return text.value();
        }
        StringBuilder copy = new StringBuilder();
        for (PrimitiveIterator.OfInt values = string.scalarValues(); values.hasNext(); ) {
            copy.appendCodePoint(values.nextInt());
        }
        return copy.toString();
    }

    /// Compares two strings by their Unicode scalar values (RFC 9535, Section 2.3.5.2.2).
    ///
    /// This is not the order of [String#compareTo(String)], which compares UTF-16 code units.
    ///
    /// @param a a string
    /// @param b another string
    /// @return a negative value, zero, or a positive value if `a` is less than, equal to, or
    ///     greater than `b`
    static int compare(JsonString a, JsonString b) {
        PrimitiveIterator.OfInt x = a.scalarValues();
        PrimitiveIterator.OfInt y = b.scalarValues();
        while (x.hasNext() && y.hasNext()) {
            int difference = Integer.compare(x.nextInt(), y.nextInt());
            if (difference != 0) {
                return difference;
            }
        }
        return Boolean.compare(x.hasNext(), y.hasNext());
    }

    /// Returns true if two strings have the same scalar values.
    ///
    /// @param a a string
    /// @param b another string
    /// @return true if the strings are equal
    static boolean equal(JsonString a, JsonString b) {
        if (a instanceof TextString x && b instanceof TextString y) {
            return x.value().equals(y.value());
        }
        return compare(a, b) == 0;
    }

    /// Returns a hash code of the scalar values of a string.
    ///
    /// The result is the same as `String.hashCode()` of [#copyOf(JsonString)], but it makes no
    /// copy. So equal strings in different representations have the same hash code.
    ///
    /// @param string a string
    /// @return the hash code
    static int hash(JsonString string) {
        if (string instanceof TextString text) {
            return text.value().hashCode();
        }
        int hash = 0;
        for (PrimitiveIterator.OfInt values = string.scalarValues(); values.hasNext(); ) {
            int value = values.nextInt();
            if (Character.isBmpCodePoint(value)) {
                hash = 31 * hash + value;
            } else {
                hash = 31 * (31 * hash + Character.highSurrogate(value)) + Character.lowSurrogate(value);
            }
        }
        return hash;
    }

    /// Returns the number of Unicode scalar values in a string, as `length()` requires
    /// (RFC 9535, Section 2.4.4).
    ///
    /// @param string a string
    /// @return the number of scalar values
    static int length(JsonString string) {
        if (string instanceof TextString text) {
            return text.value().codePointCount(0, text.value().length());
        }
        int count = 0;
        for (PrimitiveIterator.OfInt values = string.scalarValues(); values.hasNext(); values.nextInt()) {
            count++;
        }
        return count;
    }
}
