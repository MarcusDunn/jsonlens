package ca.marcusdunn.jsonlens.testkit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.evaluator.EvaluationError;
import ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator;
import ca.marcusdunn.jsonlens.path.evaluator.Node;
import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.mapped.MappedJsonError;
import ca.marcusdunn.jsonlens.mapped.MappedNode;
import ca.marcusdunn.jsonlens.model.JsonEditor;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import ca.marcusdunn.jsonlens.path.parser.ParseError;
import ca.marcusdunn.jsonlens.patch.JsonPatch;
import ca.marcusdunn.jsonlens.patch.PatchError;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

/// Runs the test suites of RFC 9535 and RFC 6902 on a model, as JUnit dynamic tests.
///
/// - [#jsonPath(JsonModel, Function)] runs each valid query of the JSONPath Compliance Test Suite,
///   and compares the values and the Normalized Paths of the results.
/// - [#jsonPatchInPlace(JsonModel, Function)] and [#jsonPatchToCopy(JsonModel, Function)] run each test of
///   the JSON Patch test suite (json-patch-tests): in place, and to a copy. A patch that must fail
///   must leave the document as it was.
///
/// The model reads each document from its JSON text, with the parser of its own library. The
/// suites are in the JAR, with their licenses: the JSONPath Compliance Test Suite (BSD-2), and the
/// JSON Patch tests (Apache-2.0).
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.TestkitSnippets region=suites}
public final class ComplianceKit {

    /// The model of the suite files. All [MappedJson] texts share one model.
    private static final JsonModel<MappedNode> SUITE = suite("json-patch-tests/spec_tests.json").model();

    private ComplianceKit() {}

    /// Returns a test for each valid query of the JSONPath Compliance Test Suite.
    ///
    /// @param model the model under test
    /// @param parser reads a JSON text into a node of the model
    /// @param <N> the node type of the model
    /// @return the tests
    public static <N> Stream<DynamicTest> jsonPath(JsonModel<N> model, Function<String, N> parser) {
        MappedNode tests = member(suite("cts/cts.json").root(), "tests");
        return elements(tests)
                .filter(test -> !(SUITE.member(test, JsonString.of("invalid_selector")) instanceof Maybe.Some<MappedNode>(MappedNode invalid)
                        && SUITE.kind(invalid) == JsonKind.TRUE))
                .map(test -> DynamicTest.dynamicTest(string(member(test, "name")), () -> jsonPathCase(test, model, parser)));
    }

    private static <N> void jsonPathCase(MappedNode test, JsonModel<N> model, Function<String, N> parser) {
        String selector = string(member(test, "selector"));
        JsonPathQuery query = switch (JsonPathParser.standard().parse(selector)) {
            case Result.Ok<JsonPathQuery, ParseError>(JsonPathQuery value) -> value;
            case Result.Err<JsonPathQuery, ParseError>(ParseError error) -> fail(selector + ": " + error.message());
        };
        N document = parser.apply(JsonText.write(SUITE, member(test, "document")));
        List<Node<N>> nodes = switch (JsonPathEvaluator.standard().evaluate(query, document, model)) {
            case Result.Ok<List<Node<N>>, EvaluationError>(List<Node<N>> value) -> value;
            case Result.Err<List<Node<N>>, EvaluationError>(EvaluationError error) -> fail(selector + ": " + error.message());
        };
        List<String> paths = nodes.stream().map(node -> node.path().toString()).toList();
        for (Outcome outcome : outcomes(test)) {
            if (outcome.matches(paths, nodes, model)) {
                return;
            }
        }
        fail(selector + " gave the paths " + paths + ", which is not a permitted result, or a value is wrong");
    }

    /// One permitted result: the values and their Normalized Paths.
    private record Outcome(List<MappedNode> values, List<String> paths) {
        <N> boolean matches(List<String> actualPaths, List<Node<N>> nodes, JsonModel<N> model) {
            if (!paths.equals(actualPaths)) {
                return false;
            }
            for (int i = 0; i < nodes.size(); i++) {
                if (!JsonModel.equal(model, nodes.get(i).value(), SUITE, values.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static List<Outcome> outcomes(MappedNode test) {
        if (SUITE.member(test, JsonString.of("result")) instanceof Maybe.Some<MappedNode>(MappedNode result)) {
            return List.of(outcome(result, member(test, "result_paths")));
        }
        MappedNode results = member(test, "results");
        MappedNode paths = member(test, "results_paths");
        return IntStream.range(0, SUITE.arrayLength(results))
                .mapToObj(i -> outcome(element(results, i), element(paths, i)))
                .toList();
    }

    private static Outcome outcome(MappedNode values, MappedNode paths) {
        return new Outcome(elements(values).toList(), elements(paths).map(ComplianceKit::string).toList());
    }

    /// Returns a test for each test of the JSON Patch test suite, in place.
    ///
    /// @param model the model under test
    /// @param parser reads a JSON text into a node of the model
    /// @param <N> the node type of the model
    /// @param <M> the type of the model
    /// @return the tests
    public static <N, M extends JsonModel<N> & JsonFactory<N> & JsonEditor<N>> Stream<DynamicTest> jsonPatchInPlace(
            M model, Function<String, N> parser) {
        return patchCases().stream().map(c -> DynamicTest.dynamicTest(c + " [in place]", () -> c.inPlace(model, parser)));
    }

    /// Returns a test for each test of the JSON Patch test suite, to a copy. Use it for a model
    /// that builds values but does not change them.
    ///
    /// @param model the model under test
    /// @param parser reads a JSON text into a node of the model
    /// @param <N> the node type of the model
    /// @param <M> the type of the model
    /// @return the tests
    public static <N, M extends JsonModel<N> & JsonFactory<N>> Stream<DynamicTest> jsonPatchToCopy(
            M model, Function<String, N> parser) {
        return patchCases().stream().map(c -> DynamicTest.dynamicTest(c + " [to a copy]", () -> c.toCopy(model, parser)));
    }

    /// A test of the JSON Patch test suite.
    private record PatchCase(String file, int number, String comment, MappedNode doc, MappedNode patch,
            Maybe<MappedNode> expected, boolean error) {

        @Override
        public String toString() {
            return file + " #" + number + " " + comment;
        }

        <N, M extends JsonModel<N> & JsonFactory<N> & JsonEditor<N>> void inPlace(M model, Function<String, N> parser) {
            N target = parser.apply(JsonText.write(SUITE, doc));
            check(model, target, JsonPatch.parse(patch, SUITE).flatMap(p -> p.apply(target, model)));
        }

        <N, M extends JsonModel<N> & JsonFactory<N>> void toCopy(M model, Function<String, N> parser) {
            N document = parser.apply(JsonText.write(SUITE, doc));
            check(model, document, JsonPatch.parse(patch, SUITE).flatMap(p -> p.applyToCopy(document, model)));
            assertTrue(JsonModel.equal(model, document, SUITE, doc), this + ": applyToCopy changed the document");
        }

        private <N> void check(JsonModel<N> model, N target, Result<N, PatchError> result) {
            switch (result) {
                case Result.Ok<N, PatchError>(N changed) -> {
                    assertTrue(!error, this + ": the patch must fail");
                    assertTrue(JsonModel.equal(model, changed, SUITE, expected.orElse(doc)), this + ": wrong result");
                }
                case Result.Err<N, PatchError>(PatchError failure) -> {
                    assertTrue(error, () -> this + ": " + failure.message());
                    // A patch is atomic: after a failure, the document has its original value.
                    assertTrue(JsonModel.equal(model, target, SUITE, doc), this + ": the failed patch changed the document");
                }
            }
        }
    }

    private static List<PatchCase> patchCases() {
        List<PatchCase> cases = new ArrayList<>();
        for (String file : List.of("tests.json", "spec_tests.json")) {
            MappedNode records = suite("json-patch-tests/" + file).root();
            for (int i = 0; i < SUITE.arrayLength(records); i++) {
                MappedNode record = element(records, i);
                if (SUITE.member(record, JsonString.of("patch")) instanceof Maybe.Some<MappedNode>(MappedNode patch)
                        && SUITE.member(record, JsonString.of("doc")) instanceof Maybe.Some<MappedNode>(MappedNode doc)
                        && SUITE.member(record, JsonString.of("disabled")).isNone()) {
                    String comment = SUITE.member(record, JsonString.of("comment")).map(ComplianceKit::string).orElse("");
                    cases.add(new PatchCase(file, i, comment, doc, patch, SUITE.member(record, JsonString.of("expected")),
                            SUITE.member(record, JsonString.of("error")).isSome()));
                }
            }
        }
        return cases;
    }

    private static MappedJson suite(String resource) {
        try (InputStream in = Objects.requireNonNull(ComplianceKit.class.getResourceAsStream(resource), resource)) {
            return switch (MappedJson.of(ByteBuffer.wrap(in.readAllBytes()))) {
                case Result.Ok<MappedJson, MappedJsonError>(MappedJson json) -> json;
                case Result.Err<MappedJson, MappedJsonError>(MappedJsonError error) -> fail(resource + ": " + error);
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static MappedNode member(MappedNode object, String name) {
        return SUITE.member(object, JsonString.of(name)).orElseGet(() -> fail("the suite has no member " + name));
    }

    private static MappedNode element(MappedNode array, int index) {
        return SUITE.element(array, index).orElseGet(() -> fail("the suite has no element " + index));
    }

    private static Stream<MappedNode> elements(MappedNode array) {
        return IntStream.range(0, SUITE.arrayLength(array)).mapToObj(i -> element(array, i));
    }

    private static String string(MappedNode string) {
        return JsonString.copyOf(SUITE.stringValue(string));
    }
}
