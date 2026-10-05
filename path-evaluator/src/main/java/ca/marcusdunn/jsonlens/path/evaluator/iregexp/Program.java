package ca.marcusdunn.jsonlens.path.evaluator.iregexp;

import ca.marcusdunn.jsonlens.model.Result;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.function.IntPredicate;

/**
 * A regexp program for a Pike virtual machine (a Thompson NFA simulation).
 *
 * <p>{@link #run} keeps a set of program positions for each input character, so its time is
 * at most the input length multiplied by the program size. There is no backtracking.
 */
final class Program {

    /** Matches one character. {@code x} is the index of the character set. */
    private static final int CHAR = 0;
    /** Continues at {@code x} and at {@code y}. */
    private static final int SPLIT = 1;
    /** Continues at {@code x}. */
    private static final int JUMP = 2;
    /** The regexp matches. */
    private static final int MATCH = 3;
    /** Continues with the next instruction only at the start of the input. */
    private static final int ASSERT_START = 4;
    /** Continues with the next instruction only at the end of the input. The last instruction kind. */
    private static final int ASSERT_END = 5;

    private final int[] ops;
    private final int[] x;
    private final int[] y;
    private final IntPredicate[] sets;

    private Program(int[] ops, int[] x, int[] y, IntPredicate[] sets) {
        this.ops = ops;
        this.x = x;
        this.y = y;
        this.sets = sets;
    }

    static Result<Program, IRegexp.Problem> compile(RegexNode tree, int maxSize) {
        // The program has size(tree) instructions and one MATCH. size() saturates at Long.MAX_VALUE,
        // so do not add 1 to it.
        if (size(tree) >= maxSize) {
            return Result.err(new IRegexp.Problem.TooComplex(maxSize));
        }
        Builder builder = new Builder();
        builder.emit(tree);
        builder.add(MATCH, 0, 0);
        return Result.ok(builder.build());
    }

    /** Returns the number of instructions for a tree, or Long.MAX_VALUE if it is very large. */
    static long size(RegexNode node) {
        return switch (node) {
            case RegexNode.Empty empty -> 0;
            case RegexNode.Anchor anchor -> 1;
            case RegexNode.CharacterSet set -> 1;
            case RegexNode.Sequence(List<RegexNode> parts) -> {
                long total = 0;
                for (RegexNode part : parts) {
                    total = saturatedAdd(total, size(part));
                }
                yield total;
            }
            case RegexNode.Choice(List<RegexNode> branches) -> {
                long total = 2L * (branches.size() - 1);
                for (RegexNode branch : branches) {
                    total = saturatedAdd(total, size(branch));
                }
                yield total;
            }
            case RegexNode.Repeat repeat -> {
                long body = size(repeat.body());
                if (repeat.max() < 0) {
                    // min copies, then SPLIT, one more copy, and JUMP.
                    yield saturatedAdd(saturatedMultiply(body, repeat.min() + 1L), 2);
                }
                // min copies, then (max - min) pairs of SPLIT and a copy.
                yield saturatedAdd(saturatedMultiply(body, repeat.max()), repeat.max() - repeat.min());
            }
        };
    }

    static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }

    static long saturatedMultiply(long a, long b) {
        return a != 0 && b > Long.MAX_VALUE / a ? Long.MAX_VALUE : a * b;
    }

    /**
     * Runs the program.
     *
     * @param input the Unicode scalar values of the input, in order
     * @param anchored true to match the complete input, false to find a matching substring
     * @return true for a match
     */
    boolean run(PrimitiveIterator.OfInt input, boolean anchored) {
        Threads current = new Threads(ops.length);
        Threads next = new Threads(ops.length);
        int generation = 1;
        addThread(current, 0, generation, true, !input.hasNext());
        while (true) {
            boolean atEnd = !input.hasNext();
            if (current.match && (!anchored || atEnd)) {
                return true;
            }
            if (atEnd) {
                return false;
            }
            int c = input.nextInt();
            generation++;
            boolean nowAtEnd = !input.hasNext();
            next.clear();
            for (int i = 0; i < current.count; i++) {
                int pc = current.pcs[i];
                // A thread list has only CHAR instructions.
                if (sets[x[pc]].test(c)) {
                    addThread(next, pc + 1, generation, false, nowAtEnd);
                }
            }
            if (!anchored) {
                addThread(next, 0, generation, false, nowAtEnd);
            }
            Threads swap = current;
            current = next;
            next = swap;
        }
    }

    /** The threads of one step: program positions that wait for a character, and MATCH. */
    private static final class Threads {
        final int[] pcs;
        final int[] marks;
        int count;
        boolean match;

        Threads(int size) {
            pcs = new int[size];
            marks = new int[size];
        }

        void clear() {
            count = 0;
            match = false;
        }
    }

    /**
     * Adds a thread and all threads that it reaches through SPLIT, JUMP, and true assertions,
     * without recursion. A position is added at most once in each generation.
     */
    private void addThread(Threads threads, int start, int generation, boolean atStart, boolean atEnd) {
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            int pc = stack.pop();
            if (threads.marks[pc] == generation) {
                continue;
            }
            threads.marks[pc] = generation;
            switch (ops[pc]) {
                case CHAR -> threads.pcs[threads.count++] = pc;
                case SPLIT -> {
                    stack.push(y[pc]);
                    stack.push(x[pc]);
                }
                case JUMP -> stack.push(x[pc]);
                case MATCH -> threads.match = true;
                case ASSERT_START -> {
                    if (atStart) {
                        stack.push(pc + 1);
                    }
                }
                default -> {
                    if (atEnd) {
                        stack.push(pc + 1);
                    }
                }
            }
        }
    }

    /** Returns the number of instructions. */
    int length() {
        return ops.length;
    }

    /** Collects instructions. */
    private static final class Builder {
        private final List<Integer> ops = new ArrayList<>();
        private final List<Integer> x = new ArrayList<>();
        private final List<Integer> y = new ArrayList<>();
        private final List<IntPredicate> sets = new ArrayList<>();

        int add(int op, int target1, int target2) {
            ops.add(op);
            x.add(target1);
            y.add(target2);
            return ops.size() - 1;
        }

        int pc() {
            return ops.size();
        }

        void emit(RegexNode node) {
            switch (node) {
                case RegexNode.Empty empty -> {}
                case RegexNode.Anchor anchor -> add(anchor.start() ? ASSERT_START : ASSERT_END, 0, 0);
                case RegexNode.CharacterSet(IntPredicate set) -> {
                    sets.add(set);
                    add(CHAR, sets.size() - 1, 0);
                }
                case RegexNode.Sequence(List<RegexNode> parts) -> parts.forEach(this::emit);
                case RegexNode.Choice(List<RegexNode> branches) -> {
                    List<Integer> jumps = new ArrayList<>();
                    for (int i = 0; i < branches.size() - 1; i++) {
                        int split = add(SPLIT, pc() + 1, 0);
                        emit(branches.get(i));
                        jumps.add(add(JUMP, 0, 0));
                        y.set(split, pc());
                    }
                    emit(branches.getLast());
                    jumps.forEach(jump -> x.set(jump, pc()));
                }
                case RegexNode.Repeat repeat -> {
                    for (int i = 0; i < repeat.min(); i++) {
                        emit(repeat.body());
                    }
                    if (repeat.max() < 0) {
                        int split = add(SPLIT, pc() + 1, 0);
                        emit(repeat.body());
                        add(JUMP, split, 0);
                        y.set(split, pc());
                    } else {
                        List<Integer> splits = new ArrayList<>();
                        for (int i = repeat.min(); i < repeat.max(); i++) {
                            splits.add(add(SPLIT, pc() + 1, 0));
                            emit(repeat.body());
                        }
                        splits.forEach(split -> y.set(split, pc()));
                    }
                }
            }
        }

        Program build() {
            return new Program(
                    ops.stream().mapToInt(Integer::intValue).toArray(),
                    x.stream().mapToInt(Integer::intValue).toArray(),
                    y.stream().mapToInt(Integer::intValue).toArray(),
                    sets.toArray(IntPredicate[]::new));
        }
    }
}
