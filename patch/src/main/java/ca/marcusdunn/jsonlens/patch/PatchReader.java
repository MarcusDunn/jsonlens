package ca.marcusdunn.jsonlens.patch;

import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.pointer.JsonPointer;
import java.util.ArrayList;
import java.util.List;

/// Reads the operations of a JSON Patch document (RFC 6902, Sections 3 and 4). It keeps the
/// values of the operations as nodes of the document, without a copy.
///
/// @param <P> the node type of the model of the patch document
final class PatchReader<P> {

    private final JsonModel<P> model;

    PatchReader(JsonModel<P> model) {
        this.model = model;
    }

    Result<List<Operation<P>>, PatchError> read(P document) {
        if (model.kind(document) != JsonKind.ARRAY) {
            return Result.err(new PatchError.NotAnArray());
        }
        int length = model.arrayLength(document);
        List<Operation<P>> operations = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            // A well-formed model has each element up to the length. Without one, the array itself
            // takes its place, and it is not an operation object.
            P element = model.element(document, index).orElse(document);
            switch (operation(element, index)) {
                case Result.Ok<Operation<P>, PatchError>(Operation<P> operation) -> operations.add(operation);
                case Result.Err<Operation<P>, PatchError>(PatchError error) -> {
                    return Result.err(error);
                }
            }
        }
        return Result.ok(List.copyOf(operations));
    }

    private Result<Operation<P>, PatchError> operation(P object, int index) {
        if (model.kind(object) != JsonKind.OBJECT) {
            return Result.err(new PatchError.NotAnObject(index));
        }
        return string(object, index, "op").flatMap(op -> {
            String name = JsonString.copyOf(op);
            return switch (name) {
                case "add" -> pointer(object, index, "path").flatMap(path ->
                        required(object, index, "value").map(value -> new Operation.Add<>(path, value)));
                case "remove" -> pointer(object, index, "path").map(Operation.Remove::new);
                case "replace" -> pointer(object, index, "path").flatMap(path ->
                        required(object, index, "value").map(value -> new Operation.Replace<>(path, value)));
                case "move" -> pointer(object, index, "path").flatMap(path ->
                        pointer(object, index, "from").map(from -> new Operation.Move<>(from, path)));
                case "copy" -> pointer(object, index, "path").flatMap(path ->
                        pointer(object, index, "from").map(from -> new Operation.Copy<>(from, path)));
                case "test" -> pointer(object, index, "path").flatMap(path ->
                        required(object, index, "value").map(value -> new Operation.Test<>(path, value)));
                default -> Result.err(new PatchError.UnknownOperation(index, name));
            };
        });
    }

    /// The member with a name. It is an error if the member is missing or not unique.
    private Result<P, PatchError> required(P object, int index, String member) {
        JsonString name = JsonString.of(member);
        if (!(model.member(object, name) instanceof Maybe.Some<P>(P value))) {
            return Result.err(new PatchError.MissingMember(index, member));
        }
        if (model.hasDuplicate(object, name)) {
            return Result.err(new PatchError.DuplicateMember(index, member));
        }
        return Result.ok(value);
    }

    private Result<JsonString, PatchError> string(P object, int index, String member) {
        return required(object, index, member).flatMap(value -> model.kind(value) == JsonKind.STRING
                ? Result.ok(model.stringValue(value))
                : Result.err(new PatchError.NotAString(index, member)));
    }

    private Result<JsonPointer, PatchError> pointer(P object, int index, String member) {
        return string(object, index, member).flatMap(text ->
                JsonPointer.parse(text).mapErr(error -> new PatchError.InvalidPointer(index, member, error)));
    }
}
