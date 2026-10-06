package ca.marcusdunn.jsonlens.mergepatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class ErrorsTest {

    private static Maybe<MergePatchError> parseMapped(String patch) {
        MappedJson json = Documents.mapped(patch);
        return JsonMergePatch.parse(json.root(), json.model()).fold(valid -> Maybe.none(), Maybe::some);
    }

    private static JsonMergePatch<JsonNode> patch(String json) {
        return Documents.valid(JsonMergePatch.parse(Documents.jackson(json), JacksonJsonModel.INSTANCE));
    }

    @Test
    @Requirement("lib/merge-patch-duplicate-names")
    void aPatchWithADuplicateNameIsAnError() {
        assertEquals(Maybe.some(new MergePatchError.DuplicateName(List.of(), "a")), parseMapped("{\"a\": 1, \"a\": null}"));
        assertEquals(Maybe.some(new MergePatchError.DuplicateName(List.of("x"), "b")),
                parseMapped("{\"x\": {\"b\": 1, \"c\": 2, \"b\": 1}}"));
        // Also in an object in an array, which the merge copies as a whole.
        assertEquals(Maybe.some(new MergePatchError.DuplicateName(List.of("x", "y", "1"), "b")),
                parseMapped("{\"x\": {\"y\": [0, {\"b\": 1, \"b\": 2}]}}"));
        assertEquals(Maybe.some(new MergePatchError.DuplicateName(List.of("0", "0"), "")),
                parseMapped("[[{\"\": 1, \"\": 2}]]"));
        // Names with the same hash code are not duplicates.
        assertEquals("Aa".hashCode(), "BB".hashCode());
        assertTrue(parseMapped("{\"Aa\": 1, \"BB\": 2, \"x\": [{\"Aa\": 1, \"BB\": 2}], \"y\": {\"a\": {\"a\": 1}}}").isNone());
        // The same name in two objects is not a duplicate.
        assertTrue(parseMapped("[{\"a\": 1}, {\"a\": 2}, {\"a\": {\"a\": 3}}]").isNone());
    }

    @Test
    @Requirement("lib/merge-patch-target-duplicates")
    void aTargetNameThatIsNotUniqueIsAnErrorIfThePatchChangesIt() {
        CountingModel model = new CountingModel(true, "dup");
        String target = "{\"dup\": 1, \"o\": {\"dup\": 2, \"x\": 3}, \"s\": 4}";
        for (String patch : List.of("{\"o\": {\"dup\": 5}}", "{\"o\": {\"dup\": null}}", "{\"o\": {\"dup\": {\"a\": 1}}}")) {
            Object document = Documents.collections(target);
            assertEquals(Result.err(new MergePatchError.TargetDuplicateName(List.of("o"), "dup")),
                    patch(patch).applyToCopy(document, model));
            assertEquals(Result.err(new MergePatchError.TargetDuplicateName(List.of("o"), "dup")),
                    patch(patch).apply(document, model));
            Documents.assertJson(target, model, document);
        }
        assertEquals(Result.err(new MergePatchError.TargetDuplicateName(List.of(), "dup")),
                patch("{\"dup\": 1}").applyToCopy(Documents.collections(target), model));
        // Other names, and objects that are not in the target, have no duplicates.
        Documents.assertJson("{\"dup\": 1, \"o\": {\"dup\": 2, \"x\": 6}, \"s\": {\"dup\": 7}}", model,
                Documents.success(patch("{\"o\": {\"x\": 6}, \"s\": {\"dup\": 7}}").applyToCopy(Documents.collections(target), model)));
    }

    @Test
    @Requirement("lib/merge-patch-errors")
    void aNumberThatTheTargetCannotHoldIsAnError() {
        CountingModel model = new CountingModel(false, "");
        String target = "{\"a\": {\"b\": 1}, \"c\": [\"s\"]}";
        assertEquals(Result.err(new MergePatchError.ValueNotRepresentable(List.of())),
                patch("1").apply(Documents.collections(target), model));
        assertEquals(Result.err(new MergePatchError.ValueNotRepresentable(List.of("a", "b"))),
                patch("{\"a\": {\"b\": 2}}").applyToCopy(Documents.collections(target), model));
        assertEquals(Result.err(new MergePatchError.ValueNotRepresentable(List.of("c"))),
                patch("{\"c\": [\"t\", 3]}").apply(Documents.collections(target), model));
        assertEquals(Result.err(new MergePatchError.ValueNotRepresentable(List.of("n", "m"))),
                patch("{\"n\": {\"m\": 4}}").apply(Documents.collections(target), model));
        // Without numbers, other values still work.
        Documents.assertJson("{\"a\": {\"b\": 1}, \"c\": [\"t\", true, null, {}]}", model,
                Documents.success(patch("{\"c\": [\"t\", true, null, {}]}").apply(Documents.collections(target), model)));
    }

    @Test
    @Requirement("lib/merge-patch-atomic")
    void afterAnErrorTheTargetHasItsOriginalValue() {
        CountingModel model = new CountingModel(false, "");
        String target = "{\"a\": {\"b\": \"x\", \"c\": \"y\"}, \"d\": \"z\", \"e\": [true]}";
        // Each kind of change comes before the number in the order of the patch.
        String patch = "{\"a\": {\"b\": null, \"c\": \"new\", \"f\": {\"g\": \"h\"}}, \"d\": null, \"e\": \"s\", \"i\": {\"j\": 1}}";
        Object document = Documents.collections(target);
        assertEquals(Result.err(new MergePatchError.ValueNotRepresentable(List.of("i", "j"))), patch(patch).apply(document, model));
        Documents.assertJson(target, model, document);
        assertEquals(0, model.edits);
        assertEquals(Result.err(new MergePatchError.ValueNotRepresentable(List.of("i", "j"))),
                patch(patch).applyToCopy(document, model));
        Documents.assertJson(target, model, document);
    }

    @Test
    @Requirement("lib/merge-patch-errors")
    void errorsDescribeTheirCause() {
        assertEquals(List.of(
                        "The object at '' of the merge patch has more than one 'a' member.",
                        "The object at '/x/0/a~1b~0c' of the target has more than one 'n' member, and the merge patch changes it.",
                        "The value at '/x/~0~1' of the merge patch cannot be added to the target: a number is too large for the"
                                + " target model."),
                List.of(
                        new MergePatchError.DuplicateName(List.of(), "a").message(),
                        new MergePatchError.TargetDuplicateName(List.of("x", "0", "a/b~c"), "n").message(),
                        new MergePatchError.ValueNotRepresentable(List.of("x", "~/")).message()));
        // The records keep a copy of the path.
        List<String> path = new ArrayList<>(List.of("a"));
        List<MergePatchError> errors = List.of(
                new MergePatchError.DuplicateName(path, "n"),
                new MergePatchError.TargetDuplicateName(path, "n"),
                new MergePatchError.ValueNotRepresentable(path));
        path.add("b");
        assertEquals(List.of(
                        new MergePatchError.DuplicateName(List.of("a"), "n"),
                        new MergePatchError.TargetDuplicateName(List.of("a"), "n"),
                        new MergePatchError.ValueNotRepresentable(List.of("a"))),
                errors);
    }

    /// A model that is not well-formed: it gives no element of an array.
    private enum NoElements implements JsonModel<Object> {
        INSTANCE;

        private static final JavaCollectionsModel MODEL = JavaCollectionsModel.INSTANCE;

        @Override
        public JsonKind kind(Object node) {
            return MODEL.kind(node);
        }

        @Override
        public int arrayLength(Object array) {
            return MODEL.arrayLength(array);
        }

        @Override
        public Maybe<Object> element(Object array, int index) {
            return Maybe.none();
        }

        @Override
        public MemberCursor<Object> memberCursor(Object object) {
            return MODEL.memberCursor(object);
        }

        @Override
        public JsonString stringValue(Object string) {
            return MODEL.stringValue(string);
        }

        @Override
        public JsonNumber numberValue(Object number) {
            return MODEL.numberValue(number);
        }
    }

    @Test
    @Requirement("lib/merge-patch-errors")
    void aPatchModelThatIsNotWellFormedGivesAnError() {
        JsonMergePatch<Object> patch = Documents.valid(JsonMergePatch.parse(Documents.collections("{\"a\": [{\"b\": 1, \"c\": 2}]}"),
                NoElements.INSTANCE));
        assertEquals(Result.err(new MergePatchError.ValueNotRepresentable(List.of("a"))),
                patch.apply(Documents.collections("{}"), JavaCollectionsModel.INSTANCE));
    }
}
