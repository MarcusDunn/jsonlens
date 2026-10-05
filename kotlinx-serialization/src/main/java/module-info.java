/**
 * jsonlens models for kotlinx.serialization: {@code KotlinxJsonModel} for {@code JsonElement} trees,
 * and {@code KotlinxObjectModel} for {@code @Serializable} Kotlin objects.
 *
 * <p>At runtime, this module requires the model module, kotlinx.serialization JSON, and the Kotlin
 * standard library.
 */
module ca.marcusdunn.jsonlens.kotlinx {
    requires static transitive org.jspecify;
    requires transitive ca.marcusdunn.jsonlens.model;
    requires transitive kotlinx.serialization.json;
    requires transitive kotlin.stdlib;

    exports ca.marcusdunn.jsonlens.kotlinx;
}
