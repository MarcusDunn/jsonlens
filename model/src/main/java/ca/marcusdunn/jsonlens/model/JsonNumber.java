package ca.marcusdunn.jsonlens.model;

import java.math.BigDecimal;
import java.math.BigInteger;

/// A JSON number in a representation that the model chooses.
///
/// [JsonModel#numberValue(Object)] returns a `JsonNumber`. For the common representations, use
/// the factories of this interface:
///
/// ```java
/// JsonNumber small = JsonNumber.of(42);
/// JsonNumber exact = JsonNumber.of(new BigDecimal("0.1"));
/// Maybe<JsonNumber> binary = JsonNumber.of(1.5); // NaN and the infinities are not JSON numbers
/// ```
///
/// A model can also implement this interface for its own representation, for example a number
/// that it reads from bytes only when the evaluator compares it. The only obligation is
/// [#exactValue()].
///
/// A `JsonNumber` has no order of its own. The model compares numbers with
/// [JsonModel#compareNumbers(JsonNumber, JsonNumber)], because only the model knows its
/// representations and can compare them without conversion.
public interface JsonNumber {

    /// Returns the exact value of the number.
    ///
    /// Each JSON number has an exact value, also with a very large exponent.
    ///
    /// @return the exact value
    JsonDecimal exactValue();

    /// Returns a number with an `int` value.
    ///
    /// @param value the value
    /// @return the number
    static JsonNumber of(int value) {
        return new LongNumber(value);
    }

    /// Returns a number with a `long` value.
    ///
    /// @param value the value
    /// @return the number
    static JsonNumber of(long value) {
        return new LongNumber(value);
    }

    /// Returns a number with a `BigInteger` value.
    ///
    /// @param value the value
    /// @return the number
    static JsonNumber of(BigInteger value) {
        return DecimalNumber.of(JsonDecimal.of(value));
    }

    /// Returns a number with a `BigDecimal` value.
    ///
    /// @param value the value
    /// @return the number
    static JsonNumber of(BigDecimal value) {
        return DecimalNumber.of(JsonDecimal.of(value));
    }

    /// Returns a number with a [JsonDecimal] value.
    ///
    /// @param value the value
    /// @return the number
    static JsonNumber of(JsonDecimal value) {
        return DecimalNumber.of(value);
    }

    /// Returns a number with a `double` value.
    ///
    /// The exact value is the shortest decimal that gives the `double`
    /// ([BigDecimal#valueOf(double)]), so the JSON text `1.1`, read as a `double`, equals the
    /// literal `1.1`.
    ///
    /// @param value the value
    /// @return the number, or [Maybe.None] if the value is NaN or infinite, because these values
    ///     are not JSON numbers
    static Maybe<JsonNumber> of(double value) {
        return Double.isFinite(value) ? Maybe.some(new DoubleNumber(value)) : Maybe.none();
    }
}
