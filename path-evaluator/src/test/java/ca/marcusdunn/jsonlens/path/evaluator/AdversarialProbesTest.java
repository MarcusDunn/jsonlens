package ca.marcusdunn.jsonlens.path.evaluator;

import static ca.marcusdunn.jsonlens.path.evaluator.Eval.paths;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Adversarial probes that showed conforming behavior. They pass and guard against regressions. */
class AdversarialProbesTest {

    /** Section 2.3.4.2.2: slices with the I-JSON extreme values do not overflow. */
    @Test
    @Requirement("2.3.4.2.2/bounds")
    void slicesAtTheIJsonExtremes() {
        assertEquals(List.of("$[2]"), paths("[0,1,2]", "$[::-9007199254740991]"));
        assertEquals(List.of("$[0]"), paths("[0,1,2]", "$[-9007199254740991:9007199254740991:9007199254740991]"));
        assertEquals(List.of("$[2]", "$[1]", "$[0]"), paths("[0,1,2]", "$[9007199254740991:-9007199254740991:-1]"));
        assertEquals(List.of(), paths("[0,1,2]", "$[-9007199254740991]"));
    }

    /** Section 2.3.5.2.2: U+FFFF is less than U+1F600, although its UTF-16 code unit is larger. */
    @Test
    @Requirement("2.3.5.2.2/string-ordering")
    void stringsCompareByScalarValues() {
        assertEquals(List.of("$[0]"), paths("[\"\\uffff\", \"\\ud83d\\ude00\"]", "$[?@ < '\\ud83d\\ude00']"));
    }

    /** Sections 2.4.1 and 2.3.5.2.2: Nothing from value() equals Nothing from a missing member. */
    @Test
    @Requirement("2.3.5.2.2/empty-equality")
    void nothingEqualsNothing() {
        assertEquals(List.of("$[0]", "$[1]"), paths("[1, null]", "$[?value(@..*) == $.missing]"));
        assertEquals(List.of(), paths("[1, null]", "$[?@ == $.missing]"));
    }

    /** RFC 9485, Section 4 and XSD: "." excludes only \n and \r, not U+0085 or U+2028. */
    @Test
    @Requirement("rfc9485-4/xsd-semantics")
    void dotExcludesOnlyLineFeedAndCarriageReturn() {
        assertEquals(List.of("$[0]", "$[1]"), paths("[\"a\\u0085b\", \"a\\u2028b\", \"a\\nb\", \"a\\rb\"]",
                "$[?match(@, 'a.b')]"));
    }
}
