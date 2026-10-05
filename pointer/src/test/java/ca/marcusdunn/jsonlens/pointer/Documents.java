package ca.marcusdunn.jsonlens.pointer;

import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.kotlinx.KotlinxJsonModel;
import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.mapped.MappedJsonError;
import ca.marcusdunn.jsonlens.mapped.MappedNode;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import kotlinx.serialization.json.Json;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** One JSON text in each model of the build. */
final class Documents {

    /** A document and its model. */
    record Document<N>(String name, N root, JsonModel<N> model) {
        Result<N, PointerError> resolve(JsonPointer pointer) {
            return pointer.resolve(root, model);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private Documents() {}

    static JsonNode jackson(String json) {
        return JsonMapper.builder().build().readTree(json);
    }

    static MappedJson mapped(String json) {
        return switch (MappedJson.of(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)))) {
            case Result.Ok<MappedJson, MappedJsonError>(MappedJson value) -> value;
            case Result.Err<MappedJson, MappedJsonError>(MappedJsonError error) -> fail(error.message());
        };
    }

    /** The JSON text in Jackson, kotlinx, mapped, and Java collections. */
    static List<Document<?>> all(String json) {
        MappedJson mapped = mapped(json);
        JsonNode jackson = jackson(json);
        return List.of(
                new Document<>("jackson", jackson, JacksonJsonModel.INSTANCE),
                new Document<>("kotlinx", Json.Default.parseToJsonElement(json), KotlinxJsonModel.INSTANCE),
                new Document<MappedNode>("mapped", mapped.root(), mapped.model()),
                new Document<>("collections",
                        JavaCollectionsModel.INSTANCE.copyOf(JacksonJsonModel.INSTANCE, jackson).orElse("none"),
                        JavaCollectionsModel.INSTANCE));
    }
}
