package ca.marcusdunn.jsonlens.model;

import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

/// A string with the value of a Java `String`.
record TextString(String value) implements JsonString {
    @Override
    public PrimitiveIterator.OfInt scalarValues() {
        return new CodePoints(value);
    }

    /// The code points of a `String`, in one object: no stream pipeline for each walk.
    ///
    /// An unpaired surrogate is a code point of its own, as `String.codePoints()` gives it.
    private static final class CodePoints implements PrimitiveIterator.OfInt {
        private final String text;
        private int index;

        CodePoints(String text) {
            this.text = text;
        }

        @Override
        public boolean hasNext() {
            return index < text.length();
        }

        @Override
        public int nextInt() {
            if (index >= text.length()) {
                throw new NoSuchElementException();
            }
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            return codePoint;
        }
    }
}
