package ca.marcusdunn.jsonlens.patch;

import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.kotlinx.KotlinxJsonModel;
import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.mapped.MappedJsonError;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.jackson.JacksonStream;
import ca.marcusdunn.jsonlens.jackson.StreamError;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElement;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** JSON texts in the models of the build. */
final class Documents {

    /// Reads exact numbers. Duplicate names: the last value wins.
    private static final JsonMapper LENIENT = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    /// Reads exact numbers, and rejects duplicate names.
    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private Documents() {}

    static JsonNode jackson(String json) {
        return LENIENT.readTree(json);
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

    /// The models that read a patch document. A reader that cannot read the text, for example
    /// because of a duplicate name, rejects the patch: the result is an error.
    enum Reader {
        /// Jackson with strict duplicate detection.
        JACKSON {
            @Override
            Result<JsonPatch<?>, PatchError> read(String patch) {
                JsonNode document;
                try {
                    document = STRICT.readTree(patch);
                } catch (RuntimeException e) {
                    return Result.err(new PatchError.NotAnArray());
                }
                return JsonPatch.parse(document, JacksonJsonModel.INSTANCE).map(p -> p);
            }
        },
        /// kotlinx.serialization: an immutable, read-only patch document.
        KOTLINX {
            @Override
            Result<JsonPatch<?>, PatchError> read(String patch) {
                JsonElement document;
                try {
                    document = kotlinx(patch);
                } catch (RuntimeException e) {
                    return Result.err(new PatchError.NotAnArray());
                }
                return JsonPatch.parse(document, KotlinxJsonModel.INSTANCE).map(p -> p);
            }
        },
        /// The memory-mapped model: it keeps duplicate names, and the patch finds them.
        MAPPED {
            @Override
            Result<JsonPatch<?>, PatchError> read(String patch) {
                MappedJson json = mapped(patch);
                return JsonPatch.parse(json.root(), json.model()).map(p -> p);
            }
        },
        /// A Jackson streaming parser, read on demand: it also keeps duplicate names.
        STREAM {
            @Override
            Result<JsonPatch<?>, PatchError> read(String patch) {
                return switch (JacksonStream.readFully(LENIENT.createParser(patch))) {
                    case Result.Ok<JacksonStream, StreamError>(JacksonStream stream) ->
                            JsonPatch.parse(stream.root(), stream.model()).map(p -> p);
                    case Result.Err<JacksonStream, StreamError>(StreamError error) -> Result.err(new PatchError.NotAnArray());
                };
            }
        };

        /// Reads a patch. A reader that cannot read the text rejects the patch.
        abstract Result<JsonPatch<?>, PatchError> read(String patch);
    }

    /// The raw text of each value of a "patch" member, in document order. The values are arrays.
    static List<String> rawPatches(String text) {
        List<String> patches = new ArrayList<>();
        int at = text.indexOf("\"patch\"");
        while (at >= 0) {
            int start = text.indexOf('[', at);
            int depth = 0;
            boolean string = false;
            int end = start;
            for (; end < text.length(); end++) {
                char c = text.charAt(end);
                if (string) {
                    if (c == '\\') {
                        end++;
                    } else if (c == '"') {
                        string = false;
                    }
                } else if (c == '"') {
                    string = true;
                } else if (c == '[' || c == '{') {
                    depth++;
                } else if ((c == ']' || c == '}') && --depth == 0) {
                    break;
                }
            }
            patches.add(text.substring(start, end + 1));
            at = text.indexOf("\"patch\"", end);
        }
        return patches;
    }
}
