package ca.marcusdunn.jsonlens.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.mergepatch.JsonMergePatch;
import ca.marcusdunn.jsonlens.mergepatch.MergePatchError;
import ca.marcusdunn.jsonlens.model.Result;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The samples of the JSON Merge Patch documentation. */
class MergePatchSnippets {

    @Test
    void apply() {
        List<String> lines = new ArrayList<>();
        // @start region="apply"
        // Keep numbers exact, and reject duplicate names, which have no defined value.
        JsonMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        JsonNode document = mapper.readTree("""
                {"title": "Goodbye!", "author": {"givenName": "John", "familyName": "Doe"}, "tags": ["example", "sample"]}""");
        JsonNode patchDocument = mapper.readTree("""
                {"title": "Hello!", "author": {"familyName": null}, "tags": ["example"]}""");

        // Read the patch with any model, and merge it in place with an editable model.
        // null removes a member, an object merges into a member, and other values replace a member.
        Result<JsonNode, MergePatchError> result = JsonMergePatch.parse(patchDocument, JacksonJsonModel.INSTANCE)
                .flatMap(patch -> patch.apply(document, JacksonJsonModel.INSTANCE));
        switch (result) {
            case Result.Ok<JsonNode, MergePatchError>(JsonNode root) -> lines.add("merged: " + root);
            case Result.Err<JsonNode, MergePatchError>(MergePatchError error) -> lines.add(error.message());
        }
        // merged: {"title":"Hello!","author":{"givenName":"John"},"tags":["example"]}
        // @end region="apply"
        assertEquals(List.of("merged: {\"title\":\"Hello!\",\"author\":{\"givenName\":\"John\"},\"tags\":[\"example\"]}"), lines);
        assertEquals("{\"title\":\"Hello!\",\"author\":{\"givenName\":\"John\"},\"tags\":[\"example\"]}", document.toString());
    }

    @Test
    void applyToCopy() {
        JsonMapper mapper = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
        // @start region="apply-to-copy"
        JsonNode document = mapper.readTree("""
                {"name": "a", "settings": {"color": "red", "size": 1}, "notes": {"long": "text"}}""");
        JsonNode patchDocument = mapper.readTree("""
                {"settings": {"size": 2}}""");

        JsonNode changed = JsonMergePatch.parse(patchDocument, JacksonJsonModel.INSTANCE)
                .flatMap(patch -> patch.applyToCopy(document, JacksonJsonModel.INSTANCE))
                .orElse(document);
        // document: {"name":"a","settings":{"color":"red","size":1},"notes":{"long":"text"}} (not changed)
        // changed:  {"name":"a","settings":{"color":"red","size":2},"notes":{"long":"text"}}
        // Only the root and "settings" are new. "notes" is the same node in both.
        // @end region="apply-to-copy"
        assertEquals("{\"name\":\"a\",\"settings\":{\"color\":\"red\",\"size\":1},\"notes\":{\"long\":\"text\"}}", document.toString());
        assertEquals("{\"name\":\"a\",\"settings\":{\"color\":\"red\",\"size\":2},\"notes\":{\"long\":\"text\"}}", changed.toString());
        assertSame(document.get("notes"), changed.get("notes"));
    }
}
