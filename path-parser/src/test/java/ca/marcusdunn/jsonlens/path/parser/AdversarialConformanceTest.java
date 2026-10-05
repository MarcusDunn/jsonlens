package ca.marcusdunn.jsonlens.path.parser;

import static ca.marcusdunn.jsonlens.path.parser.Queries.accepts;

import ca.marcusdunn.jsonlens.testsupport.Requirement;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Adversarial conformance findings for the parser. Each test asserts the behavior that RFC 9535
 * requires. These tests fail against the current code.
 */
class AdversarialConformanceTest {

    /**
     * RFC 9535, Section 2.3.5.1: {@code number = (int / "-0") [ frac ] [ exp ]} and
     * {@code exp = "e" [ "-" / "+" ] 1*DIGIT}. The grammar sets no limit on the exponent.
     *
     * <p>RFC 9535, Section 2.1: "A string is a well-formed JSONPath query if it conforms to the
     * ABNF syntax in this document." Only index values and slice parameters must be in the I-JSON
     * range (item 1 of the validity rules). Number literals have no range rule.
     *
     * <p>Each literal below has the value zero, which is in the I-JSON range. Thus the query is
     * well-formed and valid, and no overflow can occur.
     *
     * <p>Expected: the parser accepts the queries. Actual: the parser returns
     * {@code ParseError.NumberOutOfRange}, because {@code new BigDecimal(text)} cannot hold a scale
     * outside the {@code int} range.
     */
    @Disabled("ISSUE-2: zero with a very large exponent is rejected")
    @Test
    @Requirement("2.3.5.1/number-syntax")
    void acceptsZeroWithAnExponentOutsideTheIntRange() {
        accepts(
                "$[?@ == 0e99999999999]",
                "$[?@ == 0e-99999999999]",
                "$[?@ == -0.0e2147483648]",
                "$[?@ < 0E+99999999999]");
    }
}
