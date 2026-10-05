package ca.marcusdunn.jsonlens.mapped;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/// A channel that fails each operation, also when it closes. It shows the I/O errors after a file
/// is open.
class FailingChannel extends FileChannel {

    private static IOException failure() {
        return new IOException("the device failed");
    }

    @Override
    public long size() throws IOException {
        throw failure();
    }

    @Override
    public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
        throw failure();
    }

    @Override
    protected void implCloseChannel() throws IOException {
        throw failure();
    }

    @Override
    public int read(ByteBuffer destination) throws IOException {
        throw failure();
    }

    @Override
    public long read(ByteBuffer[] destinations, int offset, int length) throws IOException {
        throw failure();
    }

    @Override
    public int write(ByteBuffer source) throws IOException {
        throw failure();
    }

    @Override
    public long write(ByteBuffer[] sources, int offset, int length) throws IOException {
        throw failure();
    }

    @Override
    public long position() throws IOException {
        throw failure();
    }

    @Override
    public FileChannel position(long newPosition) throws IOException {
        throw failure();
    }

    @Override
    public FileChannel truncate(long size) throws IOException {
        throw failure();
    }

    @Override
    public void force(boolean metaData) throws IOException {
        throw failure();
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        throw failure();
    }

    @Override
    public long transferFrom(ReadableByteChannel source, long position, long count) throws IOException {
        throw failure();
    }

    @Override
    public int read(ByteBuffer destination, long position) throws IOException {
        throw failure();
    }

    @Override
    public int write(ByteBuffer source, long position) throws IOException {
        throw failure();
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) throws IOException {
        throw failure();
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        throw failure();
    }
}
