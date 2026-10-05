# Open issues

Each issue has an identifier. A test that shows the issue has `@Disabled("ISSUE-n: ...")`. To work
on an issue, remove `@Disabled` from its test, change the code until the test passes, and then
remove the issue from this file.

Sources:

- **Adversarial review:** an independent review against RFC 9535 and RFC 9485. Its tests are in
  `AdversarialConformanceTest` in the core, parser, and evaluator modules.
- **Fuzzing:** the coverage-guided fuzz tests in the `fuzz` project (see `fuzz/README.md`).

| Issue | Status | Area | Summary |
|---|---|---|---|
| ISSUE-1 | Open | core | Very deep hand-made queries cause `StackOverflowError` in `toString()`, `hashCode()`, and `equals()`. |
| ISSUE-2 | Open | parser | A number literal with the value zero and a very large exponent is rejected. |
| ISSUE-3 | Open | evaluator | The evaluator can reject a parsed query as too deep. |
| ISSUE-5 | Decision | evaluator | An invalid regexp with more than 256 nested groups gives an overflow, not LogicalFalse. |
| ISSUE-6 | Decision | evaluator | The size limit for regexps also applies to long patterns without range quantifiers. |
| ISSUE-7 | Open | parser | The error for a number literal with a very small exponent says "too large". |
| ISSUE-8 | External | build | JDK javadoc crashes on an external snippet in a module comment. |
| ISSUE-9 | Open | evaluator | A huge repetition of an empty group makes the regexp compiler slow. |

## ISSUE-1: Very deep hand-made queries overflow the stack

- **Requirement:** `lib/core-exception-free`.
- **Test:** `core/.../query/AdversarialConformanceTest.deepQueriesDoNotOverflowTheStack`.
- **Problem:** a query that a caller makes in code can have any depth. With 100,000 nested `Not`
  expressions, `JsonPathQuery.toString()` and the record methods `hashCode()` and `equals()` throw
  `StackOverflowError`. `QueryWriter` and the generated record methods use recursion.
- **Not affected:** a query from the parser. The parser accepts at most 256 levels.
- **Options:**
  1. Write `equals()`, `hashCode()`, and `toString()` without recursion for all syntax tree types,
     as `NormalizedPath` does.
  2. Limit the promise of the core module to queries with at most `MAX_QUERY_DEPTH` levels, and
     test that limit.
- **Recommendation:** option 2.

## ISSUE-2: Zero with a very large exponent is rejected

- **Requirement:** `2.3.5.1/number-syntax`.
- **Test:** `parser/.../AdversarialConformanceTest.acceptsZeroWithAnExponentOutsideTheIntRange`.
- **Problem:** `$[?@ == 0e99999999999]` gives `NumberOutOfRange`. The grammar of RFC 9535,
  Section 2.3.5.1 does not limit the exponent, and the value is zero. `new BigDecimal(text)` cannot
  hold the scale.
- **Fix:** when the digits before the exponent are all zero, the value is zero for each exponent.
  Or make `Literal.NumberLiteral` hold a `JsonDecimal`, which has no limit on the exponent. This
  also fixes ISSUE-7.

## ISSUE-3: The evaluator can reject a parsed query as too deep

- **Requirement:** `2.1/no-validity-errors-at-evaluation`.
- **Test:** `evaluator/.../AdversarialConformanceTest.aParsedQueryWithLogicalExtensionsDoesNotGiveQueryTooDeep`.
- **Problem:** the parser counts one level for each nested filter, parenthesis, or function call.
  The query validator of the evaluator counts one level for each logical expression and each
  call. With a LogicalType extension that calls itself 205 times, the parser accepts the query
  (206 levels), but the validator counts more than 1024 levels and reports `QueryTooDeep`.
  RFC 9535, Section 2.1 does not permit validity errors during evaluation. The Javadoc of
  `MAX_QUERY_DEPTH` says that a parsed query is always below the limit.
- **Fix:** make the validator count levels as the parser does, so that each parsed query is in
  the limit.

## ISSUE-5: Invalid regexp with deep groups gives an overflow

- **Requirement:** `rfc9485-8/range-quantifiers`, `2.4.6/invalid-iregexp`.
- **Problem:** `match(@, "((((…")` with more than 256 `(` gives `RegexTooComplex`. The regexp is not
  valid, and RFC 9535, Section 2.4.6 says that the result for an invalid regexp is LogicalFalse.
  RFC 9485, Section 8 permits limits only for range quantifiers.
- **Decision necessary:** check the grammar first (without a depth limit, without recursion), and
  give LogicalFalse for an invalid regexp. Or keep the current behavior as a resource limit
  (RFC 9535, Section 2.1).

## ISSUE-6: The regexp size limit applies to patterns without range quantifiers

- **Requirement:** `rfc9485-8/range-quantifiers`.
- **Problem:** a pattern of more than 10,000 ordinary characters gives `RegexTooComplex`. RFC 9485,
  Section 8 names only range quantifiers.
- **Decision necessary:** count only the expansion of range quantifiers against the limit, or keep
  one limit for the program size and document it.

## ISSUE-7: The error message for a tiny number says "too large"

- **Requirement:** `2.3.5.1/number-syntax`.
- **Problem:** `$[?@ == 1e-99999999999]` gives `NumberOutOfRange` with the message "The number ... is
  too large to represent". The number is very small.
- **Fix:** change the message to "cannot be represented". Also see ISSUE-2.

## ISSUE-8: JDK javadoc crashes on an external snippet in a module comment

- **Problem:** `{@snippet class=... region=...}` in a `module-info.java` comment crashes javadoc
  with `NullPointerException` in `SnippetTaglet` → `Utils.getLocationForPackage` →
  `JavacElements.getModuleOf`.
- **Versions:** the crash occurs with JDK 25.0.4, JDK 26.0.2, and JDK 27. No report in the OpenJDK
  bug system matches it.
- **Workaround:** the module comments have no external snippets. They refer to the package
  comments, which have the samples.
- **Next step:** report the bug to OpenJDK, with the minimal reproduction.

## ISSUE-9: A huge repetition of an empty group makes the regexp compiler slow

- **Requirement:** `4.1/regex-resources`, `rfc9485-8/range-quantifiers`.
- **Found by:** `RegexFuzzTest` (fuzzing). The inputs are in `fuzz/known-issues/ISSUE-9/`.
- **Test:** `evaluator/.../iregexp/IRegexpTest.emptyGroupWithAHugeCountCompilesQuickly`.
- **Problem:** the program of `()` has no instructions, so the size of `(){2147483647}` is 0 and
  the size check accepts it. Then the compiler runs its copy loop 2147483647 times. The compile
  takes about 4 seconds. A pattern from the JSON document (`search(@, $.regex)`) can be hostile,
  so a document can use much CPU time. RFC 9535, Section 4.1 and RFC 9485, Section 8 require
  protection against such inputs.
- **Fix:** a repetition of a body with no instructions is the empty regexp. Do not run the copy
  loop for it.

