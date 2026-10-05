package ca.marcusdunn.jsonlens.fuzz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.kotlinx.KotlinxJsonModel;
import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.mapped.MappedJsonError;
import ca.marcusdunn.jsonlens.mapped.MappedNode;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.patch.JsonPatch;
import ca.marcusdunn.jsonlens.patch.Operation;
import ca.marcusdunn.jsonlens.patch.PatchError;
import ca.marcusdunn.jsonlens.pointer.JsonPointer;
import ca.marcusdunn.jsonlens.pointer.PointerError;
import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import com.code_intelligence.jazzer.junit.DictionaryEntries;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonNull;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Applies a JSON Patch to a JSON document. The data is the document, a NUL byte, and the patch.
 *
 * <p>The properties:
 *
 * <ul>
 *   <li>Neither the patch nor the pointer module throws.
 *   <li>Jackson (strict duplicate detection) and the memory-mapped model read the same patch.
 *   <li>The Jackson and Java collections targets give the same result or the same error.
 *   <li>applyToCopy on Jackson and kotlinx gives the same result or error, and never changes its document.
 *   <li>After an error, the target has its original value (RFC 6902, Section 5).
 *   <li>The text and the URI fragment of each pointer give the same pointer again.
 * </ul>
 */
class PatchFuzzTest {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
    private static final JacksonJsonModel JACKSON = JacksonJsonModel.INSTANCE;
    private static final JavaCollectionsModel COLLECTIONS = JavaCollectionsModel.INSTANCE;
    private static final KotlinxJsonModel KOTLINX = KotlinxJsonModel.INSTANCE;

    @FuzzTest(maxDuration = "5m")
    @DictionaryEntries({
        "{\"op\":\"add\",\"path\":", "{\"op\":\"remove\",\"path\":", "{\"op\":\"replace\",\"path\":",
        "{\"op\":\"move\",\"from\":", "{\"op\":\"copy\",\"from\":", "{\"op\":\"test\",\"path\":",
        ",\"value\":", ",\"path\":", ",\"from\":", "\"/", "\"\"", "/-", "/0", "~0", "~1", "[", "]", "{", "}",
        "null", "1.0", "\"a\""
    })
    void patch(byte[] data) {
        String[] parts = Inputs.split(data, 1_000, 2_000);
        if (parts == null) {
            return;
        }
        JsonNode document;
        JsonNode patchDocument;
        try {
            document = MAPPER.readTree(parts[0]);
            patchDocument = MAPPER.readTree(parts[1]);
        } catch (RuntimeException e) {
            // Not JSON for Jackson, for example a duplicate name or a number that BigDecimal cannot hold.
            return;
        }
        if (document.isMissingNode() || patchDocument.isMissingNode()) {
            // Jackson reads an empty text as a missing node. It is not a JSON text.
            return;
        }
        Result<JsonPatch<JsonNode>, PatchError> patch = JsonPatch.parse(patchDocument, JACKSON);
        MappedJson mapped = switch (MappedJson.of(ByteBuffer.wrap(parts[1].getBytes(StandardCharsets.UTF_8)))) {
            case Result.Ok<MappedJson, MappedJsonError>(MappedJson value) -> value;
            case Result.Err<MappedJson, MappedJsonError>(MappedJsonError error) ->
                    throw new AssertionError("Jackson read the patch, but the mapped model did not: " + error.message());
        };
        Result<JsonPatch<MappedNode>, PatchError> mappedPatch = JsonPatch.parse(mapped.root(), mapped.model());
        assertEquals(patch.isOk(), mappedPatch.isOk(), "Jackson and the mapped model read " + parts[1] + " differently");
        if (!(patch instanceof Result.Ok<JsonPatch<JsonNode>, PatchError>(JsonPatch<JsonNode> valid))
                || !(mappedPatch instanceof Result.Ok<JsonPatch<MappedNode>, PatchError>(JsonPatch<MappedNode> mappedValid))) {
            assertEquals(patch.fold(p -> "", PatchError::toString), mappedPatch.fold(p -> "", PatchError::toString), parts[1]);
            return;
        }
        for (Operation<JsonNode> operation : valid.operations()) {
            checkPointer(operation.path());
        }
        applyToBothTargets(valid, document, parts);
        applyToBothTargets(mappedValid, document, parts);
    }

    private static <P> void applyToBothTargets(JsonPatch<P> patch, JsonNode document, String[] parts) {
        JsonNode jackson = document.deepCopy();
        Object collections = COLLECTIONS.copyOf(JACKSON, document).orElse("none");
        JsonElement kotlinx = KOTLINX.copyOf(JACKSON, document).orElse(JsonNull.INSTANCE);
        Result<JsonNode, PatchError> onJackson = patch.apply(jackson, JACKSON);
        Result<Object, PatchError> onCollections = patch.apply(collections, COLLECTIONS);
        // applyToCopy gives the same result or error, and never changes its document.
        Result<JsonNode, PatchError> jacksonCopy = patch.applyToCopy(document, JACKSON);
        Result<JsonElement, PatchError> kotlinxCopy = patch.applyToCopy(kotlinx, KOTLINX);
        String input = parts[1] + " on " + parts[0];
        assertTrue(JsonModel.equal(JACKSON, document, KOTLINX, kotlinx), "applyToCopy changed a document: " + input);
        switch (onJackson) {
            case Result.Ok<JsonNode, PatchError>(JsonNode root) -> {
                assertTrue(onCollections.isOk(), "only the collections target failed: " + input);
                assertTrue(JsonModel.equal(JACKSON, root, COLLECTIONS, onCollections.orElse("none")),
                        "the targets differ: " + input);
                assertTrue(JACKSON.equal(root, jacksonCopy.orElse(JACKSON.nullValue())) && jacksonCopy.isOk(),
                        "apply and applyToCopy differ: " + input);
                assertTrue(JsonModel.equal(JACKSON, root, KOTLINX, kotlinxCopy.orElse(JsonNull.INSTANCE)) && kotlinxCopy.isOk(),
                        "the kotlinx copy differs: " + input);
            }
            case Result.Err<JsonNode, PatchError>(PatchError error) -> {
                assertEquals(Result.err(error), onCollections, "the targets fail differently: " + input);
                assertEquals(Result.err(error), jacksonCopy, "applyToCopy fails differently: " + input);
                assertEquals(Result.err(error), kotlinxCopy, "the kotlinx copy fails differently: " + input);
                assertTrue(JACKSON.equal(document, jackson), "the failed patch changed the Jackson target: " + input);
                assertTrue(JsonModel.equal(JACKSON, document, COLLECTIONS, collections),
                        "the failed patch changed the collections target: " + input);
            }
        }
    }

    /** The text and the URI fragment of a pointer give the same pointer again. */
    private static void checkPointer(JsonPointer pointer) {
        assertEquals(Result.<JsonPointer, PointerError>ok(pointer), JsonPointer.parse(pointer.toString()), pointer.toString());
        boolean scalarValues = pointer.tokens().stream().allMatch(token ->
                new String(token.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8).equals(token));
        // Only a pointer of Unicode scalar values has a fragment form.
        assertEquals(scalarValues, pointer.toFragment().isSome(), pointer.toString());
        if (pointer.toFragment() instanceof Maybe.Some<String>(String fragment)) {
            assertEquals(Result.<JsonPointer, PointerError>ok(pointer), JsonPointer.parseFragment(fragment), fragment);
        }
    }
}
