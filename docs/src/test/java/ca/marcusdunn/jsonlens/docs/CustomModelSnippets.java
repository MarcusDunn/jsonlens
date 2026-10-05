package ca.marcusdunn.jsonlens.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.JsonDecimal;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator;
import ca.marcusdunn.jsonlens.path.evaluator.Node;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import org.junit.jupiter.api.Test;

/** A JsonModel for plain Java collections. */
class CustomModelSnippets {

    // @start region="model"
    // A model for values made of Map, List, String, Number, and Boolean.
    // JSON null is the sentinel JsonNull.NULL, because a node must not be Java null.
    enum JsonNull { NULL }

    static class CollectionsModel implements JsonModel<Object> {

        @Override
        public JsonKind kind(Object node) {
            return switch (node) {
                case Map<?, ?> map -> JsonKind.OBJECT;
                case List<?> list -> JsonKind.ARRAY;
                case String string -> JsonKind.STRING;
                case Number number -> JsonKind.NUMBER;
                case Boolean bool -> bool ? JsonKind.TRUE : JsonKind.FALSE;
                default -> JsonKind.NULL; // JsonNull.NULL
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
        public MemberCursor<Object> memberCursor(Object object) {
            Iterator<? extends Map.Entry<?, ?>> entries = ((Map<?, ?>) object).entrySet().iterator();
            return new MemberCursor<>() {
                private Map.Entry<?, ?> current = Map.entry("", JsonNull.NULL);

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
            // JsonString.of does not copy the String.
            return JsonString.of((String) string);
        }

        @Override
        public JsonNumber numberValue(Object number) {
            // The model chooses the representation. JsonNumber.of covers the common types.
            return switch (number) {
                case BigDecimal decimal -> JsonNumber.of(decimal);
                case BigInteger integer -> JsonNumber.of(integer);
                case Double d -> JsonNumber.of(d.doubleValue()).orElse(JsonNumber.of(0)); // NaN is not JSON
                default -> JsonNumber.of(((Number) number).longValue());
            };
        }

        // Optional: these three methods have defaults that walk the cursor. A Map does it faster.
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

        @Override
        public boolean hasDuplicate(Object object, JsonString name) {
            return false; // A Map cannot hold duplicate names.
        }

        // Replaces Java null with the sentinel.
        private static Object node(Object value) {
            return value == null ? JsonNull.NULL : value;
        }
    }
    // @end region="model"

    // @start region="compare-numbers"
    // A number type of the caller's representation.
    record LongValue(long value) implements JsonNumber {
        @Override
        public JsonDecimal exactValue() {
            return JsonDecimal.of(value);
        }
    }

    static final class LongValueModel extends CollectionsModel {

        @Override
        public JsonNumber numberValue(Object number) {
            return new LongValue(((Number) number).longValue());
        }

        @Override
        public int compareNumbers(JsonNumber a, JsonNumber b) {
            if (a instanceof LongValue x && b instanceof LongValue y) {
                return Long.compare(x.value(), y.value()); // without a conversion
            }
            return super.compareNumbers(a, b); // other numbers, for example number literals
        }
    }
    // @end region="compare-numbers"

    @Test
    void compareNumbersOfTheModel() {
        List<Object> root = List.of(1, 5, 10);
        JsonPathQuery query = JsonPathParser.standard().parse("$[?@ > $[1]]").orElse(null);
        List<Node<Object>> nodes = JsonPathEvaluator.standard()
                .evaluate(query, root, new LongValueModel())
                .orElse(List.of());
        assertEquals(List.of(10), nodes.stream().map(Node::value).toList());
        LongValueModel model = new LongValueModel();
        assertEquals(-1, model.compareNumbers(new LongValue(1), new LongValue(2)));
        assertEquals(0, model.compareNumbers(new LongValue(2), JsonNumber.of(new BigDecimal("2.0"))));
    }

    @Test
    void useTheModel() {
        // @start region="use-model"
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("name", "jsonlens");
        root.put("tags", Arrays.asList("json", null, "rfc9535"));

        JsonPathQuery query = JsonPathParser.standard().parse("$.tags[?@ == null]").orElse(null);
        List<Node<Object>> nodes = JsonPathEvaluator.standard()
                .evaluate(query, root, new CollectionsModel())
                .orElse(List.of());
        // nodes: [Node[value=NULL, path=$['tags'][1]]]
        // @end region="use-model"
        assertEquals(1, nodes.size());
        assertEquals("$['tags'][1]", nodes.getFirst().path().toString());
        assertEquals(JsonNull.NULL, nodes.getFirst().value());
    }
}
