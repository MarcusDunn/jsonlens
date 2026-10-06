package ca.marcusdunn.jsonlens.mergepatch;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.kotlinx.KotlinxJsonModel;
import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.mapped.MappedJsonError;
import ca.marcusdunn.jsonlens.model.JsonEditor;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElement;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// JSON texts in the models of the build, and checks of merge results.
final class Documents {

    /// Reads exact numbers, and rejects duplicate names.
    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private Documents() {}

    static JsonNode jackson(String json) {
        return STRICT.readTree(json);
    }

    static Object collections(String json) {
        return JavaCollectionsModel.INSTANCE.copyOf(JacksonJsonModel.INSTANCE, jackson(json)).orElseGet(() -> fail(json));
    }

    static MappedJson mapped(String json) {
        return switch (MappedJson.of(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)))) {
            case Result.Ok<MappedJson, MappedJsonError>(MappedJson value) -> value;
            case Result.Err<MappedJson, MappedJsonError>(MappedJsonError error) -> fail(error.message());
        };
    }

    static JsonElement kotlinx(String json) {
        return Json.Default.parseToJsonElement(json);
    }

    static <P> JsonMergePatch<P> valid(Result<JsonMergePatch<P>, MergePatchError> result) {
        return result.fold(patch -> patch, error -> fail(error.message()));
    }

    static <N> N success(Result<N, MergePatchError> result) {
        return result.fold(root -> root, error -> fail(error.message()));
    }

    /// The models that read a patch document.
    enum Reader {
        /// Jackson with strict duplicate detection.
        JACKSON {
            @Override
            JsonMergePatch<?> read(String patch) {
                return valid(JsonMergePatch.parse(jackson(patch), JacksonJsonModel.INSTANCE));
            }
        },
        /// The memory-mapped model: a read-only patch document in another model than the target.
        MAPPED {
            @Override
            JsonMergePatch<?> read(String patch) {
                MappedJson json = mapped(patch);
                return valid(JsonMergePatch.parse(json.root(), json.model()));
            }
        },
        /// kotlinx.serialization: an immutable patch document.
        KOTLINX {
            @Override
            JsonMergePatch<?> read(String patch) {
                return valid(JsonMergePatch.parse(kotlinx(patch), KotlinxJsonModel.INSTANCE));
            }
        };

        abstract JsonMergePatch<?> read(String patch);
    }

    /// Tells if a value of a model has the value of a JSON text.
    static <N> void assertJson(String expected, JsonModel<N> model, N actual) {
        assertTrue(JsonModel.equal(model, actual, JacksonJsonModel.INSTANCE, jackson(expected)),
                () -> "expected " + expected + " but was " + actual);
    }

    /// Merges a patch, read with each reader, into a Jackson tree and into Java collections, in
    /// place and to a copy. Checks the results, and checks that the copy leaves the target as it
    /// was.
    static void assertMerged(String target, String patch, String expected) {
        for (Reader reader : Reader.values()) {
            JsonMergePatch<?> merge = reader.read(patch);
            assertMerged(merge, JacksonJsonModel.INSTANCE, Documents::jackson, target, expected);
            assertMerged(merge, JavaCollectionsModel.INSTANCE, Documents::collections, target, expected);
        }
    }

    private static <N, M extends JsonModel<N> & JsonFactory<N> & JsonEditor<N>> void assertMerged(
            JsonMergePatch<?> patch, M model, Function<String, N> read, String target, String expected) {
        N original = read.apply(target);
        assertJson(expected, model, success(patch.applyToCopy(original, model)));
        assertJson(target, model, original);
        assertJson(expected, model, success(patch.apply(read.apply(target), model)));
    }
}
