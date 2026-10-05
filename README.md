# jsonlens

jsonlens reads, queries, and changes JSON values in the representation of any JSON library. It
implements three standards exactly, for one model of JSON values:

- JSONPath, [RFC 9535](https://www.rfc-editor.org/info/rfc9535): queries;
- JSON Pointer, [RFC 6901](https://www.rfc-editor.org/info/rfc6901): the location of one value;
- JSON Patch, [RFC 6902](https://www.rfc-editor.org/info/rfc6902): changes, in place or to a copy.

The properties:

- It does not bind to a JSON library. You give it a `JsonModel` for your JSON type, and it reads
  your values directly. It does not copy or convert them.
- It does not throw exceptions. Operations return a sealed `Result` of a value or an error record.
- It has no runtime dependencies. The JSpecify annotations are necessary only at compile time.
- Each requirement of the RFCs has a test. The build fails when a requirement has no test.

## Modules

| Artifact | Contents | Runtime dependencies |
|---|---|---|
| `jsonlens-model` | `JsonModel`, `JsonFactory`, the JSON string and number types, `Result`, `Maybe` | none |
| `jsonlens-path-core` | The query syntax tree and `NormalizedPath` | model |
| `jsonlens-path-parser` | `JsonPathParser`: query text to a validated `JsonPathQuery` | path-core |
| `jsonlens-path-evaluator` | `JsonPathEvaluator`: applies a `JsonPathQuery` to a JSON value | path-core |
| `jsonlens-jackson` | `JacksonJsonModel` for Jackson 3 `JsonNode` trees, and `JacksonStream` for Jackson streaming parsers | model, Jackson databind |
| `jsonlens-kotlinx-serialization` | `KotlinxJsonModel` for kotlinx.serialization `JsonElement`, and `KotlinxObjectModel` for `@Serializable` Kotlin objects | model, kotlinx.serialization, Kotlin |
| `jsonlens-mapped` | `MappedJson`: a read-only model over UTF-8 bytes, for example a memory-mapped file, with no copies of values | model |
| `jsonlens-pointer` | `JsonPointer`: RFC 6901 JSON Pointer for the values of any `JsonModel` | model |
| `jsonlens-patch` | `JsonPatch`: RFC 6902 JSON Patch, applied in place to any editable `JsonModel` | model, pointer |
| `jsonlens-model-testkit` | Tests for your own `JsonModel`: contract tests, `ModelVerifier`, `RandomJson`, and the RFC 9535 and RFC 6902 test suites (`ComplianceKit`). Use it only in tests. | model, path-core, mapped, path-parser, path-evaluator, patch, JUnit Jupiter |
| `jsonlens-bom` | A bill of materials with the versions of all modules | |

Use only the modules that you need. For example, a validation library can use only the JSONPath
parser. An engine with its own parser can use only the evaluator. JSON Patch needs no JSONPath module.

All library modules are Java modules (JPMS). They require Java 21 or later.

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

Each result `Node` has your own node instance and its Normalized Path, for example
`$['store']['book'][2]['title']`.

### Your own JSON type

Implement `JsonModel<N>` for your node type `N`. It has six required methods: `kind`,
`arrayLength`, `element`, `memberCursor`, `stringValue`, and `numberValue`. The other methods have
defaults that use the six: `memberCount`, `member`, `members`, and `hasDuplicate` walk the member
cursor, and `equal` and `compareNumbers` compare values. Override a default only to make it faster;
the override must give the same result as the default. Read the contract in the Javadoc of
`JsonModel`. In summary:

- A model must not throw exceptions or return `null`.
- A missing member or element is `Maybe.None`. JSON null is a present node of kind `NULL`. If your
  representation uses Java `null` for JSON null, use a sentinel object.
- The model chooses the representation of strings and numbers:
  - `stringValue` gives a `JsonString`, which gives the Unicode scalar values in order. For a
    `String`, use `JsonString.of(value)`; it does not copy. A model over bytes can decode lazily.
  - `numberValue` gives a `JsonNumber`, which gives its exact value as a `JsonDecimal`: a sign,
    the significant digits, and an exponent with no limit. For the common types, use
    `JsonNumber.of(...)` (`int`, `long`, `BigInteger`, `BigDecimal`, `JsonDecimal`, `double`).
  - Member names are also `JsonString` values, in the cursor, `member`, and `Property`. A model over
    bytes gives its names without a decoded copy. A Normalized Path keeps the name of the model,
    and decodes it only when you read the path.
- The model also owns the order of numbers, as a `Comparator` does. The default
  `compareNumbers(a, b)` compares the exact values. A model can override it to compare its own
  numbers without a conversion. The result must have the same sign as
  `a.exactValue().compareTo(b.exactValue())`. The contract tests check this invariant.

The member cursor walks the members of an object in order. The evaluator walks objects with it, so a
walk makes no stream, no list, and no `Property` for each member. For a small object, for example in
a test, `MemberCursor.of(properties)` makes a cursor over a list.

jsonlens promises never to call a model method with `null`, and to call a method that is
specific to a kind only for a node of that kind.

A model can also implement `JsonFactory<N>`, which builds nodes, and `JsonEditor<N>`, which changes
objects and arrays in place. A read-only model, for example a model over a memory-mapped file, does
not need them: all standard functions run without them.

| Interface | Jackson | kotlinx | mapped |
|---|---|---|---|
| `JsonModel` (read) | yes | yes | yes |
| `JsonFactory` (build) | yes | yes | no |
| `JsonEditor` (change in place) | yes | no (immutable) | no (read-only) |

The model also has shared operations that work across models, with no recursion:
`JsonModel.equal(...)` compares two values as RFC 9535 and RFC 6902 define equality, also when the
values are in two different models, and `JsonFactory.copyOf(...)` copies a value of any model.

#### Test your model

The module `jsonlens-model-testkit` tests a model for you. Extend `JsonModelContract` (or
`JsonFactoryContract`, or `JsonEditorContract` for a model that builds or changes values), and give
the model and a parser of your JSON library:

```java
class MyModelContractTest extends JsonModelContract<MyNode> {
    @Override protected JsonModel<MyNode> model() { return MyModel.INSTANCE; }
    @Override protected MyNode parse(String json) { return MyLibrary.parse(json); }
}
```

The contract test runs:

- fixed cases for each method, for example the seven kinds, exact numbers, and names that are not
  normalized;
- `ModelVerifier` on a corpus and on random documents from `RandomJson`. The verifier calls the
  methods of the model on each value of a document, and compares their results with each other and
  with the defaults. It reports each `Violation` with its rule and the Normalized Path of the value;
- the JSONPath Compliance Test Suite on the model, and, for a factory or an editor, the JSON Patch
  test suite, to a copy and in place.

You can also call `ModelVerifier.of(model).verify(root)` on your own documents, and
`ComplianceKit.jsonPath(model, parser)` in any JUnit test. All six models of this build pass the
kit, and so does the example model in the Javadoc of `JsonModel`.

### Jackson streams

`JacksonStream` reads a Jackson streaming `JsonParser` on demand, with no tree. A query reads only the
tokens that it needs: `$.first.id` stops after the first member `first`. The document keeps each value
that it has read, so a query can visit it again. The parser can read any format of Jackson, for
example JSON, CBOR, or Smile.

```java
JacksonStream stream = JacksonStream.open(parser).orElse(null);
var nodes = JsonPathEvaluator.standard().evaluate(query, stream.root(), stream.model());
if (stream.failure().isSome()) {
    // The parser failed during the query: the result came from a document that ended early.
}
```

A `JsonModel` method cannot report an error, so a parser error during a query ends the document at
that point and is kept in `failure()`. Check it after a query, or use `JacksonStream.readFully` to
check the whole input first. The model passes the JSONPath Compliance Test Suite, also for the values.

### Kotlin objects

`KotlinxObjectModel` reads your `@Serializable` Kotlin objects directly, through their serializers.
A JSONPath query then gives your own objects:

```kotlin
val model = KotlinxObjectModel(Json)
val lines = JsonPathEvaluator.standard().evaluate(query, model.node(order, Order.serializer()), model)
// each Node has a KotlinxNode whose value is your OrderLine
```

- **The view is the JSON of your `Json` instance.** It follows `encodeDefaults`, `explicitNulls`,
  the naming strategy, enums, value classes, and unsigned numbers. Polymorphic and contextual values,
  maps with structured keys, and serializers that write more than one level are read through
  `Json.encodeToJsonElement`, so the view is always the same as the JSON. The tests compare the view
  with `encodeToJsonElement` for many types and configurations.
- **Lazy, one level at a time.** When a query visits a node, its serializer writes only the direct
  children. A child object is read only when the query visits it.
- **Read-only.** To patch an object, encode it, use `JsonPatch.applyToCopy` with `KotlinxJsonModel`,
  and decode the result. The decoder checks the types of the patched value.

### Function extensions

There are two kinds of function extensions:

| Kind | Model | Signature |
|---|---|---|
| `FunctionExtension` (read-only) | any `JsonModel` | NodesType and LogicalType parameters. A ValueType result is an existing node or Nothing. |
| `BuildingFunctionExtension` | `JsonModel` and `JsonFactory` | any signature. It can make new values. |

Register the signature with the parser and the implementation with the evaluator:

```java
Result<JsonPathParser, FunctionRegistrationError> parser = JsonPathParser.withFunctions(List.of(First.SIGNATURE));
Result<JsonPathEvaluator, ExtensionError> evaluator = JsonPathEvaluator.withFunctions(List.of(new First()));

// With building extensions, the evaluator accepts only models that implement JsonFactory.
Result<BuildingEvaluator, ExtensionError> building =
        JsonPathEvaluator.withFunctions(List.of(new First()), List.of(new Upper()));
```

The standard functions `length`, `count`, `match`, `search`, and `value` are always available, with
each model.

### JSON Pointer and JSON Patch

The `pointer` module implements JSON Pointer ([RFC 6901](https://www.rfc-editor.org/info/rfc6901)),
and the `patch` module implements JSON Patch ([RFC 6902](https://www.rfc-editor.org/info/rfc6902)).
Both use the same models:

```java
JsonPointer pointer = JsonPointer.parse("/a~1b/0").orElse(JsonPointer.root());
Result<JsonNode, PointerError> value = pointer.resolve(document, JacksonJsonModel.INSTANCE);

Result<JsonNode, PatchError> patched = JsonPatch.parse(patchDocument, JacksonJsonModel.INSTANCE)
        .flatMap(patch -> patch.apply(document, JacksonJsonModel.INSTANCE));

// kotlinx trees are immutable: build a changed copy.
Result<JsonElement, PatchError> copy = JsonPatch.parse(patchDocument, JacksonJsonModel.INSTANCE)
        .flatMap(patch -> patch.applyToCopy(element, KotlinxJsonModel.INSTANCE));
```

- **Any model.** A pointer resolves against the values of any `JsonModel`. A patch document can be
  in any model, for example a memory-mapped file. The target of `apply` must be a `JsonModel`, a
  `JsonFactory`, and a `JsonEditor`, for example the Jackson model.
- **In place, and atomic.** A patch changes the target in place. If an operation fails, the patch
  reverses its changes, so the document has its original value (RFC 6902, Section 5). Only the
  order of the members in an object can be different, because that order is not significant.
- **Or as a copy.** `applyToCopy` needs only a `JsonModel` and a `JsonFactory`, so it also works for
  immutable kotlinx trees. It never changes the document: each change builds new containers on its
  path, and the result shares all other nodes with the document.
- **Minimal copies.** `move` moves a node, and `test` compares across models, with no copy. `add`
  and `replace` copy their value once into the target, and `copy` makes one deep copy, so that the
  target never shares a node with the patch document or with itself.
- **Duplicate names.** An operation must have exactly one `op` and one `path`. To detect two `op`
  members, read the patch with a model that keeps duplicate names (`MappedJson`), or with a parser
  that rejects them (Jackson with `StreamReadFeature.STRICT_DUPLICATE_DETECTION`). Jackson and
  kotlinx without that feature keep only one value, so the patch cannot see the duplicate.
- **Conformance.** The tests run the JSON Patch test suite (`spec/json-patch-tests`), and the
  examples of RFC 6901, on the Jackson, kotlinx, mapped, and Java collections models.

## Behavior that the RFC leaves open

The requirement catalog records each decision as an `option` row. The most important ones are:

- **Object member order.** The order of `JsonModel.memberCursor()`. The Jackson and kotlinx models give
  document order.
- **Numbers outside I-JSON.** They compare exactly, through `JsonModel.compareNumbers`, also with a very large exponent.
- **`^` and `$` in `match()` and `search()`.** They are anchors, as the JSONPath Compliance Test
  Suite expects. In strict XSD semantics (RFC 9485), they are ordinary characters.
- **Regular expressions.** jsonlens has its own I-Regexp implementation. It is checking, and it
  runs in linear time. A regular expression that is too large gives an overflow error.
- **Resource limits.** A nodelist larger than the limit gives an overflow error, not a wrong
  result. Set the limits with `JsonPathEvaluator.withLimits`.
- **Removal of the whole document** (JSON Patch). RFC 6902 does not define a `remove` with the
  path `""`. It is the error `RootRemoved`, because a document cannot be absent.

## Traceability

`spec/requirements.txt` is the requirement catalog. Each row has an identifier, a type, the module
that owns it, and a statement:

```
2.3.3.2/negative | requirement | evaluator | A negative index selects the element at the array length plus the index.
```

A test refers to a row with an annotation:

```java
@Test
@Requirement("2.3.3.2/negative")
void negativeIndex() { ... }
```

The `check` task of each module fails if a test refers to an unknown row, or if a `requirement` or
`option` row of the module has no test. Each module writes a traceability matrix to
`build/reports/traceability/requirements.md`.

The catalog has rows for RFC 9535, for RFC 9485 (I-Regexp), and for the promises of this library.
The tests also run the [JSONPath Compliance Test Suite](https://github.com/jsonpath-standard/jsonpath-compliance-test-suite),
in `spec/cts`, against both adapter models.

## Verify a release

Each release comes from the workflow `.github/workflows/release.yml`, from a tag `v<version>`. You
can check a release in four ways.

**The PGP signature.** Maven Central has a `.asc` signature for each file. The release key is
`Marcus Dunn <marcus@marcusdunn.ca>`, with the fingerprint
`6539 9874 EABF 904D C152  B8EF 4B50 1BA0 382F 9ADB`. Get it from the
Web Key Directory of `marcusdunn.ca`, and check that the fingerprint is the same:

```sh
gpg --auto-key-locate clear,wkd --locate-external-keys marcus@marcusdunn.ca
gpg --verify jsonlens-model-<version>.jar.asc jsonlens-model-<version>.jar
```

The key is also on `keyserver.ubuntu.com`, where Maven Central reads it.

In a Gradle build, add the fingerprint to the `trusted-keys` of your
`gradle/verification-metadata.xml`. Then Gradle checks the signature of each jsonlens artifact.

**The build provenance.** The release workflow attests each JAR, POM, and SBOM with a SLSA
provenance that Sigstore signs. The attestation names the commit and the workflow run that built the
file:

```sh
gh attestation verify jsonlens-model-<version>.jar --repo MarcusDunn/jsonlens
```

**A rebuild.** The build is reproducible: the same commit gives the same bytes. The Nix flake pins
the JDK and the tools.

```sh
git checkout v<version>
nix develop --command ./gradlew clean centralBundle
sha256sum build/staging-deploy/ca/marcusdunn/jsonlens/jsonlens-model/<version>/jsonlens-model-<version>.jar
```

Compare the checksum with the JAR on Maven Central. The signatures (`.asc`) differ, because each
signature has a time.

**The SBOM.** Each module has a CycloneDX SBOM with the classifier `cyclonedx`, for example
`jsonlens-jackson-<version>-cyclonedx.json`. It lists the module and its runtime dependencies. The
library modules have no runtime dependencies other than jsonlens modules; an adapter also requires
its JSON library.

## Development

The Nix flake supplies JDK 25 and Gradle 9. With direnv, run `direnv allow` one time. Without
direnv, run `nix develop`.

```sh
./gradlew build        # compile, test, and check traceability and coverage
./gradlew :path-parser:test # test one module
./gradlew javadocAll   # API documentation of all Java modules, in build/docs/javadoc
./gradlew clean centralBundle -Pversion=1.2.3  # the release bundle, in build/central-bundle.zip
```

The build uses:

- a version catalog (`gradle/libs.versions.toml`) and convention plugins (`build-logic`);
- the configuration cache and the build cache;
- Error Prone and NullAway, which treat warnings as errors;
- JDK 25 for the build, and Java 21 bytecode for the published artifacts;
- Gradle dependency verification (`gradle/verification-metadata.xml`): the build fails when a
  dependency or plugin does not have the recorded checksum or a trusted signature. After a change of
  a dependency, write the new entries, and review the diff before you commit it:
  `./gradlew --write-verification-metadata pgp,sha256 --export-keys assemble check`.

### Release

1. Set `version` in `gradle.properties` to the release version, without `-SNAPSHOT`, and commit.
2. Push a tag `v<version>`. The release workflow checks, builds, signs, and attests the release, and
   uploads the bundle to the Central Portal. Both jobs wait for approval in the environment
   `release`.
3. On central.sonatype.com, check the deployment, and publish it.

The repository needs the environment `release`, with required reviewers, the secrets
`SIGNING_KEY`, `SIGNING_PASSWORD`, and `CENTRAL_TOKEN`, and the variable `SIGNING_KEY_ID`. The
header of the workflow describes them.

### Extend the release key

The release key `marcus@marcusdunn.ca` has an offline primary key and a signing subkey for CI. Both
expire. Each week, the workflow `key-expiry.yml` reads the key from the Web Key Directory, and opens
an issue 90 days before the signing key expires. A release also stops when the signing key expires
within 30 days.

To extend the key, use the offline primary key:

```sh
gpg --quick-set-expire <FINGERPRINT> 3y                     # the primary key
gpg --quick-set-expire <FINGERPRINT> 3y <SUBKEY_FINGERPRINT> # the signing subkey
```

Then publish the new expiry in both places:

```sh
gpg --export --export-options export-minimal \
    --export-filter keep-uid="mbox = marcus@marcusdunn.ca" <FINGERPRINT> \
    > rt5udt498cuzmffxhbxas8eqhbr7wnae    # upload to https://marcusdunn.ca/.well-known/openpgpkey/hu/
gpg --keyserver keyserver.ubuntu.com --send-keys <FINGERPRINT>
```

Name the subkey by its fingerprint: `'*'` does not select a subkey that has already expired.

The fingerprint does not change, so the README, the trusted keys of users, and the CI secret stay
the same. Gradle does not read the expiry in the CI secret, and users read it from the published
key. Run the workflow `key-expiry.yml` by hand to check the result, and close the issue.

## Coverage

Each library module has 100 % line, branch, and mutation coverage. The `check` task fails if
any of them decreases:

- JaCoCo measures line and branch coverage. The report is in
  `<module>/build/reports/jacoco/test/html`.
- [PIT](https://pitest.org) changes the compiled code in small ways (mutants) and runs the tests.
  Each mutant must make a test fail. The report is in `<module>/build/reports/pitest`.

Each module must cover its own code with its own tests. To get 100 % without exclusions, the
code avoids constructs that make dead code or equivalent mutants:

- Record patterns with primitive components (for example `case Index(long i)`) are type patterns.
  javac adds exception handling to them that never runs.
- Methods that record an error do not return the result of the error method. They return `null`
  in a separate statement, so that a "return null" mutant is not equivalent.
- The Kotlin module compiles with `-Xno-call-assertions`. The null checks that Kotlin adds for
  Java results are dead code, because the Java APIs never return null.

The tests also check behavior that a mutant can change without a different result:
`StopTest` counts model calls to show that an evaluation stops after an overflow, and
`CheckingModel` fails a test if the evaluator asks for an array index outside the array.

## License

MIT. See `LICENSE`. The compliance test suite in `spec/cts` has its own BSD-2 license.
