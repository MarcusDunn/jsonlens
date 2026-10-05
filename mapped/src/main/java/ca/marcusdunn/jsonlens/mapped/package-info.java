/// A read-only JSON model over UTF-8 bytes, for example a memory-mapped file.
///
/// Open a file with [MappedJson#open(java.nio.file.Path)], or use bytes that are already in memory
/// with [MappedJson#of(java.nio.ByteBuffer)]. Then give [MappedJson#root()] and
/// [MappedJson#model()] to the evaluator:
///
/// ```java
/// switch (MappedJson.open(Path.of("store.json"))) {
///     case Result.Ok(var json) -> {
///         var nodes = JsonPathEvaluator.standard().evaluate(query, json.root(), json.model());
///     }
///     case Result.Err(var error) -> System.err.println(error.message());
/// }
/// ```
@NullMarked
package ca.marcusdunn.jsonlens.mapped;

import org.jspecify.annotations.NullMarked;
