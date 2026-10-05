package ca.marcusdunn.jsonlens.path.evaluator.iregexp;

import java.util.List;
import java.util.function.IntPredicate;

/** The syntax tree of an I-Regexp. */
sealed interface RegexNode {

    /** Matches the empty string. */
    record Empty() implements RegexNode {}

    /** Matches the empty string at the start (true) or the end (false) of the input. */
    record Anchor(boolean start) implements RegexNode {}

    /** Matches one character of a set. */
    record CharacterSet(IntPredicate set) implements RegexNode {}

    /** Matches the parts one after the other. */
    record Sequence(List<RegexNode> parts) implements RegexNode {}

    /** Matches one of the branches. */
    record Choice(List<RegexNode> branches) implements RegexNode {}

    /**
     * Matches the body from min to max times.
     *
     * @param max the maximum, or -1 for no maximum
     */
    record Repeat(RegexNode body, int min, int max) implements RegexNode {}
}
