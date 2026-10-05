package ca.marcusdunn.jsonlens.model;

/// A number with a `long` value.
record LongNumber(long value) implements JsonNumber {
    @Override
    public JsonDecimal exactValue() {
        return JsonDecimal.of(value);
    }
}
