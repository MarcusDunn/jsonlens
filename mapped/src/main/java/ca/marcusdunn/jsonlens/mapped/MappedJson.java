package ca.marcusdunn.jsonlens.mapped;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/// A JSON text in UTF-8 bytes, for example a memory-mapped file, with a read-only
/// [JsonModel].
///
/// [#open(Path)] maps a file, and [#of(ByteBuffer)] uses bytes that are already in memory. Both
/// check the text once (RFC 8259 and UTF-8) and make a structural index: the kind and the position
/// of each value, and the children of each array and object. They do not copy or decode the text.
/// The index is on the heap. It uses 5 bytes for each value, 4 more bytes for each array element,
/// 8 more bytes for each object member, and 4 bytes for each array and object.
///
/// The [#model()] reads the bytes only when the evaluator needs a value:
///
/// - A string is a [ca.marcusdunn.jsonlens.model.JsonString] that decodes UTF-8 and escapes
///   while the evaluator reads it.
/// - A number is a [ca.marcusdunn.jsonlens.model.JsonNumber] that reads its digits only for a
///   comparison. Its exact value has no limit on the exponent.
/// - A member name is also such a `JsonString`. [JsonModel#member(Object, JsonString)] compares
///   the name bytes with the query name, and the member cursor of the model gives the names without
///   a decoded copy.
///
/// A Normalized Path of a result keeps these names, and it reads the bytes when the caller reads
/// the path, for example with `toString()`. Thus the paths are correct only while the bytes stay
/// the same, and they keep the mapping in memory.
///
/// ## Duplicate member names
///
/// RFC 8259 permits duplicate names, but their meaning is not defined. This model keeps all
/// members: [JsonModel#member(Object, JsonString)] gives the first member with the name, and
/// [JsonModel#memberCursor(Object)] gives all members in document order.
/// [JsonModel#hasDuplicate(Object, ca.marcusdunn.jsonlens.model.JsonString)] finds them, for
/// example for JSON Pointer.
///
/// ## Limits and threads
///
/// A `ByteBuffer` has an `int` index, so a file can have at most 2 GiB. The caller must not change
/// the bytes while the index is in use. A `MappedJson` and its model are safe for concurrent reads.
public final class MappedJson {

    /// The maximum size of a file, in bytes.
    static final long MAX_SIZE = Integer.MAX_VALUE;

    /// The codes of the kinds in [#kinds]: one byte for each value.
    static final byte OBJECT = 0;
    static final byte ARRAY = 1;
    static final byte STRING = 2;
    static final byte NUMBER = 3;
    static final byte TRUE = 4;
    static final byte FALSE = 5;
    static final byte NULL = 6;

    /// The kinds by code.
    private static final JsonKind[] KINDS = {
        JsonKind.OBJECT, JsonKind.ARRAY, JsonKind.STRING, JsonKind.NUMBER, JsonKind.TRUE, JsonKind.FALSE, JsonKind.NULL
    };

    final ByteBuffer data;
    /// The kind of each value, in document order, as a code.
    final byte[] kinds;
    /// One slot for each value. For a string or a number, it is the offset of the first byte. For
    /// an array or an object, it is the start of its entry in [#children].
    final int[] slots;
    /// One entry for each array and object. An entry starts with the number of children. Then an
    /// array has the index of each element. An object has a pair for each member: the offset of
    /// the name, then the index of the value.
    final int[] children;

    MappedJson(ByteBuffer data, byte[] kinds, int[] slots, int[] children) {
        this.data = data;
        this.kinds = kinds;
        this.slots = slots;
        this.children = children;
    }

    /// Maps a file that holds a JSON text in UTF-8.
    ///
    /// @param path the file
    /// @return the JSON text, or an error if the file cannot be read, is larger than 2 GiB, or is not
    ///     a JSON text in UTF-8
    public static Result<MappedJson, MappedJsonError> open(Path path) {
        FileChannel channel;
        try {
            channel = FileChannel.open(path, StandardOpenOption.READ);
        } catch (IOException e) {
            return Result.err(new MappedJsonError.IoFailure(e.toString()));
        }
        return map(channel, MAX_SIZE);
    }

    /// Maps the content of an open channel, with a size limit, and closes the channel.
    static Result<MappedJson, MappedJsonError> map(FileChannel channel, long limit) {
        Result<MappedJson, MappedJsonError> result;
        try {
            long size = channel.size();
            result = size > limit
                    ? Result.err(new MappedJsonError.FileTooLarge(size, limit))
                    : of(channel.map(FileChannel.MapMode.READ_ONLY, 0, size));
        } catch (IOException e) {
            result = Result.err(new MappedJsonError.IoFailure(e.toString()));
        }
        return close(channel, result);
    }

    /// Closes a channel and returns the result. The mapping stays valid after the channel closes, so
    /// an error when the channel closes does not change the result.
    private static Result<MappedJson, MappedJsonError> close(FileChannel channel, Result<MappedJson, MappedJsonError> result) {
        try {
            channel.close();
        } catch (IOException e) {
            // The result is still correct. There is nothing else to do.
            return result;
        }
        return result;
    }

    /// Uses the bytes from the position to the limit of a buffer as a JSON text in UTF-8.
    ///
    /// The JSON text uses the bytes of the buffer; it does not copy them. The position and the limit
    /// of the buffer do not change.
    ///
    /// @param bytes the bytes of the JSON text
    /// @return the JSON text, or an error if the bytes are not a JSON text in UTF-8
    public static Result<MappedJson, MappedJsonError> of(ByteBuffer bytes) {
        return Indexer.index(bytes);
    }

    /// Returns the root value: the complete JSON text.
    ///
    /// @return the root node
    public MappedNode root() {
        return node(0);
    }

    /// Returns the model for the nodes of this JSON text.
    ///
    /// The model is read-only: it does not implement
    /// [ca.marcusdunn.jsonlens.model.JsonFactory].
    ///
    /// @return the model
    public JsonModel<MappedNode> model() {
        return MappedJsonModel.INSTANCE;
    }

    /// Returns the kind of a value.
    JsonKind kind(int index) {
        return KINDS[kinds[index]];
    }

    /// Returns a node for a value. The nodes for the same value are equal.
    MappedNode node(int index) {
        return new MappedNode(this, index);
    }
}
