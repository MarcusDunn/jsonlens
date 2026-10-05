package ca.marcusdunn.jsonlens.path.evaluator.iregexp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The regexp compiler: program sizes, limits, and grammar boundaries. */
class ProgramTest {

    private static RegexNode tree(String pattern) {
        return switch (RegexParser.parse(pattern)) {
            case Result.Ok<RegexNode, IRegexp.Problem>(RegexNode tree) -> tree;
            case Result.Err<RegexNode, IRegexp.Problem>(IRegexp.Problem problem) -> fail(pattern + ": " + problem);
        };
    }

    private static Program program(String pattern, int maxSize) {
        return switch (Program.compile(tree(pattern), maxSize)) {
            case Result.Ok<Program, IRegexp.Problem>(Program program) -> program;
            case Result.Err<Program, IRegexp.Problem>(IRegexp.Problem problem) -> fail(pattern + ": " + problem);
        };
    }

    private static boolean matches(String pattern, String input) {
        return program(pattern, 10_000).run(input.codePoints().iterator(), true);
    }

    @Test
    @Requirement("rfc9485-8/range-quantifiers")
    void programLengthIsTheComputedSize() {
        record Row(String pattern, int length) {}
        // The length includes the final MATCH instruction.
        for (Row row : List.of(
                new Row("", 1), new Row("a", 2), new Row("ab", 3), new Row("^a$", 4),
                new Row("a|b", 5), new Row("a|b|c", 8), new Row("a|bc|", 8),
                new Row("a*", 4), new Row("a+", 5), new Row("a?", 3),
                new Row("a{3}", 4), new Row("a{2,}", 6), new Row("a{2,4}", 7), new Row("a{0,2}", 5),
                new Row("(ab){2,3}", 8), new Row("(a|b)*", 7), new Row("(a{2}){3}", 7),
                new Row("a{0}", 1), new Row("a{0,0}", 1), new Row("a{0,}", 4))) {
            assertEquals(row.length(), program(row.pattern(), 10_000).length(), row.pattern());
            assertEquals(row.length(), Program.size(tree(row.pattern())) + 1, row.pattern());
        }
    }

    @Test
    @Requirement("rfc9485-8/range-quantifiers")
    void sizeLimitIsExact() {
        // "aaa" needs four instructions.
        assertEquals(4, program("aaa", 4).length());
        assertInstanceOf(IRegexp.Problem.TooComplex.class,
                ((Result.Err<Program, IRegexp.Problem>) Program.compile(tree("aaa"), 3)).error());
        assertEquals(Long.MAX_VALUE - 1, Program.saturatedMultiply(2, Long.MAX_VALUE / 2));
        assertEquals(Long.MAX_VALUE, Program.saturatedMultiply(2, Long.MAX_VALUE / 2 + 1));
    }

    @Test
    @Requirement("rfc9485-3/checking")
    void grammarBoundaries() {
        assertTrue(matches("a{2,2}", "aa"));
        assertTrue(matches("a{0}", ""));
        assertFalse(matches("a{0}", "a"));
        assertFalse(matches("a{0,0}", "a"));
        assertTrue(matches("[a-a]", "a"));
        assertTrue(matches("[\u0000]", "\u0000"));
        assertTrue(matches("\u0000", "\u0000"));
        assertTrue(matches("퟿", "퟿"));
        assertTrue(matches("", ""));
        assertTrue(matches("\\p{L}", "a"));
        for (String invalid : List.of("\udfff", "\ud800", "[\udfff]", "\\p{L", "\\p{X}", "[a-\\p{L}]", "[a-\\x]", "\\", "a{", "a{1,0}", "a{2,1}")) {
            assertInstanceOf(IRegexp.Problem.Invalid.class,
                    ((Result.Err<RegexNode, IRegexp.Problem>) RegexParser.parse(invalid)).error(), invalid);
        }
    }

    @Test
    @Requirement("rfc9485-8/range-quantifiers")
    void groupDepthLimitIsExact() {
        int limit = RegexParser.MAX_GROUP_DEPTH;
        assertTrue(matches("(".repeat(limit) + "a" + ")".repeat(limit), "a"));
        assertInstanceOf(IRegexp.Problem.TooComplex.class, ((Result.Err<RegexNode, IRegexp.Problem>)
                RegexParser.parse("(".repeat(limit + 1) + "a" + ")".repeat(limit + 1))).error());
        // Groups one after the other are not nested.
        assertTrue(matches("(a)".repeat(2 * limit), "a".repeat(2 * limit)));
    }

    @Test
    void anchoredMatchFailsAfterTheInputEnds() {
        assertFalse(matches("ab", "a"));
        assertFalse(matches("a", "ab"));
        assertFalse(matches("a", "b"));
        assertTrue(program("b", 100).run("abc".codePoints().iterator(), false));
        assertFalse(program("d", 100).run("abc".codePoints().iterator(), false));
    }
}
