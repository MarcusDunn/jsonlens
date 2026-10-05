package ca.marcusdunn.jsonlens.patch;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.kotlinx.KotlinxJsonModel;
import ca.marcusdunn.jsonlens.kotlinx.KotlinxJsonModel;
import ca.marcusdunn.jsonlens.model.JsonEditor;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;

/// Runs the JSON Patch test suite (spec/json-patch-tests): each record with a document and a
/// patch, on each target model, with the patch document in each reader model.
@Requirement("lib/patch-test-suite")
class ConformanceSuiteTest {

    /// A record of the suite, as Jackson nodes, and the raw text of its patch.
    private record Case(String file, int number, String comment, JsonNode doc, String patch, JsonNode expected, boolean error) {
        @Override
        public String toString() {
            return file + " #" + number + " " + comment;
        }
    }

    private static String text(String resource) {
        try (InputStream in = Objects.requireNonNull(ConformanceSuiteTest.class.getResourceAsStream("/" + resource))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// The records of a file. The raw text of each patch comes from the raw file, so that a reader
    /// that keeps duplicate member names sees them.
    private static List<Case> cases(String file) {
        String raw = text(file);
        JsonNode records = Documents.jackson(raw);
        List<String> patches = Documents.rawPatches(raw);
        List<Case> cases = new ArrayList<>();
        int patch = 0;
        for (int i = 0; i < records.size(); i++) {
            JsonNode record = records.get(i);
            if (!record.has("patch")) {
                continue;
            }
            String patchText = patches.get(patch++);
            if (record.path("disabled").asBoolean(false) || !record.has("doc")) {
                continue;
            }
            cases.add(new Case(file, i, record.path("comment").asString(""), record.get("doc"), patchText,
                    record.path("expected"), record.has("error")));
        }
        return cases;
    }

    @TestFactory
    Stream<DynamicTest> suite() {
        List<Case> cases = new ArrayList<>(cases("tests.json"));
        cases.addAll(cases("spec_tests.json"));
        return cases.stream().flatMap(c -> Stream.of(
                DynamicTest.dynamicTest(c + " [jackson in place]", () -> runInPlace(c, JacksonJsonModel.INSTANCE, c.doc().deepCopy())),
                DynamicTest.dynamicTest(c + " [collections in place]", () -> runInPlace(c, JavaCollectionsModel.INSTANCE,
                        JavaCollectionsModel.INSTANCE.copyOf(JacksonJsonModel.INSTANCE, c.doc()).orElse("none"))),
                DynamicTest.dynamicTest(c + " [jackson copy]", () -> runCopy(c, JacksonJsonModel.INSTANCE, c.doc().deepCopy())),
                DynamicTest.dynamicTest(c + " [collections copy]", () -> runCopy(c, JavaCollectionsModel.INSTANCE,
                        JavaCollectionsModel.INSTANCE.copyOf(JacksonJsonModel.INSTANCE, c.doc()).orElse("none"))),
                DynamicTest.dynamicTest(c + " [kotlinx copy]", () -> runCopy(c, KotlinxJsonModel.INSTANCE,
                        KotlinxJsonModel.INSTANCE.copyOf(JacksonJsonModel.INSTANCE, c.doc()).orElseGet(() -> fail("copy"))))));
    }

    private static <N, M extends JsonModel<N> & JsonFactory<N> & JsonEditor<N>> void runInPlace(Case c, M model, N document) {
        for (Documents.Reader reader : Documents.Reader.values()) {
            // Each run starts from a fresh copy of the document.
            N target = model.copyOf(model, document).orElseGet(() -> fail("copy"));
            check(c, reader, model, target, reader.read(c.patch()).flatMap(p -> p.apply(target, model)));
        }
    }

    private static <N, M extends JsonModel<N> & JsonFactory<N>> void runCopy(Case c, M model, N document) {
        for (Documents.Reader reader : Documents.Reader.values()) {
            check(c, reader, model, document, reader.read(c.patch()).flatMap(p -> p.applyToCopy(document, model)));
            // The document never changes.
            assertTrue(JsonModel.equal(model, document, JacksonJsonModel.INSTANCE, c.doc()),
                    c + " [" + reader + "]: applyToCopy changed the document");
        }
    }

    private static <N> void check(Case c, Documents.Reader reader, JsonModel<N> model, N target, Result<N, PatchError> result) {
        if (c.error()) {
            assertTrue(result.isErr(), c + " [" + reader + "]: expected an error");
            // The patch is atomic: the document has its original value.
            assertTrue(JsonModel.equal(model, target, JacksonJsonModel.INSTANCE, c.doc()),
                    c + " [" + reader + "]: the failed patch changed the document");
        } else {
            N changed = result.fold(root -> root, error -> fail(c + " [" + reader + "]: " + error.message()));
            assertTrue(JsonModel.equal(model, changed, JacksonJsonModel.INSTANCE, c.expected().isMissingNode() ? c.doc() : c.expected()),
                    c + " [" + reader + "]: wrong result");
        }
    }
}
