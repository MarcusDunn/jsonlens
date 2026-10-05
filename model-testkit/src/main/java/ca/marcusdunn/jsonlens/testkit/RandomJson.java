package ca.marcusdunn.jsonlens.testkit;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/// Makes random JSON texts (RFC 8259) from a seed. The same seed gives the same texts.
///
/// The texts have the cases that models often get wrong: escapes, non-ASCII characters and
/// surrogate pairs, the empty name, numbers with an exponent or a fraction, integers larger than a
/// `long`, equal numbers in different forms such as `100` and `1E+2`, and empty and nested
/// containers. The member names of each object are unique.
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.TestkitSnippets region=random}
public final class RandomJson {

    private static final List<String> STRINGS = List.of(
            "", "a", "é", "中", "😀", " ", "/", "\\n", "\\\"", "\\\\", "\\u0000", "\\u00e9", "\\ud83d\\ude00", "\\t");
    /// Each name has a different value, also the names with an escape.
    private static final List<String> NAMES = List.of("a", "b", "c", "é", "", "a b", "\\u00e9x", "😀", "k1", "k2");
    private static final List<String> NUMBERS = List.of(
            "0", "-0", "1", "-7", "0.5", "-12.5e-3", "100", "1E+2", "2.50", "9007199254740992", "9007199254740993",
            "123456789012345678901234567890", "1e400", "-1E-400");
    private static final int MAX_DEPTH = 4;
    private static final int MAX_CHILDREN = 5;

    private final SplittableRandom random;

    private RandomJson(long seed) {
        this.random = new SplittableRandom(seed);
    }

    /// Returns a generator with a seed.
    ///
    /// @param seed the seed
    /// @return a new generator
    public static RandomJson withSeed(long seed) {
        return new RandomJson(seed);
    }

    /// Makes the next JSON text. Its root is an object or an array.
    ///
    /// @return a JSON text
    public String next() {
        StringBuilder text = new StringBuilder();
        container(MAX_DEPTH, text);
        return text.toString();
    }

    private void value(int depth, StringBuilder text) {
        int choice = random.nextInt(depth == 0 ? 4 : 6);
        switch (choice) {
            case 0 -> string(text);
            case 1 -> text.append(pick(NUMBERS));
            case 2 -> text.append(random.nextLong());
            case 3 -> text.append(pick(List.of("true", "false", "null")));
            default -> container(depth - 1, text);
        }
    }

    private void container(int depth, StringBuilder text) {
        int children = random.nextInt(MAX_CHILDREN + 1);
        if (random.nextBoolean()) {
            text.append('[');
            for (int i = 0; i < children; i++) {
                text.append(i == 0 ? "" : ",");
                value(depth, text);
            }
            text.append(']');
        } else {
            List<String> names = new ArrayList<>(NAMES);
            text.append('{');
            for (int i = 0; i < children; i++) {
                text.append(i == 0 ? "" : ",").append('"').append(names.remove(random.nextInt(names.size()))).append("\":");
                value(depth, text);
            }
            text.append('}');
        }
    }

    private void string(StringBuilder text) {
        text.append('"');
        for (int i = random.nextInt(4); i > 0; i--) {
            text.append(pick(STRINGS));
        }
        text.append('"');
    }

    private String pick(List<String> choices) {
        return choices.get(random.nextInt(choices.size()));
    }
}
