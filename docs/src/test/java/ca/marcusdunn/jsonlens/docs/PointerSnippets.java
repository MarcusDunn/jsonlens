package ca.marcusdunn.jsonlens.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.pointer.JsonPointer;
import ca.marcusdunn.jsonlens.pointer.PointerError;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The samples of the JSON Pointer documentation. */
class PointerSnippets {

    @Test
    void resolve() {
        List<String> lines = new ArrayList<>();
        // @start region="resolve"
        JsonNode document = JsonMapper.builder().build().readTree("""
                {"foo": ["bar", "baz"], "a/b": 1}""");

        // Parse a pointer. A '/' in a name is written as "~1".
        JsonPointer pointer = JsonPointer.parse("/a~1b").orElse(JsonPointer.root());

        // Resolve it with the model of your JSON library. The result is your own node.
        switch (pointer.resolve(document, JacksonJsonModel.INSTANCE)) {
            case Result.Ok<JsonNode, PointerError>(JsonNode value) -> lines.add("value: " + value);
            case Result.Err<JsonNode, PointerError>(PointerError error) -> lines.add(error.message());
        }
        // value: 1

        // An error is a value, not an exception.
        Result<JsonNode, PointerError> missing =
                JsonPointer.parse("/foo/2").orElse(JsonPointer.root()).resolve(document, JacksonJsonModel.INSTANCE);
        // missing: Err[error=IndexOutOfRange[parent=/foo, token=2]]

        // The URI fragment form has percent-encoding.
        Maybe<String> fragment = JsonPointer.of(List.of("c%d")).toFragment(); // Some[value=#/c%25d]
        // @end region="resolve"
        assertEquals(List.of("value: 1"), lines);
        assertEquals("Err[error=IndexOutOfRange[parent=/foo, token=2]]", missing.toString());
        assertEquals(Maybe.some("#/c%25d"), fragment);
    }
}
