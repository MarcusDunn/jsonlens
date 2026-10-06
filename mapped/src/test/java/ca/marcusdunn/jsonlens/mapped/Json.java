package ca.marcusdunn.jsonlens.mapped;

import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.Result;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/// Helpers for the tests of the mapped module.
final class Json {

    private Json() {}

    static MappedJson of(byte[] bytes) {
        return switch (MappedJson.of(ByteBuffer.wrap(bytes))) {
            case Result.Ok<MappedJson, MappedJsonError>(MappedJson json) -> json;
            case Result.Err<MappedJson, MappedJsonError>(MappedJsonError error) -> fail(error.message());
        };
    }

    static MappedJson of(String text) {
        return of(text.getBytes(StandardCharsets.UTF_8));
    }

    static MappedJson of(String text, MappedJson.Limits limits) {
        return switch (MappedJson.of(ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)), limits)) {
            case Result.Ok<MappedJson, MappedJsonError>(MappedJson json) -> json;
            case Result.Err<MappedJson, MappedJsonError>(MappedJsonError error) -> fail(error.message());
        };
    }

    static MappedJsonError error(byte[] bytes) {
        return switch (MappedJson.of(ByteBuffer.wrap(bytes))) {
            case Result.Ok<MappedJson, MappedJsonError>(MappedJson json) -> fail("expected an error");
            case Result.Err<MappedJson, MappedJsonError>(MappedJsonError error) -> error;
        };
    }

    static MappedJsonError error(String text) {
        return error(text.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
