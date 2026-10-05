/**
 * An implementation of I-Regexp (RFC 9485) for match() and search().
 *
 * <p>The implementation is <em>checking</em> (RFC 9485, Section 3.1): it rejects a regexp that
 * does not agree with the ABNF of RFC 9485, Figure 1. It does not use {@code java.util.regex}.
 * It compiles a regexp into a program for a Pike virtual machine, which uses time linear in the
 * length of the input for each program instruction. A size limit on the program prevents
 * excessive resource use from range quantifiers (RFC 9485, Section 8).
 *
 * <p>This package is not exported.
 */
@NullMarked
package ca.marcusdunn.jsonlens.path.evaluator.iregexp;

import org.jspecify.annotations.NullMarked;
