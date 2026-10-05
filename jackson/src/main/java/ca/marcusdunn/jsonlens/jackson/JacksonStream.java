package ca.marcusdunn.jsonlens.jackson;

import ca.marcusdunn.jsonlens.model.JsonDecimal;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.MemberCursor;
import ca.marcusdunn.jsonlens.model.Result;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

/// A JSON document that a Jackson streaming [JsonParser] reads on demand, with no tree.
///
/// The document reads tokens only when a query needs them. A query such as `$.a` reads only up
/// to the first member `a`, and `$.items[0].id` never reads `items[1]`. The document keeps each
/// value that it has read, so a query can visit a value again. The input can be any format that a
/// Jackson parser reads, for example JSON, CBOR, or Smile.
///
/// {@snippet class=ca.marcusdunn.jsonlens.docs.JacksonStreamSnippets region=stream}
///
/// ## Errors during a query
///
/// A [JsonModel] method cannot report an error, but a parser can fail in the middle of the
/// input. Then the document ends each value that is still open at the failure, and keeps the
/// error. **After a query, check [#failure()]**: if it is present, the result came from a
/// document that ended early. To check the whole input before a query, use [#readFully].
///
/// ## Duplicate member names
///
/// The parser gives each member, so the document keeps duplicate names.
/// [JsonModel#member(Object, JsonString)] gives the first member with the name, and
/// [JsonModel#hasDuplicate(Object, JsonString)] finds the duplicates.
///
/// ## Threads
///
/// A document reads its parser under a lock, so several threads can query it. The document does
/// not close the parser.
public final class JacksonStream {

    private final JsonParser parser;
    private final JacksonStreamNode root;
    /// The objects and arrays that the parser is in, the innermost first.
    private final Deque<JacksonStreamNode> open = new ArrayDeque<>();
    private JsonString pendingName = JsonString.of("");
    private Maybe<StreamError> failure = Maybe.none();

    private JacksonStream(JsonParser parser, JsonToken first) {
        this.parser = parser;
        JacksonStreamNode value = value(first);
        // The first token of a well-formed input is a value; a failure gives a null root.
        this.root = value != null ? value : JacksonStreamNode.scalar(this, JsonKind.NULL);
    }

    /// Starts to read a document. The method reads only the first token.
    ///
    /// @param parser a parser before the first token of the document
    /// @return the document, or an error if the input has no value or the first token fails
    public static Result<JacksonStream, StreamError> open(JsonParser parser) {
        JsonToken first;
        try {
            first = parser.nextToken();
        } catch (JacksonException e) {
            return Result.err(new StreamError.ReadFailed(e.getOriginalMessage()));
        }
        if (first == null) {
            return Result.err(new StreamError.Empty());
        }
        JacksonStream stream = new JacksonStream(parser, first);
        return stream.failure instanceof Maybe.Some<StreamError>(StreamError error) ? Result.err(error) : Result.ok(stream);
    }

    /// Reads a whole document, and checks it: the input must be exactly one value.
    ///
    /// @param parser a parser before the first token of the document
    /// @return the document, or the first error
    public static Result<JacksonStream, StreamError> readFully(JsonParser parser) {
        return open(parser).flatMap(stream -> stream.checkAll());
    }

    private synchronized Result<JacksonStream, StreamError> checkAll() {
        readAll(root);
        if (failure instanceof Maybe.Some<StreamError>(StreamError error)) {
            return Result.err(error);
        }
        try {
            return parser.nextToken() == null ? Result.ok(this) : Result.err(new StreamError.TrailingContent());
        } catch (JacksonException e) {
            return Result.err(new StreamError.ReadFailed(e.getOriginalMessage()));
        }
    }

    /// Returns the root value.
    ///
    /// @return the root node
    public JacksonStreamNode root() {
        return root;
    }

    /// Returns the model for the nodes of this document.
    ///
    /// The model is read-only: it does not implement `JsonFactory`.
    ///
    /// @return the model
    public JsonModel<JacksonStreamNode> model() {
        return JacksonStreamModel.INSTANCE;
    }

    /// Returns the error that ended the document early, if any.
    ///
    /// @return the first error of the parser, or [Maybe.None] if the document has read without an
    ///     error so far
    public synchronized Maybe<StreamError> failure() {
        return failure;
    }

    // -------------------------------------------------------------------------------------------
    // Reads on demand
    // -------------------------------------------------------------------------------------------

    /// Reads to the end of a container, and returns it.
    synchronized JacksonStreamNode readAll(JacksonStreamNode container) {
        while (!container.complete) {
            step();
        }
        return container;
    }

    /// Reads until a container has a child at an index, or until it ends.
    synchronized Maybe<JacksonStreamNode> child(JacksonStreamNode container, int index) {
        while (container.children.size() <= index && !container.complete) {
            step();
        }
        return index < container.children.size() ? Maybe.some(container.children.get(index)) : Maybe.none();
    }


    /// A cursor over the members of an object. It reads forward one member at a time, so a walk
    /// that stops early does not read the rest of the object.
    MemberCursor<JacksonStreamNode> cursor(JacksonStreamNode object) {
        return new StreamCursor(object);
    }

    private final class StreamCursor implements MemberCursor<JacksonStreamNode> {
        private final JacksonStreamNode object;
        private int index = -1;

        StreamCursor(JacksonStreamNode object) {
            this.object = object;
        }

        @Override
        public boolean next() {
            if (child(object, index + 1).isNone()) {
                return false;
            }
            index++;
            return true;
        }

        @Override
        public JsonString name() {
            return nameAt(object, index);
        }

        @Override
        public JacksonStreamNode value() {
            return childAt(object, index);
        }
    }

    /// The name of a member that the stream has read. A method, not a block, under the lock.
    private synchronized JsonString nameAt(JacksonStreamNode object, int index) {
        return object.names.get(index);
    }

    /// A child that the stream has read.
    private synchronized JacksonStreamNode childAt(JacksonStreamNode container, int index) {
        return container.children.get(index);
    }

    /// Reads until an object has a member with a name, or until it ends. The first member wins.
    synchronized Maybe<JacksonStreamNode> member(JacksonStreamNode object, JsonString name) {
        int next = 0;
        while (true) {
            for (; next < object.names.size(); next++) {
                if (JsonString.equal(object.names.get(next), name)) {
                    return Maybe.some(object.children.get(next));
                }
            }
            if (object.complete) {
                return Maybe.none();
            }
            step();
        }
    }

    /// Reads one token in the innermost open container. Each call makes progress: it reads a
    /// token, or it ends all open containers after an error.
    private void step() {
        JsonToken token;
        try {
            token = parser.nextToken();
        } catch (JacksonException e) {
            fail(new StreamError.ReadFailed(e.getOriginalMessage()));
            return;
        }
        if (token == null) {
            fail(new StreamError.ReadFailed("The input ends inside a value."));
            return;
        }
        // The callers read only while a container is open.
        JacksonStreamNode container = open.element();
        switch (token) {
            case END_OBJECT, END_ARRAY -> {
                container.complete = true;
                open.pop();
            }
            case PROPERTY_NAME -> pendingName = JsonString.of(parser.currentName());
            default -> {
                JacksonStreamNode child = value(token);
                if (child != null) {
                    container.children.add(child);
                    if (container.kind == JsonKind.OBJECT) {
                        container.names.add(pendingName);
                    }
                }
            }
        }
    }

    /// The node of a value token. A container goes onto the stack of open containers.
    private @Nullable JacksonStreamNode value(JsonToken token) {
        try {
            JacksonStreamNode node = switch (token) {
                case START_OBJECT -> JacksonStreamNode.container(this, JsonKind.OBJECT);
                case START_ARRAY -> JacksonStreamNode.container(this, JsonKind.ARRAY);
                case VALUE_STRING -> JacksonStreamNode.string(this, JsonString.of(parser.getString()));
                case VALUE_NUMBER_INT -> JacksonStreamNode.number(this, integer());
                case VALUE_NUMBER_FLOAT -> decimal(parser.getString());
                case VALUE_TRUE -> JacksonStreamNode.scalar(this, JsonKind.TRUE);
                case VALUE_FALSE -> JacksonStreamNode.scalar(this, JsonKind.FALSE);
                case VALUE_NULL -> JacksonStreamNode.scalar(this, JsonKind.NULL);
                default -> null;
            };
            if (node == null) {
                fail(new StreamError.NotJson(token.name()));
            } else if (!node.complete) {
                open.push(node);
            }
            return node;
        } catch (JacksonException e) {
            fail(new StreamError.ReadFailed(e.getOriginalMessage()));
            return null;
        }
    }

    private JsonNumber integer() {
        return switch (parser.getNumberType()) {
            case INT, LONG -> JsonNumber.of(parser.getLongValue());
            default -> JsonNumber.of(parser.getBigIntegerValue());
        };
    }

    /// A number with a fraction or an exponent, from its text, so that it stays exact. NaN and
    /// the infinities (from a parser that permits them) are not JSON numbers: they are strings.
    private JacksonStreamNode decimal(String text) {
        return switch (JsonDecimal.parse(text)) {
            case Maybe.Some<JsonDecimal>(JsonDecimal value) -> JacksonStreamNode.number(this, JsonNumber.of(value));
            case Maybe.None<JsonDecimal>() -> JacksonStreamNode.string(this, JsonString.of(text));
        };
    }

    /// Keeps the error, and ends each open container. Then the document reads no more tokens, so
    /// this error is the first.
    private void fail(StreamError error) {
        failure = Maybe.some(error);
        while (!open.isEmpty()) {
            open.pop().complete = true;
        }
    }
}
