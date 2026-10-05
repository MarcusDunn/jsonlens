package ca.marcusdunn.jsonlens.testkit;

/// A rule of the [ca.marcusdunn.jsonlens.model.JsonModel] contract that [ModelVerifier] checks.
public enum Rule {
    /// No method throws an exception.
    NO_EXCEPTIONS("No method throws an exception."),
    /// No method returns `null`.
    NO_NULLS("No method returns null."),
    /// [ca.marcusdunn.jsonlens.model.JsonModel#element(Object, int)] gives an element for each index
    /// below the length, and none outside the array.
    ELEMENTS("element(i) is present for each i below arrayLength, and None outside the array."),
    /// The cursor gives the same members in the same order each time, and stays at the end.
    MEMBER_CURSOR("The cursor gives the same members in the same order each time, and next() stays false at the end."),
    /// [ca.marcusdunn.jsonlens.model.JsonModel#memberCount(Object)] gives the number of members of the
    /// cursor.
    MEMBER_COUNT("memberCount gives the number of members of the cursor."),
    /// [ca.marcusdunn.jsonlens.model.JsonModel#member(Object, ca.marcusdunn.jsonlens.model.JsonString)]
    /// gives the value of the first member of the cursor with the name.
    MEMBER("member(name) gives the value of the first member of the cursor with the name, or None."),
    /// [ca.marcusdunn.jsonlens.model.JsonModel#members(Object)] gives the members of the cursor, in
    /// order.
    MEMBERS("members() gives the members of the cursor, in order."),
    /// [ca.marcusdunn.jsonlens.model.JsonModel#hasDuplicate(Object, ca.marcusdunn.jsonlens.model.JsonString)]
    /// tells if the cursor gives two or more members with the name.
    HAS_DUPLICATE("hasDuplicate(name) tells if the cursor gives two or more members with the name."),
    /// Two reads of the same value give equal nodes with equal hash codes.
    EQUAL_NODES("Two reads of the same value give equal nodes with equal hash codes."),
    /// [ca.marcusdunn.jsonlens.model.JsonModel#stringValue(Object)] gives the same scalar values each
    /// time.
    STRING_VALUE("stringValue gives the same scalar values each time."),
    /// [ca.marcusdunn.jsonlens.model.JsonModel#numberValue(Object)] gives the same exact value each
    /// time.
    NUMBER_VALUE("numberValue gives the same exact value each time."),
    /// [ca.marcusdunn.jsonlens.model.JsonModel#compareNumbers(ca.marcusdunn.jsonlens.model.JsonNumber, ca.marcusdunn.jsonlens.model.JsonNumber)]
    /// has the sign of the comparison of the exact values.
    COMPARE_NUMBERS("compareNumbers has the sign of the comparison of the exact values."),
    /// [ca.marcusdunn.jsonlens.model.JsonModel#equal(Object, Object)] gives the result of the default
    /// method.
    EQUAL("equal gives the result of the default method.");

    private final String text;

    Rule(String text) {
        this.text = text;
    }

    /// Returns the rule as a sentence.
    ///
    /// @return the text of the rule
    public String text() {
        return text;
    }
}
