package ca.marcusdunn.jsonlens.pointer;

import static ca.marcusdunn.jsonlens.pointer.SyntaxTest.pointer;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import org.junit.jupiter.api.Test;

/// Tests that try to find places where the pointer does not conform to RFC 6901, or does not
/// keep the promises of the library.
class AdversarialConformanceTest {

    @Test
    @Requirement("rfc6901-6/uri-fragment")
    void percentEncodingsHaveOnlyAsciiHexDigits() {
        // RFC 6901, Section 6: "a JSON Pointer can be represented in a URI fragment identifier by
        // encoding it into octets using UTF-8 [RFC3629], while percent-encoding those characters
        // not allowed by the fragment rule in [RFC3986]."
        // RFC 3986, Section 2.1: "pct-encoded = "%" HEXDIG HEXDIG", and HEXDIG (RFC 5234,
        // Appendix B.1) is DIGIT / "A" / "B" / "C" / "D" / "E" / "F": ASCII characters only.
        // RFC 3986, Section 3.5: a fragment has only pchar, "/", and "?".
        // FULLWIDTH DIGIT FOUR and FULLWIDTH DIGIT ONE are not HEXDIG.
        assertEquals(Result.err(new PointerError.InvalidFragment(2)), JsonPointer.parseFragment("#/%４１"));
        // ARABIC-INDIC DIGIT TWO and ARABIC-INDIC DIGIT ZERO are not HEXDIG.
        assertEquals(Result.err(new PointerError.InvalidFragment(2)), JsonPointer.parseFragment("#/%٢٠"));
        // FULLWIDTH LATIN CAPITAL LETTER A is not HEXDIG.
        assertEquals(Result.err(new PointerError.InvalidFragment(2)), JsonPointer.parseFragment("#/%4Ａ"));
    }

    @Test
    @Requirement("rfc6901-7/errors")
    void anIndexAfterTheLargestIntIsOutOfRange() {
        // RFC 6901, Section 4: "array-index = %x30 / ( %x31-39 *(%x30-39) )". The token
        // "2147483648" has this form, so it is an array index, and the array has no element
        // with it. Section 7: "Error conditions include, but are not limited to: Invalid pointer
        // syntax; A pointer that references a nonexistent value." The library documents
        // InvalidIndex for a token that "is not an array index: digits without a leading zero",
        // and IndexOutOfRange for "an array index [that] is not less than the length of the
        // array". The cause here is IndexOutOfRange.
        for (Documents.Document<?> document : Documents.all("[1]")) {
            assertEquals(Result.err(new PointerError.IndexOutOfRange(JsonPointer.root(), "2147483648")),
                    document.resolve(pointer("/2147483648")));
            assertEquals(Result.err(new PointerError.IndexOutOfRange(JsonPointer.root(), "99999999999")),
                    document.resolve(pointer("/99999999999")));
        }
    }
}
