# Module jsonlens-kotlinx-serialization

A jsonlens model for kotlinx.serialization. With it, the JSONPath evaluator, JSON Pointer, and JSON
Patch of jsonlens work on `JsonElement` trees.

`KotlinxJsonModel` reads, builds, and copies `JsonElement` trees. A `JsonElement` cannot change, so
use `JsonPatch.applyToCopy`: it builds only the containers on the path of each change, and shares
all other nodes. A number keeps the text of the JSON input, so its value is exact.

At runtime, this module requires the jsonlens model module, kotlinx.serialization JSON, and the
Kotlin standard library.

# Package ca.marcusdunn.jsonlens.kotlinx

The model `KotlinxJsonModel`.
