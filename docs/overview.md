jsonlens reads, queries, and changes JSON values in the representation of any JSON library. It
implements JSONPath ([RFC 9535](https://www.rfc-editor.org/rfc/rfc9535)), JSON Pointer
([RFC 6901](https://www.rfc-editor.org/rfc/rfc6901)), and JSON Patch
([RFC 6902](https://www.rfc-editor.org/rfc/rfc6902)) for one model of JSON values.

- **No JSON library binding.** You give jsonlens a [ca.marcusdunn.jsonlens.model.JsonModel]
  for your JSON type. It reads your values directly. It does not copy or convert them, and the
  results are your own node instances.
- **No exceptions.** Operations return a sealed [ca.marcusdunn.jsonlens.model.Result]: a value or an
  error record. You examine it with a `switch` and record patterns.
- **No runtime dependencies.** The JSpecify nullness annotations are necessary only at compile time.
- **Traceable.** Each requirement of the three RFCs has a test. The build fails when a requirement has
  no test.

## Quick start

{@snippet class=ca.marcusdunn.jsonlens.docs.QuickStartSnippets region=quick-start}

The parse and the evaluation are two steps. You can parse a query one time and apply it to many
values. [ca.marcusdunn.jsonlens.model.Result#flatMap(java.util.function.Function)] chains the two
steps:

{@snippet class=ca.marcusdunn.jsonlens.docs.QuickStartSnippets region=chained}

## Modules

| Module | Purpose | Runtime dependencies |
|---|---|---|
| [ca.marcusdunn.jsonlens.model] | [ca.marcusdunn.jsonlens.model.JsonModel] and the other model interfaces, [ca.marcusdunn.jsonlens.model.Result], and [ca.marcusdunn.jsonlens.model.Maybe] | none |
| [ca.marcusdunn.jsonlens.path.core] | The query syntax tree and [ca.marcusdunn.jsonlens.path.core.path.NormalizedPath] | model |
| [ca.marcusdunn.jsonlens.path.parser] | [ca.marcusdunn.jsonlens.path.parser.JsonPathParser]: query text to a validated query | path-core |
| [ca.marcusdunn.jsonlens.path.evaluator] | [ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator]: applies a query to a JSON value | path-core |
| [ca.marcusdunn.jsonlens.jackson] | [ca.marcusdunn.jsonlens.jackson.JacksonJsonModel] for Jackson 3 `JsonNode` | model, Jackson databind |
| [ca.marcusdunn.jsonlens.mapped] | [ca.marcusdunn.jsonlens.mapped.MappedJson]: a read-only model over UTF-8 bytes | model |
| [ca.marcusdunn.jsonlens.pointer] | [ca.marcusdunn.jsonlens.pointer.JsonPointer]: RFC 6901 JSON Pointer for the values of any model | model |
| [ca.marcusdunn.jsonlens.patch] | [ca.marcusdunn.jsonlens.patch.JsonPatch]: RFC 6902 JSON Patch, applied in place to any editable model | model, pointer |
| [ca.marcusdunn.jsonlens.testkit] | Tests for your own model: [ca.marcusdunn.jsonlens.testkit.JsonModelContract], [ca.marcusdunn.jsonlens.testkit.ModelVerifier], and [ca.marcusdunn.jsonlens.testkit.ComplianceKit]. Use it only in tests. | model, path-core, mapped, path-parser, path-evaluator, patch, JUnit Jupiter |

The Kotlin module `jsonlens-kotlinx-serialization` has models for kotlinx.serialization `JsonElement`
trees and `@Serializable` Kotlin objects. Javadoc cannot read Kotlin, so it is not in this
documentation: see its [Dokka pages](kotlinx/index.html). Its javadoc JAR has the same pages.

Use only the modules that you need. A validation library can use only the parser. An engine with its
own parser can use only the evaluator.

## Your own JSON type

Implement [ca.marcusdunn.jsonlens.model.JsonModel] for your node type. This model reads plain
`Map` and `List` values:

{@snippet class=ca.marcusdunn.jsonlens.docs.CustomModelSnippets region=model}

{@snippet class=ca.marcusdunn.jsonlens.docs.CustomModelSnippets region=use-model}

Test the model with the test kit. A contract test gives the model and a parser; it runs fixed
cases, [ca.marcusdunn.jsonlens.testkit.ModelVerifier] on random documents, and the RFC 9535 test suite:

{@snippet class=ca.marcusdunn.jsonlens.docs.TestkitSnippets region=contract}

## Errors are values

| Operation | Result type | Error type |
|---|---|---|
| [ca.marcusdunn.jsonlens.path.parser.JsonPathParser#parse(String)] | [ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery] | [ca.marcusdunn.jsonlens.path.parser.ParseError] |
| [ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator#evaluate(ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery, Object, ca.marcusdunn.jsonlens.model.JsonModel)] | `List<Node<N>>` | [ca.marcusdunn.jsonlens.path.evaluator.EvaluationError] |
| [ca.marcusdunn.jsonlens.path.parser.JsonPathParser#withFunctions(java.util.List)] | [ca.marcusdunn.jsonlens.path.parser.JsonPathParser] | [ca.marcusdunn.jsonlens.path.parser.FunctionRegistrationError] |
| [ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator#withFunctions(java.util.List)] | [ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator] | [ca.marcusdunn.jsonlens.path.evaluator.ExtensionError] |

All error types are sealed interfaces of records. A `switch` can match the errors that you want to
handle in a special way:

{@snippet class=ca.marcusdunn.jsonlens.docs.ParsingSnippets region=errors}

## Function extensions

RFC 9535 defines the functions `length`, `count`, `match`, `search`, and `value`. You can add
more functions. The parser needs the signature, and the evaluator needs the implementation:

{@snippet class=ca.marcusdunn.jsonlens.docs.ExtensionSnippets region=extension}

{@snippet class=ca.marcusdunn.jsonlens.docs.ExtensionSnippets region=register}

## Behavior that the RFC leaves open

| Subject | Decision |
|---|---|
| The order of object members | The order of [ca.marcusdunn.jsonlens.model.JsonModel#memberCursor(Object)]. The Jackson model gives document order. |
| Numbers outside the I-JSON range | They compare exactly, with `JsonModel.compareNumbers`. The exponent has no limit. |
| `^` and `$` in `match()` and `search()` | They are anchors, as the JSONPath Compliance Test Suite expects. |
| Regular expressions | jsonlens has its own I-Regexp (RFC 9485) implementation. It is checking and runs in linear time. |
| Very large results | A nodelist larger than the limit gives an overflow error. See [ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator.Limits]. |
