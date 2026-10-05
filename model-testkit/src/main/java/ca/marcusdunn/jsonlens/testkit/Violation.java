package ca.marcusdunn.jsonlens.testkit;

/// A value of a document that breaks a [Rule].
///
/// @param rule the rule
/// @param location the location of the value, as a Normalized Path, for example `$['a'][0]`
/// @param detail what the model did
public record Violation(Rule rule, String location, String detail) {
    @Override
    public String toString() {
        return rule + " at " + location + ": " + detail;
    }
}
