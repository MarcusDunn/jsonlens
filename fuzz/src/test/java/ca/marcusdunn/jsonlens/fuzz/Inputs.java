package ca.marcusdunn.jsonlens.fuzz;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Helpers to make test inputs from fuzz data. */
final class Inputs {

    private Inputs() {}

    /** Decodes well-formed UTF-8, or returns null. */
    static String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /**
     * Splits the data at the first NUL byte into two UTF-8 strings. Returns null if there is no NUL
     * byte, if a part is not well-formed UTF-8, or if a part is longer than its limit.
     */
    static String[] split(byte[] data, int firstLimit, int secondLimit) {
        int nul = -1;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0) {
                nul = i;
                break;
            }
        }
        if (nul < 0) {
            return null;
        }
        String first = utf8(Arrays.copyOfRange(data, 0, nul));
        String second = utf8(Arrays.copyOfRange(data, nul + 1, data.length));
        if (first == null || second == null || first.length() > firstLimit || second.length() > secondLimit) {
            return null;
        }
        return new String[] {first, second};
    }
}
