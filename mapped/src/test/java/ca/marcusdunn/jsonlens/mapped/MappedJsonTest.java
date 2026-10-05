package ca.marcusdunn.jsonlens.mapped;

import static ca.marcusdunn.jsonlens.mapped.Json.bytes;
import static ca.marcusdunn.jsonlens.mapped.Json.error;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.model.JsonDecimal;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Property;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MappedJsonTest {

    private static <T> T present(Maybe<T> maybe) {
        return maybe.orElseGet(() -> fail("expected a present value"));
    }

    private static JsonModel<MappedNode> model(MappedJson json) {
        return json.model();
    }

    private static String string(String text) {
        MappedJson json = Json.of(text);
        return JsonString.copyOf(json.model().stringValue(json.root()));
    }

    private static MappedJsonError.InvalidJson invalid(int offset, String expected) {
        return new MappedJsonError.InvalidJson(offset, expected);
    }

    @Test
    @Requirement("lib/mapped-rfc8259")
    void acceptsJsonTexts() {
        for (String text : List.of(
                "0", "-0", "1.5e+3", "1E-2", "-12.5e3", "10", "19", "true", "false", "null", "\"a\"", "[]", "{}",
                " \t\n\r[ 1 , [ ] , { } ] \t\n\r", "{\"a\":1,\"b\":[true,false,null]}", "\"\\\"\\\\\\/\\b\\f\\n\\r\\t\"",
                "\"\\u09af\\uAF09\\uabcd\\uEF00\"", "\"\u00e9\u4e2d\ud83d\ude00 \u007f\"")) {
            Json.of(text);
        }
    }

    @Test
    @Requirement("lib/mapped-rfc8259")
    @Requirement("lib/mapped-errors-as-values")
    void rejectsTextsThatAreNotJson() {
        assertEquals(invalid(0, "a value"), error(""));
        assertEquals(invalid(3, "a value"), error("   "));
        assertEquals(invalid(0, "a value"), error("x"));
        assertEquals(invalid(0, "a value"), error("\f1"));
        assertEquals(invalid(1, "a value"), error("[/]"));
        assertEquals(invalid(1, "a value"), error("[:]"));
        assertEquals(invalid(1, "the end of the input"), error("01"));
        assertEquals(invalid(2, "the end of the input"), error("1 2"));
        assertEquals(invalid(1, "the end of the input"), error("1:"));
        assertEquals(invalid(1, "a digit"), error("-"));
        assertEquals(invalid(1, "a digit"), error("-a"));
        assertEquals(invalid(2, "a digit"), error("1."));
        assertEquals(invalid(2, "a digit"), error("1.a"));
        assertEquals(invalid(2, "a digit"), error("1e"));
        assertEquals(invalid(3, "a digit"), error("1e+"));
        assertEquals(invalid(3, "a digit"), error("1E-x"));
        assertEquals(invalid(3, "'true'"), error("tru"));
        assertEquals(invalid(3, "'true'"), error("trux"));
        assertEquals(invalid(1, "'false'"), error("folse"));
        assertEquals(invalid(3, "'null'"), error("nul"));
        assertEquals(invalid(3, "',' or ']'"), error("[1 2]"));
        assertEquals(invalid(2, "',' or ']'"), error("[1"));
        assertEquals(invalid(3, "a value"), error("[1,]"));
        assertEquals(invalid(1, "a value"), error("["));
        assertEquals(invalid(1, "a member name"), error("{"));
        assertEquals(invalid(1, "a member name"), error("{1:2}"));
        assertEquals(invalid(5, "':'"), error("{\"a\" 1}"));
        assertEquals(invalid(5, "a value"), error("{\"a\":}"));
        assertEquals(invalid(7, "',' or '}'"), error("{\"a\":1 \"b\"}"));
        assertEquals(invalid(7, "a member name"), error("{\"a\":1,}"));
        assertEquals(invalid(3, "'\"'"), error("{\"a"));
        assertEquals(invalid(1, "a value"), error("[}"));
        assertEquals(invalid(6, "',' or '}'"), error("{\"a\":1]"));
    }

    @Test
    @Requirement("lib/mapped-rfc8259")
    void rejectsStringsThatAreNotJson() {
        assertEquals(invalid(4, "'\"'"), error("\"abc"));
        assertEquals(invalid(2, "an escape for the control character"), error("\"a\u0001\""));
        assertEquals(invalid(1, "an escape for the control character"), error("\"\u001f\""));
        assertEquals(invalid(2, "an escape character"), error("\"\\x\""));
        assertEquals(invalid(2, "an escape character"), error("\"\\"));
        for (char bad : new char[] {'/', ':', '@', 'G', '`', 'g'}) {
            assertEquals(invalid(5, "a hexadecimal digit"), error("\"\\u12" + bad + "4\""), String.valueOf(bad));
        }
        assertEquals(invalid(5, "a hexadecimal digit"), error("\"\\u12"));
        assertEquals(invalid(3, "a hexadecimal digit"), error("\"\\uX234\""));
    }

    @Test
    @Requirement("lib/mapped-rfc8259")
    @Requirement("lib/mapped-errors-as-values")
    void rejectsBytesThatAreNotUtf8() {
        int q = '"';
        assertEquals(new MappedJsonError.InvalidUtf8(1), error(bytes(q, 0xC3, 0x28, q)));
        assertEquals(new MappedJsonError.InvalidUtf8(1), error(bytes(q, 0x80, q)));
        assertEquals(new MappedJsonError.InvalidUtf8(1), error(bytes(q, 0xC0, 0xAF, q)));
        assertEquals(new MappedJsonError.InvalidUtf8(1), error(bytes(q, 0xED, 0xA0, 0x80, q)));
        assertEquals(new MappedJsonError.InvalidUtf8(1), error(bytes(q, 0xF4, 0x90, 0x80, 0x80, q)));
        assertEquals(new MappedJsonError.InvalidUtf8(1), error(bytes(q, 0xE2, 0x82)));
        assertEquals(new MappedJsonError.InvalidUtf8(1), error(bytes(q, 0xFF, q)));
        // A valid text longer than the decoder buffer of the check.
        String longText = "\"" + "\u00e9".repeat(10_000) + "\"";
        assertEquals(10_000, JsonString.length(model(Json.of(longText)).stringValue(Json.of(longText).root())));
        assertEquals(new MappedJsonError.InvalidUtf8(20_002), error(append(longText.getBytes(StandardCharsets.UTF_8), 0xFF)));
    }

    private static byte[] append(byte[] prefix, int last) {
        byte[] bytes = java.util.Arrays.copyOf(prefix, prefix.length + 1);
        bytes[prefix.length] = (byte) last;
        return bytes;
    }

    @Test
    @Requirement("lib/mapped-deep-nesting")
    void deepNestingDoesNotOverflowTheStack() {
        int depth = 100_000;
        MappedJson arrays = Json.of("[".repeat(depth) + "1" + "]".repeat(depth));
        MappedNode node = arrays.root();
        for (int i = 0; i < depth; i++) {
            assertEquals(1, arrays.model().arrayLength(node));
            node = present(arrays.model().element(node, 0));
        }
        assertEquals(JsonKind.NUMBER, arrays.model().kind(node));
        MappedJson objects = Json.of("{\"a\":".repeat(depth) + "null" + "}".repeat(depth));
        node = objects.root();
        for (int i = 0; i < depth; i++) {
            node = present(objects.model().member(node, JsonString.of("a")));
        }
        assertEquals(JsonKind.NULL, objects.model().kind(node));
    }

    @Test
    @Requirement("lib/mapped-no-copies")
    void decodesEscapes() {
        assertEquals("\"\\/\b\f\n\r\t", string("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\""));
        assertEquals("\u00e9\uabcd", string("\"\\u00e9\\uABCD\""));
        assertEquals("\ud83d\ude00", string("\"\\uD83D\\uDE00\""));
        assertEquals("\ud83d\ude00", string("\"\\ud83d\\ude00\""));
        // Unpaired surrogate escapes are their own values.
        assertEquals("\ud83d", string("\"\\uD83D\""));
        assertEquals("\ud83dA", string("\"\\uD83D\\u0041\""));
        assertEquals("\ud83d\n", string("\"\\uD83D\\n\""));
        assertEquals("\ud83dx", string("\"\\uD83Dx\""));
        assertEquals("\ude00", string("\"\\uDE00\""));
        assertEquals("\ud83d\ud83d", string("\"\\uD83D\\uD83D\""));
    }

    @Test
    @Requirement("lib/mapped-no-copies")
    void decodesUtf8Boundaries() {
        for (String value : List.of("\u007f", "\u0080", "\u07ff", "\u0800", "\uffff", "\ud800\udc00", "\udbff\udfff",
                "a\u00e9\u4e2d\ud83d\ude00z")) {
            assertEquals(value, string("\"" + value + "\""), value);
        }
    }

    @Test
    void scalarValuesEndAtTheClosingQuote() {
        MappedJson json = Json.of("[\"ab\",\"\"]");
        PrimitiveIterator.OfInt values = json.model().stringValue(present(json.model().element(json.root(), 0))).scalarValues();
        assertEquals('a', values.nextInt());
        assertEquals('b', values.nextInt());
        assertFalse(values.hasNext());
        assertThrows(NoSuchElementException.class, values::nextInt);
        assertFalse(json.model().stringValue(present(json.model().element(json.root(), 1))).scalarValues().hasNext());
    }

    @Test
    @Requirement("lib/mapped-number-exact")
    void numbers() {
        MappedJson json = Json.of("[1e99999999999, -12.5e-3, 10, 19, 1E+2, 0.25, 12]");
        JsonModel<MappedNode> model = json.model();
        assertEquals(JsonKind.NUMBER, model.kind(present(model.element(json.root(), 0))));
        assertEquals(JsonDecimal.parse("1e99999999999"), Maybe.some(model.numberValue(present(model.element(json.root(), 0))).exactValue()));
        String[] expected = {"-0.0125", "10", "19", "100", "0.25", "12"};
        for (int i = 0; i < expected.length; i++) {
            JsonDecimal value = model.numberValue(present(model.element(json.root(), i + 1))).exactValue();
            assertEquals(JsonDecimal.of(new BigDecimal(expected[i])), value, expected[i]);
        }
        // A number at the end of the bytes.
        MappedJson last = Json.of("42");
        assertEquals(JsonDecimal.of(42), last.model().numberValue(last.root()).exactValue());
    }

    @Test
    @Requirement("lib/mapped-no-copies")
    void readsTheBytesWithoutACopy() {
        byte[] bytes = "{\"name\":\"abc\",\"n\":12}".getBytes(StandardCharsets.UTF_8);
        MappedJson json = Json.of(bytes);
        JsonModel<MappedNode> model = json.model();
        // Change the bytes after the index: the model reads the new bytes. (A caller must not do this.)
        bytes[9] = 'x';
        bytes[19] = '3';
        bytes[2] = 'N';
        assertEquals("xbc", JsonString.copyOf(model.stringValue(present(model.member(json.root(), JsonString.of("Name"))))));
        assertEquals(JsonDecimal.of(13), model.numberValue(present(model.member(json.root(), JsonString.of("n")))).exactValue());
        assertTrue(model.member(json.root(), JsonString.of("name")).isNone());
    }

    @Test
    @Requirement("lib/mapped-no-copies")
    void usesTheBytesFromThePositionToTheLimit() {
        ByteBuffer buffer = ByteBuffer.wrap(" xx[1]yy".getBytes(StandardCharsets.UTF_8), 3, 3);
        MappedJson json = switch (MappedJson.of(buffer)) {
            case Result.Ok<MappedJson, MappedJsonError>(MappedJson value) -> value;
            case Result.Err<MappedJson, MappedJsonError>(MappedJsonError e) -> fail(e.message());
        };
        assertEquals(1, json.model().arrayLength(json.root()));
        assertEquals(3, buffer.position());
        assertEquals(6, buffer.limit());
    }

    @Test
    void memberNamesMatchByScalarValues() {
        MappedJson json = Json.of("{\"\\u0061\":1,\"\u00e9\":2,\"\\u00e9x\":3,\"A\":4,\"\":5}");
        JsonModel<MappedNode> model = json.model();
        MappedNode root = json.root();
        assertEquals(JsonKind.NUMBER, model.kind(present(model.member(root, JsonString.of("a")))));
        assertEquals("2", text(json, present(model.member(root, JsonString.of("\u00e9")))));
        assertEquals("3", text(json, present(model.member(root, JsonString.of("\u00e9x")))));
        assertEquals("4", text(json, present(model.member(root, JsonString.of("A")))));
        assertEquals("5", text(json, present(model.member(root, JsonString.of("")))));
        assertTrue(model.member(root, JsonString.of("e\u0301")).isNone());
        assertTrue(model.member(root, JsonString.of("ab")).isNone());
        assertEquals(List.of("a", "\u00e9", "\u00e9x", "A", ""), model.members(root).map(property -> JsonString.copyOf(property.name())).toList());
    }

    private static String text(MappedJson json, MappedNode number) {
        return json.model().numberValue(number).exactValue().toString();
    }

    @Test
    @Requirement("lib/mapped-duplicate-names")
    void duplicateNames() {
        MappedJson json = Json.of("{\"a\":1,\"b\":2,\"a\":3}");
        JsonModel<MappedNode> model = json.model();
        assertEquals("1", text(json, present(model.member(json.root(), JsonString.of("a")))));
        assertEquals(3, model.memberCount(json.root()));
        assertEquals(List.of("a", "b", "a"), model.members(json.root()).map(property -> JsonString.copyOf(property.name())).toList());
        assertTrue(model.hasDuplicate(json.root(), JsonString.of("a")));
        assertFalse(model.hasDuplicate(json.root(), JsonString.of("b")));
        assertFalse(model.hasDuplicate(json.root(), JsonString.of("c")));
        assertFalse(model.hasDuplicate(present(model.member(Json.of("{\"x\":{}}").root(), JsonString.of("x"))), JsonString.of("a")));
    }

    @Test
    void containersWithExactlyFullLists() {
        // The internal lists start with room for 8 values.
        MappedJson json = Json.of("[[1,2,3,4,5,6,7,8],{\"a\":1,\"b\":2,\"c\":3,\"d\":4,\"e\":5,\"f\":6,\"g\":7,\"h\":8},[]]");
        JsonModel<MappedNode> model = json.model();
        assertEquals(8, model.arrayLength(present(model.element(json.root(), 0))));
        assertEquals(8, model.memberCount(present(model.element(json.root(), 1))));
        assertEquals(0, model.arrayLength(present(model.element(json.root(), 2))));
        assertEquals("8", text(json, present(model.member(present(model.element(json.root(), 1)), JsonString.of("h")))));
    }

    @Test
    void largeContainers() {
        String elements = "1,".repeat(999) + "1";
        MappedJson json = Json.of("[" + elements + ",[" + elements + "]]");
        assertEquals(1001, json.model().arrayLength(json.root()));
        MappedNode inner = present(json.model().element(json.root(), 1000));
        assertEquals(1000, json.model().arrayLength(inner));
        assertEquals(inner, present(json.model().element(json.root(), 1000)));
    }

    @Test
    void manyValuesFillMoreThanOneBlockOfTheIndex() {
        int count = 40_000;
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            text.append(i == 0 ? "" : ",").append("{\"n\":").append(i).append(",\"e\":[],\"o\":{}}");
        }
        MappedJson json = Json.of(text.append("]").toString());
        JsonModel<MappedNode> model = json.model();
        assertEquals(count, model.arrayLength(json.root()));
        for (int i : new int[] {0, 16_383, 16_384, count - 1}) {
            MappedNode item = present(model.element(json.root(), i));
            assertEquals(3, model.memberCount(item));
            assertEquals(String.valueOf(i), text(json, present(model.member(item, JsonString.of("n")))));
            assertEquals(0, model.arrayLength(present(model.member(item, JsonString.of("e")))));
            assertEquals(0, model.memberCount(present(model.member(item, JsonString.of("o")))));
        }
    }

    @Test
    @Requirement("lib/model-equal-nodes")
    void nodesAreEqualForTheSameValueInTheSameText() {
        MappedJson json = Json.of("[1, 1]");
        MappedJson other = Json.of("[1, 1]");
        JsonModel<MappedNode> model = json.model();
        MappedNode first = present(model.element(json.root(), 0));
        assertEquals(json.root(), json.root());
        assertEquals(json.root().hashCode(), json.root().hashCode());
        assertEquals(first, present(model.element(json.root(), 0)));
        // Equal values at two positions, or in two texts, are different nodes.
        assertNotEquals(first, present(model.element(json.root(), 1)));
        assertNotEquals(json.root(), other.root());
        assertNotEquals(json.root(), (Object) "[1, 1]");
        assertEquals(3, java.util.Set.of(json.root().hashCode(), first.hashCode(),
                present(model.element(json.root(), 1)).hashCode()).size());
    }

    @Test
    @Requirement("lib/mapped-errors-as-values")
    void opensFiles(@TempDir Path directory) throws IOException {
        Path file = Files.writeString(directory.resolve("data.json"), "{\"a\":[1,2]}");
        MappedJson json = switch (MappedJson.open(file)) {
            case Result.Ok<MappedJson, MappedJsonError>(MappedJson value) -> value;
            case Result.Err<MappedJson, MappedJsonError>(MappedJsonError e) -> fail(e.message());
        };
        assertEquals(2, json.model().arrayLength(present(json.model().member(json.root(), JsonString.of("a")))));
        Path empty = Files.writeString(directory.resolve("empty.json"), "");
        assertEquals(Result.err(invalid(0, "a value")), MappedJson.open(empty));
        MappedJsonError missing = ((Result.Err<MappedJson, MappedJsonError>) MappedJson.open(directory.resolve("missing.json"))).error();
        assertInstanceOf(MappedJsonError.IoFailure.class, missing);
        assertTrue(missing.message().contains("missing.json"), missing.message());
        assertInstanceOf(MappedJsonError.IoFailure.class,
                ((Result.Err<MappedJson, MappedJsonError>) MappedJson.open(directory)).error());
        // An error after the file is open, also when the channel closes.
        assertEquals(Result.err(new MappedJsonError.IoFailure("java.io.IOException: the device failed")),
                MappedJson.map(new FailingChannel(), MappedJson.MAX_SIZE));
        // The channel fails when it closes. That does not change the result.
        FileChannel closeFails = new FailingChannel() {
            @Override
            public long size() {
                return 10;
            }
        };
        assertEquals(Result.err(new MappedJsonError.FileTooLarge(10, 1)), MappedJson.map(closeFails, 1));
    }

    @Test
    @Requirement("lib/mapped-size-limit")
    void limitsTheFileSize(@TempDir Path directory) throws IOException {
        assertEquals(Integer.MAX_VALUE, MappedJson.MAX_SIZE);
        Path file = Files.writeString(directory.resolve("four.json"), "1234");
        assertEquals(Result.err(new MappedJsonError.FileTooLarge(4, 3)), MappedJson.map(FileChannel.open(file), 3));
        FileChannel channel = FileChannel.open(file);
        assertTrue(MappedJson.map(channel, 4).isOk());
        // The channel is closed, and the mapping stays valid.
        assertFalse(channel.isOpen());
        FileChannel tooLarge = FileChannel.open(file);
        MappedJson.map(tooLarge, 3);
        assertFalse(tooLarge.isOpen());
    }

    @Test
    void messages() {
        assertEquals("Expected ':' at byte 5.", invalid(5, "':'").message());
        assertEquals("The bytes at byte 2 are not well-formed UTF-8.", new MappedJsonError.InvalidUtf8(2).message());
        assertEquals("The file has 4 bytes. The maximum is 3 bytes.", new MappedJsonError.FileTooLarge(4, 3).message());
        assertEquals("The file cannot be read: gone", new MappedJsonError.IoFailure("gone").message());
    }
    @Test
    @Requirement("lib/mapped-no-copies")
    void memberNamesAreNotDecoded() {
        MappedJson json = Json.of("{\"a\\u00e9\":1,\"b\":2}");
        List<Property<MappedNode>> members = json.model().members(json.root()).toList();
        assertInstanceOf(MappedString.class, members.get(0).name());
        assertEquals(new Property<>(JsonString.of("aé"), members.get(0).value()), members.get(0));
        assertEquals("aé", JsonString.copyOf(members.get(0).name()));
        assertEquals("aé".hashCode(), JsonString.hash(members.get(0).name()));
    }
}
