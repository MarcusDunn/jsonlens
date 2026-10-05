package ca.marcusdunn.jsonlens.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.patch.JsonPatch;
import ca.marcusdunn.jsonlens.patch.PatchError;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The samples of the JSON Patch documentation. */
class PatchSnippets {

    @Test
    void apply() {
        List<String> lines = new ArrayList<>();
        // @start region="apply"
        // Keep numbers exact, and reject duplicate names, so that a patch with two "op" members fails.
        JsonMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        JsonNode document = mapper.readTree("""
                {"baz": "qux", "foo": "bar"}""");
        JsonNode patchDocument = mapper.readTree("""
                [{"op": "replace", "path": "/baz", "value": "boo"},
                 {"op": "add", "path": "/hello", "value": ["world"]},
                 {"op": "remove", "path": "/foo"}]""");

        // Read the patch with any model, and apply it in place with an editable model.
        Result<JsonNode, PatchError> result = JsonPatch.parse(patchDocument, JacksonJsonModel.INSTANCE)
                .flatMap(patch -> patch.apply(document, JacksonJsonModel.INSTANCE));
        switch (result) {
            case Result.Ok<JsonNode, PatchError>(JsonNode root) -> lines.add("patched: " + root);
            case Result.Err<JsonNode, PatchError>(PatchError error) -> lines.add(error.message());
        }
        // patched: {"baz":"boo","hello":["world"]}

        // A patch is atomic: after an error, the document has its original value.
        JsonNode failing = mapper.readTree("""
                [{"op": "replace", "path": "/baz", "value": 42},
                 {"op": "test", "path": "/baz", "value": "C"}]""");
        Result<JsonNode, PatchError> failed = JsonPatch.parse(failing, JacksonJsonModel.INSTANCE)
                .flatMap(patch -> patch.apply(document, JacksonJsonModel.INSTANCE));
        // failed: Err[error=TestFailed[operation=1]], document: {"baz":"boo","hello":["world"]}
        // @end region="apply"
        assertEquals(List.of("patched: {\"baz\":\"boo\",\"hello\":[\"world\"]}"), lines);
        assertEquals("Err[error=TestFailed[operation=1]]", failed.toString());
        assertEquals("{\"baz\":\"boo\",\"hello\":[\"world\"]}", document.toString());
    }
    @Test
    void applyToCopy() {
        JsonMapper mapper = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
        // @start region="apply-to-copy"
        JsonNode document = mapper.readTree("""
                {"name": "a", "lines": [{"sku": "x", "qty": 1}], "notes": {"long": "text"}}""");
        JsonNode patchDocument = mapper.readTree("""
                [{"op": "replace", "path": "/lines/0/qty", "value": 2}]""");

        JsonNode changed = JsonPatch.parse(patchDocument, JacksonJsonModel.INSTANCE)
                .flatMap(patch -> patch.applyToCopy(document, JacksonJsonModel.INSTANCE))
                .orElse(document);
        // document: {"name":"a","lines":[{"sku":"x","qty":1}],"notes":{"long":"text"}} (not changed)
        // changed:  {"name":"a","lines":[{"sku":"x","qty":2}],"notes":{"long":"text"}}
        // Only the root, "/lines", and "/lines/0" are new. "/notes" is the same node in both.
        // @end region="apply-to-copy"
        assertEquals("{\"name\":\"a\",\"lines\":[{\"sku\":\"x\",\"qty\":1}],\"notes\":{\"long\":\"text\"}}", document.toString());
        assertEquals("{\"name\":\"a\",\"lines\":[{\"sku\":\"x\",\"qty\":2}],\"notes\":{\"long\":\"text\"}}", changed.toString());
        org.junit.jupiter.api.Assertions.assertSame(document.get("notes"), changed.get("notes"));
    }
}
