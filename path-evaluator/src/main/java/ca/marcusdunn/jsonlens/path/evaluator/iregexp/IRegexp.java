package ca.marcusdunn.jsonlens.path.evaluator.iregexp;

import ca.marcusdunn.jsonlens.model.Result;
import java.util.PrimitiveIterator;

/** A compiled I-Regexp (RFC 9485). Instances are immutable and safe for use by more than one thread. */
public final class IRegexp {

    /** The reason why a regexp cannot be compiled. */
    public sealed interface Problem {

        /**
         * The regexp does not agree with the I-Regexp grammar.
         *
         * @param position the index of the UTF-16 code unit where the problem is
         */
        record Invalid(int position) implements Problem {}

        /**
         * The regexp is valid, but its program has more instructions than the limit.
         *
         * @param limit the maximum number of instructions
         */
        record TooComplex(int limit) implements Problem {}
    }

    private final Program program;

    private IRegexp(Program program) {
        this.program = program;
    }

    /**
     * Compiles a regexp.
     *
     * @param pattern the regexp
     * @param maxSize the maximum number of program instructions
     * @return the compiled regexp, or the problem
     */
    public static Result<IRegexp, Problem> compile(String pattern, int maxSize) {
        return RegexParser.parse(pattern)
                .flatMap(tree -> Program.compile(tree, maxSize))
                .map(IRegexp::new);
    }

    /**
     * Returns true if the complete input matches the regexp, as match() requires.
     *
     * @param input the input
     * @return true for a match of the complete input
     */
    public boolean matches(String input) {
        return matches(input.codePoints().iterator());
    }

    /**
     * Returns true if the complete input matches the regexp.
     *
     * @param input the Unicode scalar values of the input, in order
     * @return true for a match of the complete input
     */
    public boolean matches(PrimitiveIterator.OfInt input) {
        return program.run(input, true);
    }

    /**
     * Returns true if a substring of the input matches the regexp, as search() requires.
     *
     * @param input the input
     * @return true if at least one substring matches
     */
    public boolean find(String input) {
        return find(input.codePoints().iterator());
    }

    /**
     * Returns true if a substring of the input matches the regexp.
     *
     * @param input the Unicode scalar values of the input, in order
     * @return true if at least one substring matches
     */
    public boolean find(PrimitiveIterator.OfInt input) {
        return program.run(input, false);
    }
}
