package ca.marcusdunn.jsonlens.mapped;

import ca.marcusdunn.jsonlens.model.JsonString;
import java.nio.ByteBuffer;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

/// A string in the bytes of a [MappedJson]. It decodes UTF-8 and escapes while the caller reads it.
///
/// The indexer has checked the bytes, so the decoder does not check them again.
///
/// @param data the bytes of the JSON text
/// @param start the offset of the opening quote
record MappedString(ByteBuffer data, int start) implements JsonString {

    @Override
    public PrimitiveIterator.OfInt scalarValues() {
        return new ScalarValues(data, start + 1);
    }

    /// Decodes the scalar values from an offset to the closing quote.
    private static final class ScalarValues implements PrimitiveIterator.OfInt {
        private final ByteBuffer data;
        private int pos;

        ScalarValues(ByteBuffer data, int pos) {
            this.data = data;
            this.pos = pos;
        }

        @Override
        public boolean hasNext() {
            return at(pos) != '"';
        }

        @Override
        public int nextInt() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            int b = at(pos);
            if (b == '\\') {
                return escape();
            }
            if (b <= 0x7F) {
                pos++;
                return b;
            }
            int length;
            int value;
            if (b < 0xE0) {
                length = 2;
                value = b & 0x1F;
            } else if (b < 0xF0) {
                length = 3;
                value = b & 0x0F;
            } else {
                length = 4;
                value = b & 0x07;
            }
            for (int i = 1; i < length; i++) {
                value = (value << 6) | (at(pos + i) & 0x3F);
            }
            pos += length;
            return value;
        }

        private int escape() {
            int c = at(pos + 1);
            pos += 2;
            return switch (c) {
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> unicode();
                default -> c; // '"', '\\', or '/'
            };
        }

        /// The value of a unicode escape. A high surrogate escape followed by a low surrogate
        /// escape is one scalar value. An unpaired surrogate escape is its own value.
        private int unicode() {
            int unit = hex4(pos);
            pos += 4;
            if (Character.isHighSurrogate((char) unit) && at(pos) == '\\' && at(pos + 1) == 'u') {
                int low = hex4(pos + 2);
                if (Character.isLowSurrogate((char) low)) {
                    pos += 6;
                    return Character.toCodePoint((char) unit, (char) low);
                }
            }
            return unit;
        }

        private int hex4(int offset) {
            int value = 0;
            for (int i = 0; i < 4; i++) {
                value = (value << 4) | Character.digit(at(offset + i), 16);
            }
            return value;
        }

        private int at(int offset) {
            return data.get(offset) & 0xFF;
        }
    }
}
