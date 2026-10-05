package ca.marcusdunn.jsonlens.jackson;

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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.POJONode;

/// A [JsonModel] for Jackson 3 [JsonNode] values.
///
/// The model reads the nodes of the tree directly. It does not copy them, so each result
/// Normalized Path leads to a node of your own tree. The model has
/// no state: use [#INSTANCE].
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.QuickStartSnippets region=quick-start}
///
/// ## Exact numbers
///
/// By default, Jackson reads a JSON number with a fraction or an exponent as a
/// `double`. Such a number is not exact, and a number outside the range of
/// `double` becomes infinite. To keep all numbers exact, enable
/// `DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS` on the mapper:
///
/// {@snippet :
/// JsonMapper mapper = JsonMapper.builder()
///         .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS) // @highlight
///         .build();
/// }
///
/// ## Changes
///
/// The model also implements [JsonEditor]: it changes `ObjectNode` and `ArrayNode` values in
/// place.
///
/// ## Member order
///
/// Jackson keeps the members of an object in document order, so the evaluator gives object members
/// in document order.
///
/// ## Nodes that are not JSON
///
/// A Jackson tree can hold values that JSON cannot represent. The model classifies them as
/// follows:
///
/// - A NaN or infinite `double` or `float` is a [JsonKind#STRING] with
///   the text `"NaN"`, `"Infinity"`, or `"-Infinity"`.
/// - A binary node is a [JsonKind#STRING] with the Base64 text that Jackson writes for
///   it.
/// - A POJO node is a [JsonKind#STRING] with the text of `String.valueOf` of the
///   POJO. A POJO node that holds `null` is a [JsonKind#NULL].
/// - A missing node is a [JsonKind#NULL].
public final class JacksonJsonModel implements JsonModel<JsonNode>, JsonFactory<JsonNode>, JsonEditor<JsonNode> {

    /// The model. It has no state.
    public static final JacksonJsonModel INSTANCE = new JacksonJsonModel();

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private JacksonJsonModel() {}

    @Override
    public JsonKind kind(JsonNode node) {
        return switch (node.getNodeType()) {
            case OBJECT -> JsonKind.OBJECT;
            case ARRAY -> JsonKind.ARRAY;
            case STRING, BINARY -> JsonKind.STRING;
            case NUMBER -> isFinite(node) ? JsonKind.NUMBER : JsonKind.STRING;
            case BOOLEAN -> node.booleanValue() ? JsonKind.TRUE : JsonKind.FALSE;
            case NULL, MISSING -> JsonKind.NULL;
            case POJO -> ((POJONode) node).getPojo() == null ? JsonKind.NULL : JsonKind.STRING;
        };
    }

    @Override
    public int arrayLength(JsonNode array) {
        return array.size();
    }

    @Override
    public Maybe<JsonNode> element(JsonNode array, int index) {
        return index >= 0 && index < array.size() ? Maybe.some(array.get(index)) : Maybe.none();
    }

    @Override
    public int memberCount(JsonNode object) {
        return object.size();
    }

    @Override
    public Maybe<JsonNode> member(JsonNode object, JsonString name) {
        JsonNode value = object.get(JsonString.copyOf(name));
        return value == null ? Maybe.none() : Maybe.some(value);
    }

    /// An `ObjectNode` is a map, so it cannot hold duplicate names.
    @Override
    public boolean hasDuplicate(JsonNode object, JsonString name) {
        return false;
    }

    @Override
    public MemberCursor<JsonNode> memberCursor(JsonNode object) {
        return new EntryCursor(object.properties().iterator());
    }

    /// A cursor over the entries of an `ObjectNode`: no stream and no `Property` for each member.
    private static final class EntryCursor implements MemberCursor<JsonNode> {
        private static final Map.Entry<String, JsonNode> BEFORE_FIRST = Map.entry("", NODES.nullNode());
        private final Iterator<Map.Entry<String, JsonNode>> entries;
        private Map.Entry<String, JsonNode> current = BEFORE_FIRST;

        EntryCursor(Iterator<Map.Entry<String, JsonNode>> entries) {
            this.entries = entries;
        }

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
            return JsonString.of(current.getKey());
        }

        @Override
        public JsonNode value() {
            return current.getValue();
        }
    }

    @Override
    public JsonString stringValue(JsonNode string) {
        return JsonString.of(switch (string.getNodeType()) {
            case NUMBER -> Double.toString(string.doubleValue());
            case POJO -> pojoText(string);
            default -> string.asString();
        });
    }

    @Override
    public JsonNumber numberValue(JsonNode number) {
        if (number.isInt() || number.isLong() || number.isShort()) {
            return JsonNumber.of(number.longValue());
        }
        if (number.isDouble() || number.isFloat()) {
            // kind() gives STRING for NaN and the infinities, so the value is finite here.
            return JsonNumber.of(number.doubleValue()).orElse(JsonNumber.of(0));
        }
        if (number.isBigInteger()) {
            return JsonNumber.of(number.bigIntegerValue());
        }
        return JsonNumber.of(number.decimalValue());
    }

    @Override
    public JsonNode string(JsonString value) {
        return NODES.stringNode(JsonString.copyOf(value));
    }

    @Override
    public Maybe<JsonNode> number(JsonNumber value) {
        // A BigDecimal cannot hold an exponent outside the int range.
        return value.exactValue().toBigDecimal().map(NODES::numberNode);
    }

    @Override
    public JsonNode bool(boolean value) {
        return NODES.booleanNode(value);
    }

    @Override
    public JsonNode nullValue() {
        return NODES.nullNode();
    }

    @Override
    public JsonNode array(List<JsonNode> elements) {
        return NODES.arrayNode().addAll(elements);
    }

    @Override
    public JsonNode object(List<Property<JsonNode>> members) {
        ObjectNode object = NODES.objectNode();
        members.forEach(member -> object.set(JsonString.copyOf(member.name()), member.value()));
        return object;
    }

    @Override
    public Maybe<JsonNode> putMember(JsonNode object, JsonString name, JsonNode value) {
        JsonNode previous = ((ObjectNode) object).replace(JsonString.copyOf(name), value);
        return previous == null ? Maybe.none() : Maybe.some(previous);
    }

    @Override
    public Maybe<JsonNode> removeMember(JsonNode object, JsonString name) {
        JsonNode removed = ((ObjectNode) object).remove(JsonString.copyOf(name));
        return removed == null ? Maybe.none() : Maybe.some(removed);
    }

    @Override
    public void insertElement(JsonNode array, int index, JsonNode value) {
        ((ArrayNode) array).insert(index, value);
    }

    @Override
    public JsonNode setElement(JsonNode array, int index, JsonNode value) {
        JsonNode previous = array.get(index);
        ((ArrayNode) array).set(index, value);
        return previous;
    }

    @Override
    public JsonNode removeElement(JsonNode array, int index) {
        return ((ArrayNode) array).remove(index);
    }


    private static boolean isFinite(JsonNode number) {
        return !(number.isDouble() || number.isFloat()) || Double.isFinite(number.doubleValue());
    }

    private static String pojoText(JsonNode pojo) {
        return String.valueOf(((POJONode) pojo).getPojo());
    }
}
