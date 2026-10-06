package ca.marcusdunn.jsonlens.mergepatch;

import static ca.marcusdunn.jsonlens.mergepatch.Documents.assertMerged;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.kotlinx.KotlinxJsonModel;
import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import kotlinx.serialization.json.JsonElement;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class MergeTest {

    @Test
    @Requirement("rfc7396-A/examples")
    void eachExampleOfAppendixAGivesItsResult() {
        String[][] examples = {
            {"{\"a\":\"b\"}", "{\"a\":\"c\"}", "{\"a\":\"c\"}"},
            {"{\"a\":\"b\"}", "{\"b\":\"c\"}", "{\"a\":\"b\",\"b\":\"c\"}"},
            {"{\"a\":\"b\"}", "{\"a\":null}", "{}"},
            {"{\"a\":\"b\",\"b\":\"c\"}", "{\"a\":null}", "{\"b\":\"c\"}"},
            {"{\"a\":[\"b\"]}", "{\"a\":\"c\"}", "{\"a\":\"c\"}"},
            {"{\"a\":\"c\"}", "{\"a\":[\"b\"]}", "{\"a\":[\"b\"]}"},
            {"{\"a\":{\"b\":\"c\"}}", "{\"a\":{\"b\":\"d\",\"c\":null}}", "{\"a\":{\"b\":\"d\"}}"},
            {"{\"a\":[{\"b\":\"c\"}]}", "{\"a\":[1]}", "{\"a\":[1]}"},
            {"[\"a\",\"b\"]", "[\"c\",\"d\"]", "[\"c\",\"d\"]"},
            {"{\"a\":\"b\"}", "[\"c\"]", "[\"c\"]"},
            {"{\"a\":\"foo\"}", "null", "null"},
            {"{\"a\":\"foo\"}", "\"bar\"", "\"bar\""},
            {"{\"e\":null}", "{\"a\":1}", "{\"e\":null,\"a\":1}"},
            {"[1,2]", "{\"a\":\"b\",\"c\":null}", "{\"a\":\"b\"}"},
            {"{}", "{\"a\":{\"bb\":{\"ccc\":null}}}", "{\"a\":{\"bb\":{}}}"},
        };
        assertEquals(15, examples.length);
        for (String[] example : examples) {
            assertMerged(example[0], example[1], example[2]);
        }
    }

    @Test
    @Requirement("rfc7396-3/example")
    void theExampleOfSection3GivesItsResult() {
        assertMerged("""
                {
                  "title": "Goodbye!",
                  "author" : {
                    "givenName" : "John",
                    "familyName" : "Doe"
                  },
                  "tags":[ "example", "sample" ],
                  "content": "This will be unchanged"
                }""", """
                {
                  "title": "Hello!",
                  "phoneNumber": "+01-123-456-7890",
                  "author": {
                    "familyName": null
                  },
                  "tags": [ "example" ]
                }""", """
                {
                  "title": "Hello!",
                  "author" : {
                    "givenName" : "John"
                  },
                  "tags": [ "example" ],
                  "content": "This will be unchanged",
                  "phoneNumber": "+01-123-456-7890"
                }""");
    }

    @Test
    @Requirement("rfc7396-2/document")
    void eachJsonValueIsAMergePatch() {
        for (String patch : List.of("{\"a\": 1}", "{}", "[1, null]", "[]", "\"s\"", "-1.5e3", "true", "false", "null")) {
            assertTrue(JsonMergePatch.parse(Documents.jackson(patch), JacksonJsonModel.INSTANCE).isOk());
            MappedJson mapped = Documents.mapped(patch);
            assertTrue(JsonMergePatch.parse(mapped.root(), mapped.model()).isOk());
        }
        JsonNode document = Documents.jackson("{\"a\": [1]}");
        JsonMergePatch<JsonNode> patch = Documents.valid(JsonMergePatch.parse(document, JacksonJsonModel.INSTANCE));
        assertSame(document, patch.document());
        assertSame(JacksonJsonModel.INSTANCE, patch.model());
    }

    @Test
    @Requirement("rfc7396-2/replace")
    void aPatchThatIsNotAnObjectReplacesTheTarget() {
        for (String target : List.of("{\"a\": 1}", "[1, 2]", "\"s\"", "3", "true", "null", "{}")) {
            for (String patch : List.of("[{\"a\": null}, null]", "[]", "\"x\"", "1.50", "-0", "true", "false", "null")) {
                assertMerged(target, patch, patch);
            }
        }
    }

    @Test
    @Requirement("rfc7396-2/non-object-target")
    void anObjectPatchMergesIntoAnEmptyObjectIfTheTargetIsNotAnObject() {
        for (String target : List.of("[1, {\"a\": 2}]", "[]", "\"s\"", "3", "true", "false", "null")) {
            assertMerged(target, "{}", "{}");
            assertMerged(target, "{\"a\": null}", "{}");
            assertMerged(target, "{\"a\": null, \"b\": [null], \"c\": {\"d\": null, \"e\": 1}}",
                    "{\"b\": [null], \"c\": {\"e\": 1}}");
        }
    }

    @Test
    @Requirement("rfc7396-2/add-replace")
    void aMemberValueAddsOrReplacesTheMember() {
        assertMerged("{\"a\": 1}", "{\"b\": \"x\"}", "{\"a\": 1, \"b\": \"x\"}");
        assertMerged("{\"a\": 1}", "{\"a\": \"x\"}", "{\"a\": \"x\"}");
        assertMerged("{\"a\": {\"b\": 1}}", "{\"a\": 2}", "{\"a\": 2}");
        assertMerged("{\"a\": null}", "{\"a\": false}", "{\"a\": false}");
        assertMerged("{\"a\": 1, \"b\": 2}", "{\"b\": 3.25, \"c\": true, \"\": \"empty\", \"a/b~c\": 4}",
                "{\"a\": 1, \"b\": 3.25, \"c\": true, \"\": \"empty\", \"a/b~c\": 4}");
        // Names compare by their Unicode scalar values.
        assertMerged("{\"\\u00e9\": 1, \"e\\u0301\": 2}", "{\"\\u00e9\": 3, \"\\ud83d\\ude00\": 4}",
                "{\"\\u00e9\": 3, \"e\\u0301\": 2, \"\\ud83d\\ude00\": 4}");
    }

    @Test
    @Requirement("rfc7396-2/null-removes")
    void aNullMemberRemovesTheMember() {
        assertMerged("{\"a\": 1, \"b\": 2}", "{\"a\": null}", "{\"b\": 2}");
        assertMerged("{\"a\": {\"b\": [1]}}", "{\"a\": null}", "{}");
        assertMerged("{\"a\": null}", "{\"a\": null}", "{}");
        // A member that is not in the target needs no change.
        assertMerged("{\"a\": 1}", "{\"b\": null}", "{\"a\": 1}");
        assertMerged("{\"a\": 1, \"b\": 2, \"c\": 3}", "{\"a\": null, \"c\": null, \"d\": null}", "{\"b\": 2}");
    }

    @Test
    @Requirement("rfc7396-2/recursive")
    void anObjectMemberMergesIntoTheMember() {
        assertMerged("{\"a\": {\"b\": 1, \"c\": {\"d\": 2, \"e\": 3}}, \"f\": 4}",
                "{\"a\": {\"b\": null, \"c\": {\"d\": 5, \"g\": {\"h\": null}}, \"i\": {}}}",
                "{\"a\": {\"c\": {\"d\": 5, \"e\": 3, \"g\": {}}, \"i\": {}}, \"f\": 4}");
        // The target member is not an object, or is not there: the merge starts from an empty object.
        assertMerged("{\"a\": [1], \"b\": \"s\", \"c\": null}",
                "{\"a\": {\"x\": null, \"y\": 1}, \"b\": {\"x\": {\"z\": null}}, \"c\": {}, \"d\": {\"e\": null}}",
                "{\"a\": {\"y\": 1}, \"b\": {\"x\": {}}, \"c\": {}, \"d\": {}}");
        assertMerged("{\"a\": {\"b\": 1}}", "{\"a\": {}}", "{\"a\": {\"b\": 1}}");
    }

    @Test
    @Requirement("rfc7396-2/arrays")
    void anArrayReplacesTheValueAsAWhole() {
        assertMerged("{\"a\": [1, 2, 3]}", "{\"a\": [4]}", "{\"a\": [4]}");
        assertMerged("{\"a\": [{\"b\": 1}]}", "{\"a\": [{\"c\": 2}]}", "{\"a\": [{\"c\": 2}]}");
        // The null elements, and the null members of objects in the array, stay.
        assertMerged("{\"a\": {\"b\": 1}}", "{\"a\": [null, {\"b\": null, \"c\": [{\"d\": null}]}]}",
                "{\"a\": [null, {\"b\": null, \"c\": [{\"d\": null}]}]}");
        assertMerged("{\"a\": 1}", "[{\"a\": null}]", "[{\"a\": null}]");
        assertMerged("[1, 2]", "{\"a\": []}", "{\"a\": []}");
    }

    @Test
    @Requirement("lib/merge-patch-model-agnostic")
    void patchDocumentsAndTargetsCanBeOfAnyModel() {
        String patch = "{\"a\": {\"b\": [1.5, \"x\"], \"c\": null}, \"d\": 1e400}";
        String expected = "{\"a\": {\"b\": [1.5, \"x\"]}, \"d\": 1e400, \"e\": true}";
        MappedJson mapped = Documents.mapped(patch);
        List<JsonMergePatch<?>> patches = List.of(
                Documents.valid(JsonMergePatch.parse(Documents.jackson(patch), JacksonJsonModel.INSTANCE)),
                Documents.valid(JsonMergePatch.parse(Documents.kotlinx(patch), KotlinxJsonModel.INSTANCE)),
                Documents.valid(JsonMergePatch.parse(mapped.root(), mapped.model())),
                Documents.valid(JsonMergePatch.parse(Documents.collections(patch), JavaCollectionsModel.INSTANCE)));
        for (JsonMergePatch<?> each : patches) {
            String target = "{\"a\": {\"c\": 2}, \"e\": true}";
            Documents.assertJson(expected, JacksonJsonModel.INSTANCE,
                    Documents.success(each.apply(Documents.jackson(target), JacksonJsonModel.INSTANCE)));
            Documents.assertJson(expected, JavaCollectionsModel.INSTANCE,
                    Documents.success(each.apply(Documents.collections(target), JavaCollectionsModel.INSTANCE)));
            // kotlinx.serialization trees are immutable: only applyToCopy changes them.
            JsonElement kotlinx = Documents.kotlinx(target);
            Documents.assertJson(expected, KotlinxJsonModel.INSTANCE,
                    Documents.success(each.applyToCopy(kotlinx, KotlinxJsonModel.INSTANCE)));
            Documents.assertJson(target, KotlinxJsonModel.INSTANCE, kotlinx);
        }
    }
}
