package ca.marcusdunn.jsonlens.testkit;

import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import java.util.PrimitiveIterator;

/// Writes a value of any model as a JSON text (RFC 8259), so that a parser of another library can
/// read it. The suites use it to give each document to the parser of the model under test.
///
/// The writer uses recursion: the documents of the suites are not deep.
interface JsonText {

    static <N> String write(JsonModel<N> model, N value) {
        StringBuilder text = new StringBuilder();
        write(model, value, text);
        return text.toString();
    }

    private static <N> void write(JsonModel<N> model, N value, StringBuilder text) {
        switch (model.kind(value)) {
            case OBJECT -> {
                text.append('{');
                MemberCursor<N> cursor = model.memberCursor(value);
                String separator = "";
                while (cursor.next()) {
                    text.append(separator);
                    string(cursor.name(), text);
                    text.append(':');
                    write(model, cursor.value(), text);
                    separator = ",";
                }
                text.append('}');
            }
            case ARRAY -> {
                text.append('[');
                for (int i = 0; i < model.arrayLength(value); i++) {
                    text.append(i == 0 ? "" : ",");
                    switch (model.element(value, i)) {
                        case Maybe.Some<N>(N element) -> write(model, element, text);
                        case Maybe.None<N>() -> throw new IllegalArgumentException("the array has no element " + i);
                    }
                }
                text.append(']');
            }
            case STRING -> string(model.stringValue(value), text);
            case NUMBER -> text.append(model.numberValue(value).exactValue());
            case TRUE -> text.append("true");
            case FALSE -> text.append("false");
            default -> text.append("null"); // NULL
        }
    }

    private static void string(JsonString value, StringBuilder text) {
        text.append('"');
        for (PrimitiveIterator.OfInt values = value.scalarValues(); values.hasNext(); ) {
            int c = values.nextInt();
            if (c == '"' || c == '\\') {
                text.append('\\').appendCodePoint(c);
            } else if (c < 0x20 || (c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE)) {
                // A control character, or an unpaired surrogate: an escape.
                text.append(String.format("\\u%04x", c));
            } else {
                text.appendCodePoint(c);
            }
        }
        text.append('"');
    }
}
