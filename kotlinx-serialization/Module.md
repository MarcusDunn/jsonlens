# Module jsonlens-kotlinx-serialization

jsonlens models for kotlinx.serialization. With them, the JSONPath evaluator, JSON Pointer, and JSON
Patch of jsonlens work on Kotlin values.

- `KotlinxJsonModel` reads, builds, and copies `JsonElement` trees. `JsonPatch.applyToCopy`
  changes such a tree: it builds only the containers on the path of each change, and shares all
  other nodes.
- `KotlinxObjectModel` reads `@Serializable` Kotlin objects directly, through their serializers.
  It encodes one level of a value only when a query visits it, so a query that reads one field does
  not encode the whole object.

At runtime, this module requires the jsonlens model module, kotlinx.serialization JSON, and the
Kotlin standard library.

# Package ca.marcusdunn.jsonlens.kotlinx

The models `KotlinxJsonModel` and `KotlinxObjectModel`, and the node type `KotlinxNode` of
`KotlinxObjectModel`.
