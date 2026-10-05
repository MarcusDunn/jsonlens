package ca.marcusdunn.jsonlens.path.parser;

import static ca.marcusdunn.jsonlens.path.parser.Queries.accepts;
import static ca.marcusdunn.jsonlens.path.parser.Queries.reject;
import static ca.marcusdunn.jsonlens.path.parser.Queries.rejects;
import static ca.marcusdunn.jsonlens.path.parser.Queries.selector;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.path.core.query.Selector;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Sections 2.3.3 and 2.3.4: index and slice selectors. */
class IndexAndSliceTest {

    private static final long MAX = 9007199254740991L;

    private static Selector.Slice slice(String query) {
        return (Selector.Slice) selector(query);
    }

    private static Selector.Slice slice(@Nullable Long start, @Nullable Long end, @Nullable Long step) {
        return new Selector.Slice(maybe(start), maybe(end), maybe(step));
    }

    private static Maybe<Long> maybe(@Nullable Long value) {
        return value == null ? Maybe.none() : Maybe.some(value);
    }

    @Test
    @Requirement("2.3.3.1/int-syntax")
    void indexSyntax() {
        assertEquals(new Selector.Index(0), selector("$[0]"));
        assertEquals(new Selector.Index(-1), selector("$[-1]"));
        assertEquals(new Selector.Index(10), selector("$[10]"));
        reject("$[01]", ParseError.InvalidInteger.class);
        reject("$[-0]", ParseError.InvalidInteger.class);
        reject("$[-01]", ParseError.InvalidInteger.class);
        rejects("$[+1]", "$[1.0]", "$[- 1]", "$[1e2]", "$[0x1]", "$[-]", "$[1 2]");
    }

    @Test
    @Requirement("2.3.3.1/ijson-range")
    @Requirement("2.1/ijson-integer-range")
    void indexInIjsonRange() {
        assertEquals(new Selector.Index(MAX), selector("$[9007199254740991]"));
        assertEquals(new Selector.Index(-MAX), selector("$[-9007199254740991]"));
        reject("$[9007199254740992]", ParseError.IntegerOutOfRange.class);
        reject("$[-9007199254740992]", ParseError.IntegerOutOfRange.class);
        reject("$[99999999999999999999999]", ParseError.IntegerOutOfRange.class);
    }

    @Test
    @Requirement("2.3.4.1/syntax")
    void sliceSyntax() {
        assertEquals(slice(1L, 3L, null), slice("$[1:3]"));
        assertEquals(slice(null, null, null), slice("$[:]"));
        assertEquals(slice(null, null, null), slice("$[::]"));
        assertEquals(slice(1L, null, null), slice("$[1:]"));
        assertEquals(slice(null, 2L, null), slice("$[:2]"));
        assertEquals(slice(null, null, 2L), slice("$[::2]"));
        assertEquals(slice(-1L, null, -1L), slice("$[-1::-1]"));
        assertEquals(slice(1L, 2L, null), slice("$[1:2:]"));
        assertEquals(slice(1L, 2L, 3L), slice("$[ 1 : 2 : 3 ]"));
        assertEquals(slice(1L, 2L, 3L), slice("$[1 :2: 3]"));
        accepts("$[0:0:0]", "$[1:2,3:4]");
        rejects("$[1:2:3:4]", "$[a:b]", "$[1:-0]", "$[:::]", "$[1:2:+3]", "$[1::-0]", "$[1:02]");
    }

    @Test
    @Requirement("2.3.4.1/ijson-range")
    @Requirement("2.1/ijson-integer-range")
    void sliceInIjsonRange() {
        assertEquals(slice(-MAX, MAX, -MAX), slice("$[-9007199254740991:9007199254740991:-9007199254740991]"));
        reject("$[9007199254740992:]", ParseError.IntegerOutOfRange.class);
        reject("$[:9007199254740992]", ParseError.IntegerOutOfRange.class);
        reject("$[::-9007199254740992]", ParseError.IntegerOutOfRange.class);
    }
}
