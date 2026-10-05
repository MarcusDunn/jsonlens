package ca.marcusdunn.jsonlens.mapped;

import java.util.ArrayList;
import java.util.List;

/// A growable list of `int` values, in blocks of a fixed size.
///
/// The list does not copy its values when it grows: it adds a block. Only [#toArray()] copies
/// the values, one time, into an array of the exact size.
final class IntList {

    private static final int BLOCK_BITS = 14;
    private static final int BLOCK_SIZE = 1 << BLOCK_BITS;
    private static final int BLOCK_MASK = BLOCK_SIZE - 1;

    private final List<int[]> blocks = new ArrayList<>();
    private int size;

    void add(int value) {
        if (size == blocks.size() << BLOCK_BITS) {
            blocks.add(new int[BLOCK_SIZE]);
        }
        blocks.get(size >>> BLOCK_BITS)[size & BLOCK_MASK] = value;
        size++;
    }

    int get(int index) {
        return blocks.get(index >>> BLOCK_BITS)[index & BLOCK_MASK];
    }

    void set(int index, int value) {
        blocks.get(index >>> BLOCK_BITS)[index & BLOCK_MASK] = value;
    }

    int size() {
        return size;
    }

    /// Removes the values from an index to the end. The list keeps its blocks for later values.
    void truncate(int newSize) {
        size = newSize;
    }

    int[] toArray() {
        int[] array = new int[size];
        for (int start = 0; start < size; start += BLOCK_SIZE) {
            System.arraycopy(blocks.get(start >>> BLOCK_BITS), 0, array, start, Math.min(BLOCK_SIZE, size - start));
        }
        return array;
    }
}
