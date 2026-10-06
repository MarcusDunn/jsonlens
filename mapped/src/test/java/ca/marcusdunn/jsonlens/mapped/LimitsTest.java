package ca.marcusdunn.jsonlens.mapped;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Requirement("lib/mapped-limits")
class LimitsTest {

    private static Result<MappedJson, MappedJsonError> of(String text, MappedJson.Limits limits) {
        return MappedJson.of(ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)), limits);
    }

    private static Result<MappedJson, MappedJsonError> of(String text) {
        return MappedJson.of(ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void theDefaultsAreTheDefaultsOfJackson() {
        assertEquals(new MappedJson.Limits(1000, 1000), MappedJson.Limits.DEFAULT);
    }

    @Test
    void nestingAtTheLimitIsAccepted() {
        assertTrue(of("[".repeat(1000) + "]".repeat(1000)).isOk());
        assertTrue(of("{\"a\":".repeat(999) + "[]" + "}".repeat(999)).isOk());
        assertTrue(of("[[1], {\"a\": [2]}]", new MappedJson.Limits(3, 1000)).isOk());
        assertTrue(of("1", new MappedJson.Limits(0, 1000)).isOk());
    }

    @Test
    void nestingOneLevelDeeperIsAnError() {
        assertEquals(Result.err(new MappedJsonError.NestingTooDeep(1000, 1000)), of("[".repeat(1001) + "]".repeat(1001)));
        assertEquals(Result.err(new MappedJsonError.NestingTooDeep(6, 1)), of("{\"a\": {}}", new MappedJson.Limits(1, 1000)));
        assertEquals(Result.err(new MappedJsonError.NestingTooDeep(0, 0)), of("[]", new MappedJson.Limits(0, 1000)));
    }

    @Test
    void aNumberAtTheLimitIsAccepted() {
        assertTrue(of("1".repeat(1000)).isOk());
        assertTrue(of("[-1.5e+10]", new MappedJson.Limits(1000, 8)).isOk());
        assertTrue(of("[-0]", new MappedJson.Limits(1000, 2)).isOk());
    }

    @Test
    void aLongerNumberIsAnError() {
        assertEquals(Result.err(new MappedJsonError.NumberTooLong(0, 1000)), of("1".repeat(1001)));
        assertEquals(Result.err(new MappedJsonError.NumberTooLong(1, 7)), of("[-1.5e+10]", new MappedJson.Limits(1000, 7)));
        assertEquals(Result.err(new MappedJsonError.NumberTooLong(6, 1000)), of("{\"a\": 1e" + "7".repeat(1000) + "}"));
    }

    @Test
    void aMappedFileHasTheLimits(@TempDir Path directory) throws Exception {
        Path file = Files.writeString(directory.resolve("deep.json"), "[[[]]]");
        assertTrue(MappedJson.open(file).isOk());
        assertEquals(Result.err(new MappedJsonError.NestingTooDeep(2, 2)), MappedJson.open(file, new MappedJson.Limits(2, 1000)));
    }

    @Test
    void theErrorsHaveMessages() {
        assertEquals("The value at byte 7 is nested more than 2 levels deep.", new MappedJsonError.NestingTooDeep(7, 2).message());
        assertEquals("The number at byte 3 has more than 10 characters.", new MappedJsonError.NumberTooLong(3, 10).message());
    }
}
