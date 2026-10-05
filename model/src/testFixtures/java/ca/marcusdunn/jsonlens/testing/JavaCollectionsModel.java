package ca.marcusdunn.jsonlens.testing;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.JsonEditor;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Property;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A reference {@link JsonModel} for JSON values made of {@link Map}, {@link List},
 * {@link String}, {@link Number} or {@link JsonNumber}, and {@link Boolean}. It also builds such
 * values (mutable {@link ArrayList} and {@link LinkedHashMap} containers), and it changes
 * them. To change a value, its containers must be mutable.
 *
 * <p>JSON null is {@link #NULL}. Java {@code null} in a map or a list also means JSON null. The
 * model returns {@link #NULL} for it, because a node must not be {@code null}.
 */
public final class JavaCollectionsModel implements JsonModel<Object>, JsonFactory<Object>, JsonEditor<Object> {

    /** The model. It has no state. */
    public static final JavaCollectionsModel INSTANCE = new JavaCollectionsModel();

    /** The type of the JSON null sentinel. */
    public enum JsonNull {
        /** The one JSON null node. */
        INSTANCE
    }

    /** The node for JSON null. */
    public static final JsonNull NULL = JsonNull.INSTANCE;

    private JavaCollectionsModel() {}

    @Override
    public JsonKind kind(Object node) {
        return switch (node) {
            case Map<?, ?> map -> JsonKind.OBJECT;
            case List<?> list -> JsonKind.ARRAY;
            case String string -> JsonKind.STRING;
            case Number number -> JsonKind.NUMBER;
            case JsonNumber number -> JsonKind.NUMBER;
            case Boolean bool -> bool ? JsonKind.TRUE : JsonKind.FALSE;
            case JsonNull jsonNull -> JsonKind.NULL;
            default -> throw new IllegalArgumentException("not a JSON value: " + node.getClass());
        };
    }

    @Override
    public int arrayLength(Object array) {
        return ((List<?>) array).size();
    }

    @Override
    public Maybe<Object> element(Object array, int index) {
        List<?> list = (List<?>) array;
        return index >= 0 && index < list.size() ? Maybe.some(node(list.get(index))) : Maybe.none();
    }

    @Override
    public int memberCount(Object object) {
        return ((Map<?, ?>) object).size();
    }

    @Override
    public Maybe<Object> member(Object object, JsonString name) {
        Map<?, ?> map = (Map<?, ?>) object;
        String key = JsonString.copyOf(name);
        return map.containsKey(key) ? Maybe.some(node(map.get(key))) : Maybe.none();
    }

    /// A `Map` cannot hold duplicate names.
    @Override
    public boolean hasDuplicate(Object object, JsonString name) {
        return false;
    }

    @Override
    public MemberCursor<Object> memberCursor(Object object) {
        Iterator<? extends Map.Entry<?, ?>> entries = ((Map<?, ?>) object).entrySet().iterator();
        return new MemberCursor<>() {
            private Map.Entry<?, ?> current = Map.entry("", NULL);

            @Override
            public boolean next() {
                if (!entries.hasNext()) {
                    return false;
                }
                current = entries.next();
                return true;
            }

            @Override
            public JsonString name() {
                return JsonString.of((String) current.getKey());
            }

            @Override
            public Object value() {
                return node(current.getValue());
            }
        };
    }

    @Override
    public JsonString stringValue(Object string) {
        return JsonString.of((String) string);
    }

    @Override
    public JsonNumber numberValue(Object number) {
        return switch (number) {
            case BigDecimal decimal -> JsonNumber.of(decimal);
            case BigInteger integer -> JsonNumber.of(integer);
            case Double d -> finite(d.doubleValue());
            case Float f -> finite(Double.parseDouble(f.toString()));
            case JsonNumber json -> json;
            case Number n -> JsonNumber.of(n.longValue());
            default -> throw new IllegalArgumentException("not a number: " + number);
        };
    }

    private static JsonNumber finite(double value) {
        return switch (JsonNumber.of(value)) {
            case Maybe.Some<JsonNumber>(JsonNumber number) -> number;
            case Maybe.None<JsonNumber>() -> throw new IllegalArgumentException("not a JSON number: " + value);
        };
    }

    @Override
    public Object string(JsonString value) {
        return JsonString.copyOf(value);
    }

    @Override
    public Maybe<Object> number(JsonNumber value) {
        return Maybe.some(value);
    }

    @Override
    public Object bool(boolean value) {
        return value;
    }

    @Override
    public Object nullValue() {
        return NULL;
    }

    @Override
    public Object array(List<Object> elements) {
        return new ArrayList<>(elements);
    }

    @Override
    public Object object(List<Property<Object>> members) {
        Map<String, Object> object = new LinkedHashMap<>();
        members.forEach(member -> object.put(JsonString.copyOf(member.name()), member.value()));
        return object;
    }

    private static Object node(@Nullable Object value) {
        return value == null ? NULL : value;
    }
    @Override
    public Maybe<Object> putMember(Object object, JsonString name, Object value) {
        Map<String, Object> map = map(object);
        String key = JsonString.copyOf(name);
        Maybe<Object> previous = map.containsKey(key) ? Maybe.some(node(map.get(key))) : Maybe.none();
        map.put(key, value);
        return previous;
    }

    @Override
    public Maybe<Object> removeMember(Object object, JsonString name) {
        Map<String, Object> map = map(object);
        String key = JsonString.copyOf(name);
        return map.containsKey(key) ? Maybe.some(node(map.remove(key))) : Maybe.none();
    }

    @Override
    public void insertElement(Object array, int index, Object value) {
        list(array).add(index, value);
    }

    @Override
    public Object setElement(Object array, int index, Object value) {
        return node(list(array).set(index, value));
    }

    @Override
    public Object removeElement(Object array, int index) {
        return node(list(array).remove(index));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object object) {
        return (Map<String, Object>) object;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object array) {
        return (List<Object>) array;
    }
}
