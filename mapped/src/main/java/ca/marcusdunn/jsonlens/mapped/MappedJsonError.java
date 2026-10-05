package ca.marcusdunn.jsonlens.mapped;

/// The reason why [MappedJson] cannot read the bytes or the file.
public sealed interface MappedJsonError {

    /// Returns a description of the error.
    ///
    /// @return a description of the error
    default String message() {
        return switch (this) {
            case InvalidJson e -> "Expected " + e.expected() + " at byte " + e.offset() + ".";
            case InvalidUtf8 e -> "The bytes at byte " + e.offset() + " are not well-formed UTF-8.";
            case FileTooLarge e -> "The file has " + e.size() + " bytes. The maximum is " + e.limit() + " bytes.";
            case IoFailure e -> "The file cannot be read: " + e.reason();
        };
    }

    /// The bytes do not agree with the JSON grammar of RFC 8259.
    ///
    /// @param offset the index of the byte where the problem is
    /// @param expected a description of what the grammar permits at that byte
    record InvalidJson(int offset, String expected) implements MappedJsonError {}

    /// The bytes are not well-formed UTF-8 (RFC 3629).
    ///
    /// @param offset the index of the first byte that is not valid
    record InvalidUtf8(int offset) implements MappedJsonError {}

    /// The file is too large. A `ByteBuffer` has an `int` index, so the limit is 2 GiB.
    ///
    /// @param size the size of the file, in bytes
    /// @param limit the maximum size, in bytes
    record FileTooLarge(long size, long limit) implements MappedJsonError {}

    /// The file cannot be opened or mapped.
    ///
    /// @param reason a description of the I/O problem
    record IoFailure(String reason) implements MappedJsonError {}
}
