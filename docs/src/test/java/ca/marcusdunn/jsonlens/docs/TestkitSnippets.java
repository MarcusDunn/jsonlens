package ca.marcusdunn.jsonlens.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.docs.CustomModelSnippets.CollectionsModel;
import ca.marcusdunn.jsonlens.docs.CustomModelSnippets.JsonNull;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.testkit.ComplianceKit;
import ca.marcusdunn.jsonlens.testkit.JsonModelContract;
import ca.marcusdunn.jsonlens.testkit.ModelVerifier;
import ca.marcusdunn.jsonlens.testkit.RandomJson;
import ca.marcusdunn.jsonlens.testkit.Violation;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** The test kit on the model of {@link CustomModelSnippets}. */
class TestkitSnippets {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .build();

    /** Reads a JSON text into Map, List, String, number, and Boolean values. */
    static Object parse(String json) {
        Object value = MAPPER.readValue(json, Object.class);
        return value == null ? JsonNull.NULL : value;
    }

    // @start region="contract"
    // The contract test of a model: give the model and a parser of its JSON library.
    // The test runs fixed cases, the verifier on random documents, and the RFC 9535 test suite.
    static class CollectionsModelContractTest extends JsonModelContract<Object> {

        @Override
        protected JsonModel<Object> model() {
            return new CollectionsModel();
        }

        @Override
        protected Object parse(String json) {
            return TestkitSnippets.parse(json); // The parser must keep numbers exact.
        }
    }
    // @end region="contract"

    @Test
    void verifiesADocument() {
        // @start region="verify"
        JsonModel<Object> model = new CollectionsModel();
        List<Violation> violations = ModelVerifier.of(model).verify(Map.of("a", List.of(1, "x")));
        // Each violation names a rule, the location of a value, and what the model did.
        // @end region="verify"
        assertEquals(List.of(), violations);
    }

    @Test
    void verifiesRandomDocuments() {
        ModelVerifier<Object> verifier = ModelVerifier.of(new CollectionsModel());
        // @start region="random"
        RandomJson random = RandomJson.withSeed(42); // The same seed gives the same documents.
        for (int i = 0; i < 100; i++) {
            Object document = parse(random.next());
            assertEquals(List.of(), verifier.verify(document));
        }
        // @end region="random"
    }

    // @start region="suites"
    // The suites as JUnit dynamic tests, for example in a test class that is not a contract test.
    @TestFactory
    Stream<DynamicTest> jsonPathSuite() {
        return ComplianceKit.jsonPath(new CollectionsModel(), TestkitSnippets::parse);
    }
    // @end region="suites"
}
