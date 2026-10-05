package ca.marcusdunn.jsonlens.testkit;

import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/// Reads JSON texts for the tests: with the mapped model, and as Java collections.
final class Documents {

    private Documents() {}

    static MappedJson mapped(String json) {
        return MappedJson.of(ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8))).fold(value -> value, error -> fail(json + ": " + error));
    }

    static Object collections(String json) {
        MappedJson mapped = mapped(json);
        return JavaCollectionsModel.INSTANCE.copyOf(mapped.model(), mapped.root()).orElseGet(() -> fail("copy of " + json));
    }
}
