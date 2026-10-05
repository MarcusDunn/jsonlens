package ca.marcusdunn.jsonlens.model;

import java.math.BigDecimal;

/// A number with a finite `double` value.
record DoubleNumber(double value) implements JsonNumber {
    @Override
    public JsonDecimal exactValue() {
        return JsonDecimal.of(BigDecimal.valueOf(value));
    }
}
