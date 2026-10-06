package ca.marcusdunn.jsonlens.mergepatch;

import ca.marcusdunn.jsonlens.model.JsonEditor;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Property;
import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import java.util.List;

/// The Java collections model, with a count of the nodes that its factory builds and of the
/// changes of its editor. It can refuse all numbers, as a representation with a limit does, and it
/// can report one member name as a duplicate in each object.
final class CountingModel implements JsonModel<Object>, JsonFactory<Object>, JsonEditor<Object> {

    private static final JavaCollectionsModel MODEL = JavaCollectionsModel.INSTANCE;
    private final boolean numbers;
    private final String duplicate;
    int built;
    int edits;

    CountingModel() {
        this(true, "");
    }

    CountingModel(boolean numbers, String duplicate) {
        this.numbers = numbers;
        this.duplicate = duplicate;
    }

    @Override
    public boolean hasDuplicate(Object object, JsonString name) {
        return JsonString.equal(name, JsonString.of(duplicate)) && !duplicate.isEmpty();
    }

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
        return MODEL.element(array, index);
    }

    @Override
    public Maybe<Object> member(Object object, JsonString name) {
        return MODEL.member(object, name);
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

    @Override
    public Object string(JsonString value) {
        built++;
        return MODEL.string(value);
    }

    @Override
    public Maybe<Object> number(JsonNumber value) {
        built++;
        return numbers ? MODEL.number(value) : Maybe.none();
    }

    @Override
    public Object bool(boolean value) {
        built++;
        return MODEL.bool(value);
    }

    @Override
    public Object nullValue() {
        built++;
        return MODEL.nullValue();
    }

    @Override
    public Object array(List<Object> elements) {
        built++;
        return MODEL.array(elements);
    }

    @Override
    public Object object(List<Property<Object>> members) {
        built++;
        return MODEL.object(members);
    }

    @Override
    public Maybe<Object> putMember(Object object, JsonString name, Object value) {
        edits++;
        return MODEL.putMember(object, name, value);
    }

    @Override
    public Maybe<Object> removeMember(Object object, JsonString name) {
        edits++;
        return MODEL.removeMember(object, name);
    }

    @Override
    public void insertElement(Object array, int index, Object value) {
        MODEL.insertElement(array, index, value);
    }

    @Override
    public Object setElement(Object array, int index, Object value) {
        return MODEL.setElement(array, index, value);
    }

    @Override
    public Object removeElement(Object array, int index) {
        return MODEL.removeElement(array, index);
    }
}
