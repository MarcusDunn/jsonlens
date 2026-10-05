package ca.marcusdunn.jsonlens.jackson;

import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import java.util.ArrayList;
import java.util.List;

/// A value of a [JacksonStream]. The children of an object or an array grow while the stream reads
/// them. Only [JacksonStream] makes nodes, and it gives the same instance for the same value each
/// time.
public final class JacksonStreamNode {

    private static final JsonString EMPTY = JsonString.of("");
    private static final JsonNumber ZERO = JsonNumber.of(0);

    final JacksonStream stream;
    final JsonKind kind;
    final JsonString string;
    final JsonNumber number;
    /// The children read so far, in order.
    final List<JacksonStreamNode> children = new ArrayList<>();
    /// The member names read so far, for an object.
    final List<JsonString> names = new ArrayList<>();
    /// Tells if the stream has read all children. A scalar is always complete.
    boolean complete;

    private JacksonStreamNode(JacksonStream stream, JsonKind kind, JsonString string, JsonNumber number, boolean complete) {
        this.stream = stream;
        this.kind = kind;
        this.string = string;
        this.number = number;
        this.complete = complete;
    }

    static JacksonStreamNode container(JacksonStream stream, JsonKind kind) {
        return new JacksonStreamNode(stream, kind, EMPTY, ZERO, false);
    }

    static JacksonStreamNode scalar(JacksonStream stream, JsonKind kind) {
        return new JacksonStreamNode(stream, kind, EMPTY, ZERO, true);
    }

    static JacksonStreamNode string(JacksonStream stream, JsonString value) {
        return new JacksonStreamNode(stream, JsonKind.STRING, value, ZERO, true);
    }

    static JacksonStreamNode number(JacksonStream stream, JsonNumber value) {
        return new JacksonStreamNode(stream, JsonKind.NUMBER, EMPTY, value, true);
    }
}
