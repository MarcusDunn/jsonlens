package ca.marcusdunn.jsonlens.path.parser;

import static ca.marcusdunn.jsonlens.path.parser.Queries.accepts;
import static ca.marcusdunn.jsonlens.path.parser.Queries.canonical;
import static ca.marcusdunn.jsonlens.path.parser.Queries.onlySegment;
import static ca.marcusdunn.jsonlens.path.parser.Queries.parse;
import static ca.marcusdunn.jsonlens.path.parser.Queries.reject;
import static ca.marcusdunn.jsonlens.path.parser.Queries.rejects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.core.query.Segment;
import ca.marcusdunn.jsonlens.path.core.query.Selector;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Sections 2.1, 2.2, and 2.5: the structure of a query. */
class QueryStructureTest {

    @Test
    @Requirement("2.1.1/root-then-segments")
    void rootFollowedByZeroOrMoreSegments() {
        assertEquals(new JsonPathQuery(List.of()), parse("$"));
        JsonPathQuery query = parse("$.a[0]..b");
        assertEquals(3, query.segments().size());
        assertEquals("$['a'][0]..['b']", query.toString());
    }

    @Test
    @Requirement("2.2.1/root-identifier")
    void queryMustStartWithTheRootIdentifier() {
        reject("", ParseError.UnexpectedEnd.class);
        reject("@.a", ParseError.UnexpectedCharacter.class);
        reject("a", ParseError.UnexpectedCharacter.class);
        reject(".a", ParseError.UnexpectedCharacter.class);
        reject("$$", ParseError.UnexpectedCharacter.class);
        reject("[0]", ParseError.UnexpectedCharacter.class);
    }

    @Test
    @Requirement("2.1.1/blank-space")
    void blankSpaceIsSpaceTabLineFeedAndCarriageReturn() {
        assertEquals("$['a'][0]", canonical("$ .a\t[0]"));
        assertEquals("$['a'][0]", canonical("$\n.a\r[0]"));
        assertEquals("$[0,1]", canonical("$[ 0 , 1 ]"));
        assertEquals("$..['a']", canonical("$ \t\n\r..a"));
        rejects(
                "$\u000b.a", // vertical tab
                "$\f.a", // form feed
                "$ .a", // no-break space
                "$ .a", // em space
                "$　.a", // ideographic space
                "$[ 0]");
    }

    @Test
    @Requirement("2.1.1/no-outer-blank-space")
    void noBlankSpaceAroundTheQuery() {
        rejects(" $", "$ ", "\t$", "$.a\n", "$[0] ", "\r\n$");
    }

    @Test
    @Requirement("2.1/well-formed")
    void rejectsQueriesThatAreNotWellFormed() {
        rejects(
                "$.",
                "$[",
                "$]",
                "$[0",
                "$['a'",
                "$['a]",
                "$.a.",
                "$[0]]",
                "$[0]x",
                "$[?]",
                "$[?@.a",
                "$[?(@.a]",
                "$[*,]",
                "$..",
                "$a",
                "$.a b");
    }

    @Test
    @Requirement("2.1/validity-independent-of-value")
    void validityNeedsNoJsonValue() {
        // The parser has no JSON value. The same text always gives the same result.
        assertEquals(parse("$[?length(@.a) > 1]"), parse("$[?length(@.a) > 1]"));
        assertEquals(reject("$[?length(@.*) > 1]"), reject("$[?length(@.*) > 1]"));
    }

    @Test
    @Requirement("2.5/segment-kinds")
    void childAndDescendantSegments() {
        assertInstanceOf(Segment.Child.class, onlySegment("$['a']"));
        assertInstanceOf(Segment.Child.class, onlySegment("$.a"));
        assertInstanceOf(Segment.Descendant.class, onlySegment("$..a"));
        assertInstanceOf(Segment.Descendant.class, onlySegment("$..['a']"));
    }

    @Test
    @Requirement("2.5.1.1/bracketed-selection")
    void bracketedSelection() {
        assertEquals(
                List.of(new Selector.Name("a"), new Selector.Index(1), new Selector.Wildcard()),
                onlySegment("$[ 'a' ,\t1 ,* ]").selectors());
        rejects("$[]", "$[ ]", "$['a',]", "$[,'a']", "$['a' 'b']", "$['a',,'b']");
    }

    @Test
    @Requirement("2.5.1.1/dot-wildcard")
    void dotWildcardIsShorthand() {
        assertEquals(parse("$[*]"), parse("$.*"));
        assertEquals(parse("$[*][*]"), parse("$.*.*"));
    }

    @Test
    @Requirement("2.5.1.1/member-name-shorthand")
    void memberNameShorthand() {
        assertEquals(parse("$['a']"), parse("$.a"));
        assertEquals("$['foo']['bar']", canonical("$.foo.bar"));
        accepts(
                "$._",
                "$.a1",
                "$.A_z9",
                "$.é",
                "$.\u0080",
                "$.퟿",
                "$.",
                "$.中文",
                "$.😀",
                "$..é");
        rejects("$.1a", "$.-a", "$.a-b", "$.$", "$.'a'", "$.\u007f", "$.a.b.", "$.@");
    }

    @Test
    @Requirement("2.5.1.1/no-blank-after-dot")
    void noBlankAfterDot() {
        rejects("$. a", "$.\ta", "$. *", "$.\n*");
    }

    @Test
    @Requirement("2.5.2.1/syntax")
    void descendantSegmentSyntax() {
        assertEquals(List.of(new Selector.Name("a")), onlySegment("$..a").selectors());
        assertEquals(List.of(new Selector.Wildcard()), onlySegment("$..*").selectors());
        assertEquals(List.of(new Selector.Index(0)), onlySegment("$..[0]").selectors());
        assertEquals(List.of(new Selector.Name("a"), new Selector.Index(1)), onlySegment("$..['a',1]").selectors());
        rejects("$.. a", "$..\t[0]", "$...a", "$.. *", "$..'a'");
    }

    @Test
    @Requirement("2.5.2.1/bare-double-dot")
    void bareDoubleDotIsRejected() {
        rejects("$..", "$.a..", "$..[", "$..[0].. ", "$[?@..]");
    }

    @Test
    @Requirement("2.3/selector-kinds")
    void fiveSelectorKinds() {
        List<Selector> selectors = onlySegment("$['a',*,1,1:2,?@]").selectors();
        assertInstanceOf(Selector.Name.class, selectors.get(0));
        assertInstanceOf(Selector.Wildcard.class, selectors.get(1));
        assertInstanceOf(Selector.Index.class, selectors.get(2));
        assertInstanceOf(Selector.Slice.class, selectors.get(3));
        assertInstanceOf(Selector.Filter.class, selectors.get(4));
        rejects("$[a]", "$[@]", "$[$]", "$[true]", "$[(0)]");
    }

    @Test
    @Requirement("2.3.2.1/syntax")
    void wildcardSyntax() {
        assertEquals(List.of(new Selector.Wildcard()), onlySegment("$[*]").selectors());
        rejects("$[**]", "$[*1]", "$.**");
    }
}
