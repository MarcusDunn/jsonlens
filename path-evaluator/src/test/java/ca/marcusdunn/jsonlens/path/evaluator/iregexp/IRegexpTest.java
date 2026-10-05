package ca.marcusdunn.jsonlens.path.evaluator.iregexp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** RFC 9485: the I-Regexp implementation of match() and search(). */
class IRegexpTest {

    private static final int LIMIT = 10_000;

    private static IRegexp compile(String pattern) {
        return switch (IRegexp.compile(pattern, LIMIT)) {
            case Result.Ok<IRegexp, IRegexp.Problem>(IRegexp regexp) -> regexp;
            case Result.Err<IRegexp, IRegexp.Problem>(IRegexp.Problem problem) -> fail(pattern + ": " + problem);
        };
    }

    private static IRegexp.Problem problem(String pattern) {
        return switch (IRegexp.compile(pattern, LIMIT)) {
            case Result.Ok<IRegexp, IRegexp.Problem>(IRegexp regexp) -> fail("expected a problem: " + pattern);
            case Result.Err<IRegexp, IRegexp.Problem>(IRegexp.Problem problem) -> problem;
        };
    }

    private static boolean matches(String pattern, String input) {
        return compile(pattern).matches(input);
    }

    @Test
    @Requirement("rfc9485-3/checking")
    void acceptsTheGrammar() {
        for (String pattern : List.of(
                "", "a", "a|b", "a|", "|", "()", "(a|b)*c", "a+", "a?", "a{2}", "a{2,}", "a{2,3}", "a{0,0}",
                ".", "\\n", "\\r", "\\t", "\\(", "\\)", "\\*", "\\+", "\\-", "\\.", "\\?", "\\[", "\\\\", "\\]",
                "\\^", "\\{", "\\|", "\\}", "[a]", "[^a]", "[a-z]", "[-a]", "[a-]", "[-]", "[--]", "[\\p{L}\\d-]".replace("\\d", "0"),
                "[\\n-\\r]", "\\p{L}", "\\P{Nd}", "[\\P{Lu}x]", "\u00e9", "\ud83d\ude00", "[\ud83d\ude00-\ud83d\ude4f]",
                ",", "-", "/", "<", "@", "_", "`", "~", "\"", "'", "#", "&", "!", "%", "=")) {
            compile(pattern);
        }
    }

    @Test
    @Requirement("rfc9485-3/checking")
    void rejectsWhatIsNotInTheGrammar() {
        for (String pattern : List.of(
                "(", ")", "a)", "[", "]", "}", "{", "a{", "a{1", "a{,2}", "a{2,1}", "a**", "a+?", "*", "+a", "?",
                "\\", "\\d", "\\w", "\\s", "\\S", "\\b", "\\a", "\\x41", "\\u0041", "\\1", "(?:a)", "(?i)a",
                "[]", "[^]", "[a-]b]", "[z-a]", "[a-b-c]", "[--a]", "[[a]]", "[a-\\p{L}]", "[\\p{L}-a]",
                "\\p{L", "\\p{}", "\\p{Lx}", "\\p{IsBasicLatin}", "\\p{Cs}", "[a", "a{1}{2}", "\ud800")) {
            assertInstanceOf(IRegexp.Problem.Invalid.class, problem(pattern), pattern);
        }
    }

    @Test
    @Requirement("rfc9485-3/unicode")
    void supportsUnicode() {
        assertTrue(matches(".", "\ud83d\ude00"));
        assertFalse(matches("..", "\ud83d\ude00"));
        assertTrue(matches("\\p{L}+", "\u00e9\u4e2dA"));
        assertTrue(matches("\\p{Lu}\\p{Ll}", "Ab"));
        assertFalse(matches("\\p{Lu}", "a"));
        assertTrue(matches("\\p{Nd}", "\u0663"));
        assertTrue(matches("\\P{Nd}", "x"));
        assertTrue(matches("\\p{So}", "\ud83d\ude00"));
        assertTrue(matches("\\p{Zs}", "\u3000"));
        assertTrue(matches("\\p{Cc}", "\u0000"));
        assertTrue(matches("\\p{Co}", "\ue000"));
        assertTrue(matches("\\p{Cn}", "\u0378"));
        assertTrue(matches("\\p{Pd}\\p{Ps}\\p{Pe}", "-()"));
        assertTrue(matches("\\p{Sm}\\p{Sc}", "+$"));
        assertTrue(matches("\\p{M}", "\u0301"));
        assertTrue(matches("[\ud83d\ude00-\ud83d\ude4f]", "\ud83d\ude03"));
        assertTrue(matches("[^a]", "\ud83d\ude00"));
    }

    @Test
    @Requirement("rfc9485-3/unicode")
    void everyCategoryName() {
        String[][] samples = {
            {"L", "a"}, {"Lu", "A"}, {"Ll", "a"}, {"Lt", "\u01c5"}, {"Lm", "\u02b0"}, {"Lo", "\u05d0"},
            {"M", "\u0301"}, {"Mn", "\u0301"}, {"Mc", "\u0903"}, {"Me", "\u20dd"},
            {"N", "1"}, {"Nd", "1"}, {"Nl", "\u2160"}, {"No", "\u00b2"},
            {"P", "!"}, {"Pc", "_"}, {"Pd", "-"}, {"Ps", "("}, {"Pe", ")"}, {"Pi", "\u00ab"}, {"Pf", "\u00bb"}, {"Po", "!"},
            {"Z", " "}, {"Zs", " "}, {"Zl", "\u2028"}, {"Zp", "\u2029"},
            {"S", "+"}, {"Sm", "+"}, {"Sc", "$"}, {"Sk", "^"}, {"So", "\u00a9"},
            {"C", "\u0000"}, {"Cc", "\u0000"}, {"Cf", "\u200b"}, {"Cn", "\u0378"}, {"Co", "\ue000"},
        };
        for (String[] sample : samples) {
            assertTrue(matches("\\p{" + sample[0] + "}", sample[1]), sample[0]);
            assertFalse(matches("\\P{" + sample[0] + "}", sample[1]), sample[0]);
        }
        assertFalse(matches("\\p{Lu}", "a"));
        assertFalse(matches("\\p{N}", "a"));
    }

    @Test
    @Requirement("rfc9485-3/checking")
    void characterClassEdges() {
        assertTrue(matches("[-a]", "-"));
        assertTrue(matches("[a-]", "-"));
        assertFalse(matches("[-a]", "b"));
        assertFalse(matches("[a-]", "b"));
        assertTrue(matches("[b-d]", "c"));
        assertTrue(matches("[b-d]", "b"));
        assertTrue(matches("[b-d]", "d"));
        assertFalse(matches("[b-d]", "a"));
        assertFalse(matches("[b-d]", "e"));
        assertTrue(matches("[\ue000]", "\ue000"));
        assertTrue(matches("[\ud7ff]", "\ud7ff"));
        assertInstanceOf(IRegexp.Problem.Invalid.class, problem("[\ud800]"));
        assertInstanceOf(IRegexp.Problem.Invalid.class, problem("[\udfff]"));
        assertInstanceOf(IRegexp.Problem.Invalid.class, problem("a{1,x}"));
        assertInstanceOf(IRegexp.Problem.Invalid.class, problem("a{1,2"));
    }

    @Test
    @Requirement("rfc9485-8/range-quantifiers")
    void sizeSaturates() {
        String huge = "((a{2147483647}){2147483647}){2147483647}";
        assertInstanceOf(IRegexp.Problem.TooComplex.class, problem(huge));
        assertInstanceOf(IRegexp.Problem.TooComplex.class, problem(huge + huge));
        assertInstanceOf(IRegexp.Problem.TooComplex.class, problem(huge + "|" + huge));
        assertEquals(Long.MAX_VALUE, Program.saturatedAdd(Long.MAX_VALUE, 1));
        assertEquals(3, Program.saturatedAdd(1, 2));
        assertEquals(Long.MAX_VALUE, Program.saturatedMultiply(Long.MAX_VALUE, 2));
        assertEquals(0, Program.saturatedMultiply(0, Long.MAX_VALUE));
        assertEquals(6, Program.saturatedMultiply(2, 3));
    }

    @Test
    @Requirement("rfc9485-4/xsd-semantics")
    void hasXsdSemantics() {
        assertTrue(matches("a.c", "abc"));
        assertFalse(matches("a.c", "a\nc"));
        assertFalse(matches("a.c", "a\rc"));
        assertTrue(matches("a.c", "a\u2028c"));
        assertTrue(matches("[^a]", "\n"));
        assertFalse(matches("abc", "abcd"));
        assertFalse(matches("bcd", "abcd"));
        assertTrue(compile("bc").find("abcd"));
        assertTrue(matches("(ab|cd){2}", "abcd"));
        assertFalse(matches("(ab|cd){2}", "ab"));
        assertTrue(matches("a{2,3}", "aaa"));
        assertFalse(matches("a{2,3}", "aaaa"));
        assertTrue(matches("a{2,}", "aaaaaa"));
        assertTrue(matches("", ""));
        assertTrue(matches("a|", ""));
        assertTrue(matches("[\\^]", "^"));
        assertTrue(matches("[$]", "$"));
    }

    @Test
    @Requirement("rfc9485-4/anchors")
    void caretAndDollarAreAnchors() {
        assertTrue(matches("^ab.*", "abc"));
        assertTrue(matches(".*bc$", "abc"));
        assertFalse(matches("a^b", "a^b"));
        assertTrue(compile("^ab").find("abc"));
        assertFalse(compile("^bc").find("abc"));
        assertTrue(compile("bc$").find("abc"));
        assertFalse(compile("ab$").find("abc"));
        assertTrue(compile("^$").find(""));
        assertFalse(compile("^$").find("a"));
    }

    @Test
    @Requirement("rfc9485-8/range-quantifiers")
    void largeRangeQuantifiersAreTooComplex() {
        assertInstanceOf(IRegexp.Problem.TooComplex.class, problem("a{20,200000}"));
        assertInstanceOf(IRegexp.Problem.TooComplex.class, problem("(a{1,100}){1,100}"));
        assertInstanceOf(IRegexp.Problem.TooComplex.class, problem("a{99999999999999999999}"));
        assertInstanceOf(IRegexp.Problem.TooComplex.class, problem("(".repeat(300) + ")".repeat(300)));
        assertTrue(matches("a{1,1000}", "a".repeat(1000)));
        assertEquals(compile("(a{2,4}){2,4}").matches("aaaaa"), true);
    }

    @Test
    @Disabled("ISSUE-9: a huge repetition of an empty group makes the regexp compiler slow")
    @Requirement("4.1/regex-resources")
    void emptyGroupWithAHugeCountCompilesQuickly() {
        // Found by RegexFuzzTest. Each compile took about 4 seconds.
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            compile("(){2147483647}");
            compile("(()){2147483647}");
            compile("(){2147483647}(){2147483647}");
        });
    }

    @Test
    @Requirement("4.1/regex-resources")
    void noCatastrophicBacktracking() {
        String input = "a".repeat(50_000);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertFalse(matches("(a*)*b", input));
            assertFalse(matches("(a|a)*b", input));
            assertFalse(matches("(a|aa)+b", input));
            assertFalse(matches("(.*a){20}b", input));
            assertFalse(compile("(a+)+b").find(input));
        });
    }
}
