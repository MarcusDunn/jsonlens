package ca.marcusdunn.jsonlens.jackson;

import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;

/// The read-only model of [JacksonStream]. Each node knows its stream, so one model serves all.
/// A method that needs a child that the stream has not read yet makes the stream read forward.
enum JacksonStreamModel implements JsonModel<JacksonStreamNode> {
    INSTANCE;

    @Override
    public JsonKind kind(JacksonStreamNode node) {
        return node.kind;
    }

    @Override
    public int arrayLength(JacksonStreamNode array) {
        return array.stream.readAll(array).children.size();
    }

    @Override
    public Maybe<JacksonStreamNode> element(JacksonStreamNode array, int index) {
        return index < 0 ? Maybe.none() : array.stream.child(array, index);
    }

    @Override
    public int memberCount(JacksonStreamNode object) {
        return object.stream.readAll(object).children.size();
    }

    @Override
    public Maybe<JacksonStreamNode> member(JacksonStreamNode object, JsonString name) {
        return object.stream.member(object, name);
    }

    @Override
    public MemberCursor<JacksonStreamNode> memberCursor(JacksonStreamNode object) {
        return object.stream.cursor(object);
    }

    @Override
    public JsonString stringValue(JacksonStreamNode string) {
        return string.string;
    }

    @Override
    public JsonNumber numberValue(JacksonStreamNode number) {
        return number.number;
    }
}
