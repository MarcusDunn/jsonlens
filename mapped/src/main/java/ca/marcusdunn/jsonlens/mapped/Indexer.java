package ca.marcusdunn.jsonlens.mapped;

import ca.marcusdunn.jsonlens.model.Result;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Makes the structural index of a JSON text in one pass (RFC 8259).
///
/// The index has one entry for each value, in document order: its kind and its slot. See
/// [MappedJson] for the layout. The pass does not decode strings or numbers.
///
/// The children of the open containers wait on one shared stack. When a container ends, its
/// entries move from the stack to the list of children. Then the list of children has the
/// containers in the order of their ends.
///
/// The pass uses an explicit stack, not recursion, so deep nesting cannot overflow the stack. A
/// method that fails records the error and returns false. The pass does not use exceptions.
final class Indexer {

    private static final int END = -1;
    private static final int NO_NAME = -1;

    /// What the pass expects next.
    private enum State {
        /// A value.
        VALUE,
        /// The first member or element of a container, or the end of the container.
        OPENED,
        /// A member (name and colon) or an element, after a comma or an opening bracket.
        MEMBER,
        /// A comma, the end of a container, or the end of the input.
        AFTER
    }

    /// A container that is not complete, and the size of the pending stack when it started.
    private record Frame(int value, boolean object, int mark) {}

    private final ByteBuffer data;
    private final int length;
    private int pos;
    private byte[] kinds = new byte[16];
    private final IntList slots = new IntList();
    private final IntList children = new IntList();
    /// The entries of the children of the open containers: the innermost container last.
    private final IntList pending = new IntList();
    private MappedJsonError.@Nullable InvalidJson error;

    private Indexer(ByteBuffer data) {
        this.data = data;
        this.length = data.limit();
    }

    /// Indexes the bytes from the position to the limit of a buffer.
    static Result<MappedJson, MappedJsonError> index(ByteBuffer bytes) {
        ByteBuffer data = bytes.slice();
        int invalid = firstInvalidUtf8(data);
        if (invalid != END) {
            return Result.err(new MappedJsonError.InvalidUtf8(invalid));
        }
        Indexer indexer = new Indexer(data);
        if (!indexer.run()) {
            // Each method that returns false records the error first.
            return Result.err(Objects.requireNonNull(indexer.error));
        }
        return Result.ok(new MappedJson(
                data,
                Arrays.copyOf(indexer.kinds, indexer.slots.size()),
                indexer.slots.toArray(),
                indexer.children.toArray()));
    }

    /// Returns the offset of the first byte that is not well-formed UTF-8, or END.
    private static int firstInvalidUtf8(ByteBuffer data) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer in = data.duplicate();
        CharBuffer out = CharBuffer.allocate(4096);
        while (true) {
            CoderResult result = decoder.decode(in, out, true);
            if (result.isError()) {
                return in.position();
            }
            if (result.isUnderflow()) {
                return END;
            }
            // The output is full. Only the validation is necessary, so discard the characters.
            out.clear();
        }
    }

    private boolean run() {
        Deque<Frame> stack = new ArrayDeque<>();
        State state = State.VALUE;
        int name = NO_NAME;
        skipWhitespace();
        while (true) {
            if (state == State.VALUE) {
                int c = at(pos);
                if (c == '{' || c == '[') {
                    boolean object = c == '{';
                    stack.push(new Frame(add(object ? MappedJson.OBJECT : MappedJson.ARRAY, stack, name), object, pending.size()));
                    pos++;
                    skipWhitespace();
                    state = State.OPENED;
                } else if (scalar(stack, name)) {
                    state = State.AFTER;
                } else {
                    return false;
                }
            } else if (state == State.OPENED) {
                Frame top = Objects.requireNonNull(stack.peek());
                if (at(pos) == close(top)) {
                    pos++;
                    finish(Objects.requireNonNull(stack.pop()));
                    state = State.AFTER;
                } else {
                    state = State.MEMBER;
                }
            } else if (state == State.MEMBER) {
                Frame top = Objects.requireNonNull(stack.peek());
                name = NO_NAME;
                if (top.object()) {
                    name = memberName();
                    if (name == NO_NAME) {
                        return false;
                    }
                }
                state = State.VALUE;
            } else {
                // State.AFTER
                skipWhitespace();
                Frame top = stack.peek();
                if (top == null) {
                    if (pos == length) {
                        return true;
                    }
                    fail(pos, "the end of the input");
                    return false;
                }
                int c = at(pos);
                if (c == ',') {
                    pos++;
                    skipWhitespace();
                    state = State.MEMBER;
                } else if (c == close(top)) {
                    pos++;
                    finish(Objects.requireNonNull(stack.pop()));
                } else {
                    fail(pos, top.object() ? "',' or '}'" : "',' or ']'");
                    return false;
                }
            }
        }
    }

    private static int close(Frame frame) {
        return frame.object() ? '}' : ']';
    }

    /// Adds a value at the position, and adds it to the children of the current container.
    private int add(byte kind, Deque<Frame> stack, int name) {
        int value = slots.size();
        if (value == kinds.length) {
            kinds = Arrays.copyOf(kinds, 2 * value);
        }
        kinds[value] = kind;
        // The offset of the value. A container replaces it with the start of its children.
        slots.add(pos);
        Frame parent = stack.peek();
        if (parent != null) {
            if (parent.object()) {
                pending.add(name);
            }
            pending.add(value);
        }
        return value;
    }

    /// Moves the children of a complete container from the pending stack to the list of children.
    private void finish(Frame frame) {
        slots.set(frame.value(), children.size());
        int entries = pending.size() - frame.mark();
        children.add(frame.object() ? entries / 2 : entries);
        for (int k = frame.mark(); k < pending.size(); k++) {
            children.add(pending.get(k));
        }
        pending.truncate(frame.mark());
    }

    /// A string, a number, or a literal at the position.
    private boolean scalar(Deque<Frame> stack, int name) {
        int c = at(pos);
        if (c == '"') {
            add(MappedJson.STRING, stack, name);
            return string();
        }
        if (c == '-' || isDigit(c)) {
            add(MappedJson.NUMBER, stack, name);
            return number();
        }
        if (c == 't') {
            add(MappedJson.TRUE, stack, name);
            return literal("true");
        }
        if (c == 'f') {
            add(MappedJson.FALSE, stack, name);
            return literal("false");
        }
        if (c == 'n') {
            add(MappedJson.NULL, stack, name);
            return literal("null");
        }
        fail(pos, "a value");
        return false;
    }

    /// A member name and the colon after it. Returns the offset of the name, or NO_NAME.
    private int memberName() {
        int start = pos;
        if (at(pos) != '"') {
            fail(pos, "a member name");
            return NO_NAME;
        }
        if (!string()) {
            return NO_NAME;
        }
        skipWhitespace();
        if (at(pos) != ':') {
            fail(pos, "':'");
            return NO_NAME;
        }
        pos++;
        skipWhitespace();
        return start;
    }

    /// A string at the position, from its opening quote to its closing quote.
    private boolean string() {
        pos++; // the opening quote
        while (true) {
            int c = at(pos);
            if (c == '"') {
                pos++;
                return true;
            }
            if (c == END) {
                fail(pos, "'\"'");
                return false;
            }
            if (c == '\\') {
                if (!escape()) {
                    return false;
                }
            } else if (c < 0x20) {
                fail(pos, "an escape for the control character");
                return false;
            } else {
                pos++;
            }
        }
    }

    /// An escape sequence at the position.
    private boolean escape() {
        return switch (at(pos + 1)) {
            case '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {
                pos += 2;
                yield true;
            }
            case 'u' -> unicodeEscape();
            default -> fail(pos + 1, "an escape character");
        };
    }

    /// A unicode escape at the position: a backslash, "u", and four hexadecimal digits.
    private boolean unicodeEscape() {
        for (int i = 2; i < 6; i++) {
            if (!isHex(at(pos + i))) {
                fail(pos + i, "a hexadecimal digit");
                return false;
            }
        }
        pos += 6;
        return true;
    }

    /// A number at the position: `-? (0 / [1-9][0-9]*) ("." [0-9]+)? ([eE] [-+]? [0-9]+)?`.
    private boolean number() {
        if (at(pos) == '-') {
            pos++;
        }
        if (at(pos) == '0') {
            pos++;
        } else if (!digits()) {
            fail(pos, "a digit");
            return false;
        }
        if (at(pos) == '.') {
            pos++;
            if (!digits()) {
                fail(pos, "a digit");
                return false;
            }
        }
        int c = at(pos);
        if (c == 'e' || c == 'E') {
            pos++;
            c = at(pos);
            if (c == '+' || c == '-') {
                pos++;
            }
            if (!digits()) {
                fail(pos, "a digit");
                return false;
            }
        }
        return true;
    }

    /// One or more digits at the position.
    private boolean digits() {
        if (!isDigit(at(pos))) {
            return false;
        }
        while (isDigit(at(pos))) {
            pos++;
        }
        return true;
    }

    private boolean literal(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (at(pos + i) != text.charAt(i)) {
                fail(pos + i, "'" + text + "'");
                return false;
            }
        }
        pos += text.length();
        return true;
    }

    private void skipWhitespace() {
        while (true) {
            int c = at(pos);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            pos++;
        }
    }

    /// Returns the byte at an offset as a value from 0 to 255, or END after the last byte.
    private int at(int offset) {
        return offset < length ? data.get(offset) & 0xFF : END;
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHex(int c) {
        return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /// Records the error. Always returns false. Each caller returns at once.
    private boolean fail(int offset, String expected) {
        error = new MappedJsonError.InvalidJson(offset, expected);
        return false;
    }
}
