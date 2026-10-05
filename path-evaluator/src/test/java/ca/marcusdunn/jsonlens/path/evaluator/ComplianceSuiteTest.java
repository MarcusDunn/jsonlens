package ca.marcusdunn.jsonlens.path.evaluator;

import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.jackson.JacksonStream;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;

/** The evaluator part of the JSONPath Compliance Test Suite (spec/cts), with the Jackson, kotlinx, memory-mapped, and streaming models. */
@Requirement("lib/cts-evaluator")
@Requirement("lib/model-no-null-arguments")
@Requirement("lib/model-kind-preconditions")
class ComplianceSuiteTest {

    /** One permitted result: values and Normalized Paths. */
    private record Outcome(List<JsonNode> values, List<String> paths) {}

    @TestFactory
    Stream<DynamicTest> complianceSuite() {
        JsonNode suite;
        try (InputStream in = ComplianceSuiteTest.class.getResourceAsStream("/cts.json")) {
            if (in == null) {
                return fail("cts.json is not on the test class path");
            }
            suite = Eval.MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return StreamSupport.stream(suite.get("tests").spliterator(), false)
                .filter(test -> !test.path("invalid_selector").asBoolean(false))
                .map(test -> DynamicTest.dynamicTest(test.get("name").asString(), () -> check(test)));
    }

    private static void check(JsonNode test) {
        String selector = test.get("selector").asString();
        JsonPathQuery query = Eval.query(JsonPathParser.standard(), selector);
        String document = test.get("document").toString();
        List<Outcome> permitted = new ArrayList<>();
        if (test.has("result")) {
            permitted.add(outcome(test.get("result"), test.get("result_paths")));
        } else {
            for (int i = 0; i < test.get("results").size(); i++) {
                permitted.add(outcome(test.get("results").get(i), test.get("results_paths").get(i)));
            }
        }

        List<Node<JsonNode>> jackson = Eval.nodes(Eval.STANDARD, query, Eval.jackson(document), Eval.JACKSON);
        Outcome fromJackson = new Outcome(jackson.stream().map(Node::value).toList(), Eval.paths(jackson));
        if (!permitted.contains(fromJackson)) {
            fail("Jackson: " + selector + " gave " + fromJackson + ", expected one of " + permitted);
        }

        var kotlinx = Eval.nodes(Eval.STANDARD, query, Eval.kotlinx(document), Eval.KOTLINX);
        Outcome fromKotlinx = new Outcome(
                kotlinx.stream().map(node -> Eval.jackson(node.value().toString())).toList(), Eval.paths(kotlinx));
        if (!permitted.contains(fromKotlinx)) {
            fail("kotlinx: " + selector + " gave " + fromKotlinx + ", expected one of " + permitted);
        }

        // The read-only model over UTF-8 bytes. It writes no JSON text, so compare the paths and read the values from Jackson.
        var mapped = Eval.nodes(Eval.STANDARD, query, Eval.mapped(document).root(), Eval.MAPPED);
        List<String> mappedPaths = Eval.paths(mapped);
        if (permitted.stream().noneMatch(outcome -> outcome.paths().equals(mappedPaths))) {
            fail("mapped: " + selector + " gave " + mappedPaths + ", expected one of " + permitted);
        }

        // The read-only model over a Jackson streaming parser: the paths, and the values compared across models.
        JacksonStream stream = Eval.stream(document);
        var streamed = Eval.nodes(Eval.STANDARD, query, stream.root(), Eval.STREAM);
        List<String> streamedPaths = Eval.paths(streamed);
        Outcome match = permitted.stream().filter(outcome -> outcome.paths().equals(streamedPaths)).findFirst()
                .orElseGet(() -> fail("stream: " + selector + " gave " + streamedPaths + ", expected one of " + permitted));
        for (int i = 0; i < streamed.size(); i++) {
            if (!JsonModel.equal(Eval.STREAM, streamed.get(i).value(), JacksonJsonModel.INSTANCE, match.values().get(i))) {
                fail("stream: " + selector + " gave a wrong value at " + streamedPaths.get(i));
            }
        }
        if (stream.failure().isSome()) {
            fail("stream: " + selector + " failed: " + stream.failure());
        }
    }

    private static Outcome outcome(JsonNode values, JsonNode paths) {
        List<JsonNode> valueList = new ArrayList<>();
        values.forEach(valueList::add);
        List<String> pathList = new ArrayList<>();
        paths.forEach(path -> pathList.add(path.asString()));
        return new Outcome(valueList, pathList);
    }
}
