package ca.marcusdunn.jsonlens.path.parser;

import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The parser part of the JSONPath Compliance Test Suite (spec/cts). */
@Requirement("lib/cts-parser")
class ComplianceSuiteTest {

    @TestFactory
    Stream<DynamicTest> complianceSuite() {
        JsonNode suite;
        try (InputStream in = ComplianceSuiteTest.class.getResourceAsStream("/cts.json")) {
            if (in == null) {
                return fail("cts.json is not on the test class path");
            }
            suite = JsonMapper.builder().build().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return StreamSupport.stream(suite.get("tests").spliterator(), false).map(test -> {
            String name = test.get("name").asString();
            String selector = test.get("selector").asString();
            boolean invalid = test.path("invalid_selector").asBoolean(false);
            return DynamicTest.dynamicTest(name, () -> {
                switch (Queries.STANDARD.parse(selector)) {
                    case Result.Ok<JsonPathQuery, ParseError>(JsonPathQuery query) -> {
                        if (invalid) {
                            fail("expected an error for " + selector + ", but got " + query);
                        }
                    }
                    case Result.Err<JsonPathQuery, ParseError>(ParseError error) -> {
                        if (!invalid) {
                            fail("expected " + selector + " to parse, but: " + error.message());
                        }
                    }
                }
            });
        });
    }
}
