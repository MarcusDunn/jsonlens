package ca.marcusdunn.jsonlens.patch;

import static ca.marcusdunn.jsonlens.patch.OperationsTest.patch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

@Requirement("lib/patch-copy-limit")
class CopyLimitTest {

    private static final JacksonJsonModel MODEL = JacksonJsonModel.INSTANCE;

    /// Values of different shapes: scalars, flat and nested arrays and objects.
    private static final List<String> VALUES = List.of(
            "1", "[]", "{}", "[1, 2, 3]", "{\"a\": 1, \"b\": 2}", "[[[1]]]", "{\"a\": {\"b\": {\"c\": 1}}}",
            "[{\"a\": [1, 2]}, {}, [3]]", "{\"x\": [1, {\"y\": 2}], \"z\": null}");

    /// The number of nodes of a value, by recursion.
    private static int nodes(JsonNode value) {
        int count = 1;
        for (JsonNode child : value.values()) {
            count += nodes(child);
        }
        return count;
    }

    /// Checks that a result is a document with the expected value, by JSON equality: a copy can
    /// hold a number in another Jackson node type.
    private static void assertOk(JsonNode expected, Result<JsonNode, PatchError> result, String message) {
        assertTrue(result instanceof Result.Ok<JsonNode, PatchError>(JsonNode value) && MODEL.equal(expected, value),
                message + ": " + result);
    }

    private static JsonPatch<JsonNode> copyOfV(int limit) {
        return patch("[{\"op\": \"copy\", \"from\": \"/v\", \"path\": \"/c\"}]").withLimits(new JsonPatch.Limits(limit));
    }

    @Test
    void aCopyAtTheLimitSucceedsAndOneNodeMoreFails() {
        for (String value : VALUES) {
            String text = "{\"v\": " + value + "}";
            int nodes = nodes(Documents.jackson(value));
            JsonNode document = Documents.jackson(text);
            JsonNode expected = Documents.jackson("{\"v\": " + value + ", \"c\": " + value + "}");
            assertOk(expected, copyOfV(nodes).applyToCopy(document, MODEL), value);
            assertEquals(Result.err(new PatchError.CopyLimitExceeded(0, nodes - 1)),
                    copyOfV(nodes - 1).applyToCopy(document, MODEL), value);
            assertEquals(Result.err(new PatchError.CopyLimitExceeded(0, nodes - 1)),
                    copyOfV(nodes - 1).apply(document, MODEL), value);
            assertEquals(Documents.jackson(text), document, value);
            assertOk(expected, copyOfV(nodes).apply(document, MODEL), value);
        }
    }

    @Test
    void theLimitIsForAllCopiesTogether() {
        String twoCopies = "[{\"op\": \"copy\", \"from\": \"/v\", \"path\": \"/c\"}, {\"op\": \"copy\", \"from\": \"/v\", \"path\": \"/d\"}]";
        JsonNode document = Documents.jackson("{\"v\": [1, 2]}");
        assertEquals(Result.err(new PatchError.CopyLimitExceeded(1, 5)),
                patch(twoCopies).withLimits(new JsonPatch.Limits(5)).applyToCopy(document, MODEL));
        assertOk(Documents.jackson("{\"v\": [1, 2], \"c\": [1, 2], \"d\": [1, 2]}"),
                patch(twoCopies).withLimits(new JsonPatch.Limits(6)).applyToCopy(document, MODEL), "two copies");
    }

    @Test
    void aPatchThatDoublesTheDocumentStopsAtTheDefaultLimit() {
        // Each copy appends the whole document to its own array, so it doubles the document: 30
        // copies would make 2^31 nodes.
        String copy = "{\"op\": \"copy\", \"from\": \"\", \"path\": \"/a/-\"}";
        JsonPatch<JsonNode> doubling = patch("[" + String.join(", ", java.util.Collections.nCopies(30, copy)) + "]");
        assertSame(JsonPatch.Limits.DEFAULT, doubling.limits());
        JsonNode document = Documents.jackson("{\"a\": []}");
        // Operation k copies 2^(k + 1) nodes, so the copies so far have 2^(k + 2) - 2 nodes. That
        // passes 1,000,000 at k = 18.
        assertEquals(Result.err(new PatchError.CopyLimitExceeded(18, 1_000_000)), doubling.apply(document, MODEL));
        assertEquals(Documents.jackson("{\"a\": []}"), document);
        assertEquals(Result.err(new PatchError.CopyLimitExceeded(18, 1_000_000)), doubling.applyToCopy(document, MODEL));
    }

    @Test
    void theErrorHasAMessage() {
        assertEquals("Operation 3 copies too many nodes: the copy operations of the patch add more than 10 nodes.",
                new PatchError.CopyLimitExceeded(3, 10).message());
    }

    @Test
    void otherOperationsHaveNoLimit() {
        JsonNode document = Documents.jackson("{\"v\": [1, 2, 3]}");
        String patch = "[{\"op\": \"add\", \"path\": \"/a\", \"value\": [1, 2, 3]}, {\"op\": \"move\", \"from\": \"/v\", \"path\": \"/m\"}]";
        assertTrue(patch(patch).withLimits(new JsonPatch.Limits(0)).applyToCopy(document, MODEL).isOk());
    }
}
