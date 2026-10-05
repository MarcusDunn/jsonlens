package ca.marcusdunn.jsonlens.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.JsonDecimal;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Property;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/// Tests that a [JsonModel] obeys its contract, as JUnit tests.
///
/// A test class extends this class, and gives the model and a parser of its JSON library. The
/// parser must keep numbers exact. The tests are:
///
/// - fixed cases for each method, for example the seven kinds, exact numbers, and names that are
///   not normalized;
/// - [ModelVerifier] on a corpus of documents, and on random documents from [RandomJson];
/// - the JSONPath Compliance Test Suite, from [ComplianceKit#jsonPath(JsonModel, java.util.function.Function)].
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.TestkitSnippets region=contract}
///
/// [JsonFactoryContract] and [JsonEditorContract] add the tests for a model that builds and
/// changes values.
///
/// @param <N> the node type of the model
public abstract class JsonModelContract<N> {

    /// Makes the contract test. A subclass gives the model and a parser.
    protected JsonModelContract() {}

    /**
     * Returns the model under test.
     *
     * @return the model under test
     */
    protected abstract JsonModel<N> model();

    /**
     * Parses a JSON text with the JSON library of the model.
     *
     * @param json a JSON text
     * @return the root node
     */
    protected abstract N parse(String json);

    private static <T> T present(Maybe<T> maybe) {
        return switch (maybe) {
            case Maybe.Some<T>(T value) -> value;
            case Maybe.None<T>() -> fail("expected a present value");
        };
    }

    private JsonKind kindOf(String json) {
        return model().kind(parse(json));
    }

    private JsonDecimal numberOf(String json) {
        N node = parse(json);
        assertEquals(JsonKind.NUMBER, model().kind(node), json);
        return model().numberValue(node).exactValue();
    }

    private static JsonDecimal exact(String text) {
        return present(JsonDecimal.parse(text));
    }

    /**
     * Tells if the JSON library reads numbers with any exponent, for example {@code 1e99999999999}.
     * Then the comparison test also uses such numbers.
     *
     * @return {@code true} if the library reads numbers with any exponent; {@code false} by default
     */
    protected boolean readsAnyExponent() {
        return false;
    }

    /** The numbers of the model, and the numbers of the core factories, for the text of a number. */
    private List<JsonNumber> numbersOf(String text) {
        List<JsonNumber> numbers = new ArrayList<>();
        numbers.add(model().numberValue(parse(text)));
        numbers.add(JsonNumber.of(exact(text)));
        Maybe<BigDecimal> held = exact(text).toBigDecimal();
        if (!(held instanceof Maybe.Some<BigDecimal>(BigDecimal decimal))) {
            return numbers;
        }
        if (decimal.stripTrailingZeros().scale() <= 0 && decimal.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0) {
            numbers.add(JsonNumber.of(decimal.longValue()));
        }
        double binary = Double.parseDouble(text);
        if (Double.isFinite(binary) && BigDecimal.valueOf(binary).compareTo(decimal) == 0) {
            numbers.add(present(JsonNumber.of(binary)));
        }
        return numbers;
    }

    private String stringOf(String json) {
        N node = parse(json);
        assertEquals(JsonKind.STRING, model().kind(node), json);
        JsonString string = model().stringValue(node);
        StringBuilder scalarValues = new StringBuilder();
        for (var values = string.scalarValues(); values.hasNext(); ) {
            scalarValues.appendCodePoint(values.nextInt());
        }
        assertEquals(scalarValues.toString(), JsonString.copyOf(string), json);
        assertEquals(scalarValues.codePointCount(0, scalarValues.length()), JsonString.length(string), json);
        return scalarValues.toString();
    }

    @Test
    void classifiesTheSevenKinds() {
        assertEquals(JsonKind.OBJECT, kindOf("{}"));
        assertEquals(JsonKind.ARRAY, kindOf("[]"));
        assertEquals(JsonKind.STRING, kindOf("\"a\""));
        assertEquals(JsonKind.NUMBER, kindOf("1"));
        assertEquals(JsonKind.TRUE, kindOf("true"));
        assertEquals(JsonKind.FALSE, kindOf("false"));
        assertEquals(JsonKind.NULL, kindOf("null"));
    }

    @Test
    void stringsThatLookLikeOtherKindsAreStrings() {
        assertEquals(JsonKind.STRING, kindOf("\"true\""));
        assertEquals(JsonKind.STRING, kindOf("\"null\""));
        assertEquals(JsonKind.STRING, kindOf("\"1\""));
    }

    @Test
    void readsArrayElements() {
        N array = parse("[1, \"a\", null, [], {}]");
        assertEquals(5, model().arrayLength(array));
        assertEquals(JsonKind.NUMBER, model().kind(present(model().element(array, 0))));
        assertEquals(JsonKind.STRING, model().kind(present(model().element(array, 1))));
        assertEquals(JsonKind.NULL, model().kind(present(model().element(array, 2))));
        assertEquals(JsonKind.ARRAY, model().kind(present(model().element(array, 3))));
        assertEquals(JsonKind.OBJECT, model().kind(present(model().element(array, 4))));
    }

    @Test
    void indexOutsideTheArrayIsNone() {
        N array = parse("[1, 2]");
        assertTrue(model().element(array, 2).isNone());
        assertTrue(model().element(array, -1).isNone());
        assertTrue(model().element(array, Integer.MAX_VALUE).isNone());
        assertTrue(model().element(array, Integer.MIN_VALUE).isNone());
        assertTrue(model().element(parse("[]"), 0).isNone());
    }

    @Test
    void readsObjectMembers() {
        N object = parse("{\"a\": 1, \"b\": \"x\", \"n\": null}");
        assertEquals(3, model().memberCount(object));
        assertEquals(JsonKind.NUMBER, model().kind(present(model().member(object, JsonString.of("a")))));
        assertEquals(JsonKind.STRING, model().kind(present(model().member(object, JsonString.of("b")))));
        assertEquals(JsonKind.NULL, model().kind(present(model().member(object, JsonString.of("n")))));
        assertEquals(
                Set.of("a", "b", "n"),
                model().members(object).map(property -> JsonString.copyOf(property.name())).collect(Collectors.toSet()));
    }

    @Test
    void missingMemberIsNone() {
        N object = parse("{\"a\": 1}");
        assertTrue(model().member(object, JsonString.of("b")).isNone());
        assertTrue(model().member(object, JsonString.of("A")).isNone());
        assertTrue(model().member(object, JsonString.of("")).isNone());
        assertEquals(0, model().memberCount(parse("{}")));
        assertEquals(0, model().members(parse("{}")).count());
    }

    @Test
    void memberNamesAreNotNormalized() {
        // U+00E9 and "e" followed by U+0301 are canonically equivalent, but not equal.
        N object = parse("{\"é\": 1}");
        assertTrue(model().member(object, JsonString.of("é")).isSome());
        assertTrue(model().member(object, JsonString.of("é")).isNone());
    }

    @Test
    void memberOrderIsStable() {
        N object = parse("{\"c\": 1, \"a\": 2, \"b\": 3}");
        List<String> first = model().members(object).map(property -> JsonString.copyOf(property.name())).toList();
        List<String> second = model().members(object).map(property -> JsonString.copyOf(property.name())).toList();
        assertEquals(first, second);
    }

    @Test
    void givesEqualNodesForTheSameValue() {
        N object = parse("{\"a\": [1]}");
        N viaMember = present(model().member(object, JsonString.of("a")));
        assertEqualNodes(viaMember, present(model().member(object, JsonString.of("a"))));
        assertEqualNodes(viaMember, model().members(object).findFirst().map(Property::value).orElseThrow());
        MemberCursor<N> cursor = model().memberCursor(object);
        assertTrue(cursor.next());
        assertEqualNodes(viaMember, cursor.value());
        assertEqualNodes(present(model().element(viaMember, 0)), present(model().element(viaMember, 0)));
    }

    private static void assertEqualNodes(Object expected, Object actual) {
        assertEquals(expected, actual);
        assertEquals(expected.hashCode(), actual.hashCode());
    }

    @Test
    void readsStringValues() {
        assertEquals("", stringOf("\"\""));
        assertEquals("a\nb\"\\/", stringOf("\"a\\nb\\\"\\\\\\/\""));
        assertEquals("🁁", stringOf("\"\\uD83C\\uDC41\""));
        assertEquals("é中", stringOf("\"é中\""));
    }

    @Test
    void readsExactNumbers() {
        assertEquals(JsonDecimal.ZERO, numberOf("0"));
        assertEquals(JsonDecimal.ZERO, numberOf("-0"));
        assertEquals(exact("1"), numberOf("1.0"));
        assertEquals(exact("0.1"), numberOf("0.1"));
        assertEquals(exact("-0.0125"), numberOf("-12.5e-3"));
        assertEquals(exact("9007199254740993"), numberOf("9007199254740993"));
        assertEquals(exact("123456789012345678901234567890"), numberOf("123456789012345678901234567890"));
        assertEquals(exact("1e400"), numberOf("1e400"));
        assertEquals(exact("1E-400"), numberOf("1E-400"));
        if (readsAnyExponent()) {
            assertEquals(exact("1e99999999999"), numberOf("1e99999999999"));
        }
    }

    /**
     * Tests the invariant of {@link JsonModel#compareNumbers}: it has the same sign as the comparison
     * of the exact values, for the numbers of the model and the numbers of the core factories.
     */
    @Test
    void comparesNumbersAsTheirExactValues() {
        List<List<String>> ascending = new ArrayList<>(List.of(
                List.of("-1e400"),
                List.of("-9007199254740993"),
                List.of("-1.5", "-15e-1", "-1.50"),
                List.of("-1E-400"),
                List.of("0", "-0", "0.0", "0e5"),
                List.of("1E-400"),
                List.of("0.1"),
                List.of("1", "1.0", "10e-1"),
                List.of("1.5"),
                List.of("2", "2.0", "0.2e1"),
                List.of("9007199254740992"),
                List.of("9007199254740993"),
                List.of("9223372036854775807"),
                List.of("9223372036854775808"),
                List.of("123456789012345678901234567890", "1.2345678901234567890123456789e29"),
                List.of("1e400")));
        if (readsAnyExponent()) {
            ascending.add(0, List.of("-1e99999999999"));
            ascending.add(List.of("1e99999999999", "10e99999999998"));
        }
        List<List<JsonNumber>> groups = ascending.stream()
                .map(texts -> texts.stream().flatMap(text -> numbersOf(text).stream()).toList())
                .toList();
        for (int i = 0; i < groups.size(); i++) {
            for (int j = 0; j < groups.size(); j++) {
                int expected = Integer.compare(i, j);
                for (JsonNumber a : groups.get(i)) {
                    for (JsonNumber b : groups.get(j)) {
                        String pair = a.exactValue() + " <=> " + b.exactValue();
                        assertEquals(expected, a.exactValue().compareTo(b.exactValue()), pair);
                        assertEquals(expected, Integer.signum(model().compareNumbers(a, b)), pair);
                    }
                }
            }
        }
    }
    private boolean equal(String a, String b) {
        return model().equal(parse(a), parse(b));
    }

    @Test
    void equalValuesAreEqual() {
        for (String[] pair : new String[][] {
                {"null", "null"}, {"true", "true"}, {"false", "false"}, {"\"aé\"", "\"a\\u00e9\""},
                {"1", "1.0"}, {"-0", "0"}, {"1e2", "100"}, {"[]", "[]"}, {"{}", "{}"},
                {"[1, \"a\", [null]]", "[1.0, \"a\", [null]]"},
                {"{\"a\": 1, \"b\": [true, {}]}", "{\"b\": [true, {}], \"a\": 1}"}}) {
            assertTrue(equal(pair[0], pair[1]), pair[0] + " == " + pair[1]);
            assertTrue(equal(pair[1], pair[0]), pair[1] + " == " + pair[0]);
        }
    }

    @Test
    void differentValuesAreNotEqual() {
        for (String[] pair : new String[][] {
                {"null", "false"}, {"true", "false"}, {"\"a\"", "\"b\""}, {"\"1\"", "1"}, {"1", "2"},
                {"[]", "{}"}, {"[1]", "[1, 1]"}, {"[1, 2]", "[2, 1]"}, {"[[1]]", "[[2]]"},
                {"{\"a\": 1}", "{\"a\": 1, \"b\": 1}"}, {"{\"a\": 1}", "{\"b\": 1}"},
                {"{\"a\": {\"b\": 1}}", "{\"a\": {\"b\": 2}}"}, {"\"é\"", "\"é\""}}) {
            assertFalse(equal(pair[0], pair[1]), pair[0] + " != " + pair[1]);
            assertFalse(equal(pair[1], pair[0]), pair[1] + " != " + pair[0]);
        }
    }

    @Test
    void uniqueNamesAreNotDuplicates() {
        N object = parse("{\"a\": 1, \"b\": 2}");
        assertFalse(model().hasDuplicate(object, JsonString.of("a")));
        assertFalse(model().hasDuplicate(object, JsonString.of("c")));
    }
    @Test
    void memberCursorWalksTheMembersInOrder() {
        for (String json : List.of("{}", "{\"a\": 1}", "{\"c\": [1], \"a\": {\"x\": null}, \"b\": \"s\", \"é\": true}")) {
            N object = parse(json);
            List<Property<N>> members = model().members(object).toList();
            MemberCursor<N> cursor = model().memberCursor(object);
            for (Property<N> member : members) {
                assertTrue(cursor.next(), json);
                assertTrue(JsonString.equal(member.name(), cursor.name()), json);
                assertEquals(member.value(), cursor.value(), json);
            }
            assertFalse(cursor.next(), json);
            assertFalse(cursor.next(), json);
        }
    }

    /// Documents with the cases that models often get wrong. The random documents add more.
    private static final List<String> CORPUS = List.of(
            "{}",
            "[]",
            "{\"a\": 1, \"b\": [1, 1.0, 10e-1, 2], \"c\": {\"a\": {}}}",
            "[{\"x\": 1}, {\"x\": 1.0}, {\"x\": 2}, [1], [1.0], [2]]",
            "{\"\": \"\", \"é\": \"\\u00e9\", \"😀\": \"\\ud83d\\ude00\", \"a\\u0000b\": \"\\n\"}",
            "[1e400, 1e401, -1E-400, 123456789012345678901234567890, 9007199254740992, 9007199254740993, -0, 0, 0.5]",
            "[true, false, null, \"true\", \"null\", \"1\"]");

    /// Returns the number of random documents that the verifier test checks.
    ///
    /// @return the number of random documents; 100 by default
    protected int randomDocuments() {
        return 100;
    }

    /// Tells if the parser and the model keep duplicate member names. Then the tests also use
    /// documents with duplicate names.
    ///
    /// @return `true` if the model keeps each member of a duplicate name; `false` by default
    protected boolean keepsDuplicateNames() {
        return false;
    }

    /// A document with duplicate names, for a model that keeps them.
    private static final String DUPLICATES = "{\"a\": 1, \"b\": [2], \"a\": 3, \"\": {\"x\": 1, \"x\": 2, \"x\": 3}}";

    @Test
    void duplicateNamesGiveTheFirstMember() {
        Assumptions.assumeTrue(keepsDuplicateNames(), "the model does not keep duplicate names");
        N object = parse(DUPLICATES);
        assertEquals(4, model().memberCount(object));
        assertEquals(JsonDecimal.of(1), model().numberValue(present(model().member(object, JsonString.of("a")))).exactValue());
        assertTrue(model().hasDuplicate(object, JsonString.of("a")));
        assertFalse(model().hasDuplicate(object, JsonString.of("b")));
    }

    /// Runs [ModelVerifier] on the corpus and on random documents from [RandomJson].
    @Test
    void verifierFindsNoViolations() {
        List<String> documents = new ArrayList<>(CORPUS);
        if (keepsDuplicateNames()) {
            documents.add(DUPLICATES);
        }
        RandomJson random = RandomJson.withSeed(9535);
        for (int i = 0; i < randomDocuments(); i++) {
            documents.add(random.next());
        }
        ModelVerifier<N> verifier = ModelVerifier.of(model());
        List<String> failures = new ArrayList<>();
        for (String document : documents) {
            for (Violation violation : verifier.verify(parse(document))) {
                failures.add(document + "\n    " + violation);
            }
        }
        assertEquals(List.of(), failures);
    }

    /// Runs the JSONPath Compliance Test Suite on the model.
    ///
    /// @return a test for each valid query of the suite
    @TestFactory
    Stream<DynamicTest> jsonPathComplianceSuite() {
        return ComplianceKit.jsonPath(model(), this::parse);
    }
}
