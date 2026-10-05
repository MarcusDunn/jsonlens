package ca.marcusdunn.jsonlens.mapped;

import ca.marcusdunn.jsonlens.model.JsonDecimal;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/// A number in the bytes of a [MappedJson]. It reads its digits only when the caller asks for
/// the exact value.
///
/// @param data the bytes of the JSON text
/// @param start the offset of the first byte of the number
record MappedNumber(ByteBuffer data, int start) implements JsonNumber {

    @Override
    public JsonDecimal exactValue() {
        int end = start;
        while (end < data.limit() && isNumberByte(data.get(end))) {
            end++;
        }
        byte[] text = new byte[end - start];
        data.get(start, text);
        // The indexer has checked the grammar, so the text is a JSON number.
        return JsonDecimal.parse(new String(text, StandardCharsets.US_ASCII)).orElse(JsonDecimal.ZERO);
    }

    private static boolean isNumberByte(byte b) {
        return (b >= '0' && b <= '9') || b == '-' || b == '+' || b == '.' || b == 'e' || b == 'E';
    }
}
