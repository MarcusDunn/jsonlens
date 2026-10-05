package ca.marcusdunn.jsonlens.benchmarks

import kotlinx.serialization.Serializable

/** The benchmark file as Kotlin objects, for the benchmarks of KotlinxObjectModel. See BenchmarkData. */
@Serializable
data class Items(val items: List<Item>)

/** One record of the benchmark file. */
@Serializable
data class Item(
    val id: Long,
    val name: String,
    val category: String,
    val price: Double,
    val stock: Int,
    val available: Boolean,
    val tags: List<String>,
    val ratings: List<Int>,
    val author: Author,
    val note: String?,
)

/** The author of a record. */
@Serializable
data class Author(val first: String, val last: String)
