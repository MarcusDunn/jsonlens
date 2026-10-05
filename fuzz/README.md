# Fuzz tests

This project has coverage-guided fuzz tests. They use [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer),
which is based on libFuzzer. Jazzer measures the branch coverage of each input, and it changes
the inputs that find new branches.

| Fuzz test | Input | Properties |
|---|---|---|
| `ParserFuzzTest.parse` | Query bytes | The parser does not throw. Bytes and text give the same result. The text of each parsed query, from `toString()`, gives an equal query. |
| `EvaluatorFuzzTest.evaluate` | Query, NUL byte, JSON text | The evaluator does not throw and keeps its promises to the model. Jackson and kotlinx give the same result. Each Normalized Path in the result selects exactly that node (RFC 9535, Section 2.7). The text of the query gives the same result. |
| `RegexFuzzTest.compare` | Regexp, NUL byte, input | For each valid I-Regexp, `match()` and `search()` agree with `java.util.regex`, after the translation of RFC 9485, Section 5. |
| `PatchFuzzTest.patch` | JSON text, NUL byte, patch text | Pointer and patch do not throw. Strict Jackson and the mapped model read the same patch. The Jackson and Java collections targets give the same result or the same error, and applyToCopy on Jackson and kotlinx gives the same again without a change of its document. After an error, each target has its original value. The text and the URI fragment of each pointer give the same pointer again. |

## Regression mode

`./gradlew :fuzz:test` (and so `./gradlew check`) runs each fuzz test once for each input in
`src/test/resources/ca/marcusdunn/jsonlens/fuzz/<Test>Inputs/<method>/`. The inputs come from the
JSONPath Compliance Test Suite, from examples, and from problems that the fuzzer found.

## Fuzzing mode

Run one fuzz test at a time:

```sh
./gradlew :fuzz:fuzz --tests '*ParserFuzzTest*'
./gradlew :fuzz:fuzz --tests '*EvaluatorFuzzTest*'
./gradlew :fuzz:fuzz --tests '*RegexFuzzTest*'
./gradlew :fuzz:fuzz --tests '*PatchFuzzTest*'
```

Each run stops after the `maxDuration` of its `@FuzzTest`, or at the first problem. Jazzer keeps
the inputs that it finds in `.cifuzz-corpus/` (not in git), so the next run continues from them.

## When the fuzzer finds a problem

1. Jazzer writes the input to the `<Test>Inputs/<method>/` directory, with a name that starts
   with `crash-`. The input is then a regression test.
2. Add an issue to `ISSUES.md` at the root of the repository.
3. If the fix is not immediate, rename the input to `known-ISSUE-n-...` and move it to the directory
   `fuzz/known-issues/`, so that `check` passes. Move it back when you fix the issue.
