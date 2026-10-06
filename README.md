# jsonlens

> This is a vibecoded project - I attempted to ensure quality through a tracability matrix for all implemented RFCs in addition to 100% test coverage, including mutation.

`jsonlens` implements several RFCs with a JSON library agnostic core, it has a focus on correctness and minimal copies.

API documentation: <https://jsonlens.marcusdunn.ca> (Kotlin: <https://jsonlens.marcusdunn.ca/kotlinx/>). For coding agents: <https://jsonlens.marcusdunn.ca/llms.txt>.

## Implemented RFCs

- JSONPath, [RFC 9535](https://www.rfc-editor.org/info/rfc9535): queries;
- JSON Pointer, [RFC 6901](https://www.rfc-editor.org/info/rfc6901): the location of one value;
- JSON Patch, [RFC 6902](https://www.rfc-editor.org/info/rfc6902): changes, in place or to a copy;
- JSON Merge Patch, [RFC 7396](https://www.rfc-editor.org/info/rfc7396): changes as a partial document, in place or to a copy.

## Modules

| Artifact | Contents | Runtime dependencies |
|---|---|---|
| `jsonlens-model` | `JsonModel`, `JsonFactory`, the JSON string and number types, `Result`, `Maybe` | none |
| `jsonlens-path-core` | The query syntax tree and `NormalizedPath` | model |
| `jsonlens-path-parser` | `JsonPathParser`: query text to a validated `JsonPathQuery` | path-core |
| `jsonlens-path-evaluator` | `JsonPathEvaluator`: applies a `JsonPathQuery` to a JSON value | path-core |
| `jsonlens-jackson` | `JacksonJsonModel` for Jackson 3 `JsonNode` trees, and `JacksonStream` for Jackson streaming parsers | model, Jackson databind |
| `jsonlens-kotlinx-serialization` | `KotlinxJsonModel` for kotlinx.serialization `JsonElement` trees | model, kotlinx.serialization, Kotlin |
| `jsonlens-mapped` | `MappedJson`: a read-only model over UTF-8 bytes, for example a memory-mapped file, with no copies of values | model |
| `jsonlens-pointer` | `JsonPointer`: RFC 6901 JSON Pointer for the values of any `JsonModel` | model |
| `jsonlens-patch` | `JsonPatch`: RFC 6902 JSON Patch, applied in place to any editable `JsonModel` | model, pointer |
| `jsonlens-merge-patch` | `JsonMergePatch`: RFC 7396 JSON Merge Patch, merged in place into any editable `JsonModel`, or into a copy | model |
| `jsonlens-model-testkit` | Tests for your own `JsonModel`: contract tests, `ModelVerifier`, `RandomJson`, and the RFC 9535 and RFC 6902 test suites (`ComplianceKit`). Use it only in tests. | model, path-core, mapped, path-parser, path-evaluator, patch, JUnit Jupiter |
| `jsonlens-bom` | A bill of materials with the versions of all modules | |

## Usage

```java
JsonPathQuery query = switch (JsonPathParser.standard().parse("$.store.book[?@.price < 10].title")) {
    case Result.Ok(var value) -> value;
    case Result.Err(var error) -> throw new IllegalArgumentException(error.message());
};

JsonNode root = JsonMapper.builder().build().readTree(json);
switch (JsonPathEvaluator.standard().evaluate(query, root, JacksonJsonModel.INSTANCE)) {
    case Result.Ok(var nodes) -> nodes.forEach(node -> System.out.println(node.path() + " = " + node.value()));
    case Result.Err(var error) -> System.err.println(error.message());
}
```
