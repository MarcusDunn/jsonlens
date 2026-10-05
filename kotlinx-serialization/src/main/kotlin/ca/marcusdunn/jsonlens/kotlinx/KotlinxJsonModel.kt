package ca.marcusdunn.jsonlens.kotlinx

import ca.marcusdunn.jsonlens.model.Maybe
import ca.marcusdunn.jsonlens.model.JsonDecimal
import ca.marcusdunn.jsonlens.model.JsonFactory
import ca.marcusdunn.jsonlens.model.JsonKind
import ca.marcusdunn.jsonlens.model.JsonModel
import ca.marcusdunn.jsonlens.model.JsonNumber
import ca.marcusdunn.jsonlens.model.JsonString
import ca.marcusdunn.jsonlens.model.MemberCursor
import ca.marcusdunn.jsonlens.model.Property
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

/**
 * A [JsonModel] for kotlinx.serialization [JsonElement] values.
 *
 * The model reads the elements of the tree directly. It does not copy them. A number keeps the
 * exact text of the JSON input, so [numberValue] is exact.
 *
 * Every JSON number has the kind [JsonKind.NUMBER]. [numberValue] keeps the text of the number and reads
 * it only when the evaluator needs the exact value.
 *
 * A [JsonPrimitive] that is not a string can hold text that is not a JSON literal, for example
 * `NaN` from `JsonPrimitive(Double.NaN)` or the text of `JsonUnquotedLiteral`. The model
 * classifies such a primitive as a [JsonKind.STRING] with its text.
 */
public object KotlinxJsonModel : JsonModel<JsonElement>, JsonFactory<JsonElement> {

    private val jsonNumber = Regex("""-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][-+]?[0-9]+)?""")

    /** An integer with at most 18 digits always fits in a long. */
    private val shortInteger = Regex("""-?(0|[1-9][0-9]{0,17})""")

    override fun kind(node: JsonElement): JsonKind = when (node) {
        is JsonObject -> JsonKind.OBJECT
        is JsonArray -> JsonKind.ARRAY
        is JsonNull -> JsonKind.NULL
        is JsonPrimitive -> when {
            node.isString -> JsonKind.STRING
            node.content == "true" -> JsonKind.TRUE
            node.content == "false" -> JsonKind.FALSE
            jsonNumber.matches(node.content) -> JsonKind.NUMBER
            else -> JsonKind.STRING
        }
    }

    override fun arrayLength(array: JsonElement): Int = (array as JsonArray).size

    override fun element(array: JsonElement, index: Int): Maybe<JsonElement> =
        (array as JsonArray).getOrNull(index)?.let { Maybe.some(it) } ?: Maybe.none()

    override fun memberCount(`object`: JsonElement): Int = (`object` as JsonObject).size

    override fun member(`object`: JsonElement, name: JsonString): Maybe<JsonElement> =
        (`object` as JsonObject)[JsonString.copyOf(name)]?.let { Maybe.some(it) } ?: Maybe.none()

    /** A [JsonObject] is a map, so it cannot hold duplicate names. */
    override fun hasDuplicate(`object`: JsonElement, name: JsonString): Boolean = false

    override fun memberCursor(`object`: JsonElement): MemberCursor<JsonElement> =
        EntryCursor((`object` as JsonObject).entries.iterator(), JsonNull)

    override fun stringValue(string: JsonElement): JsonString = JsonString.of((string as JsonPrimitive).content)

    override fun numberValue(number: JsonElement): JsonNumber {
        val text = (number as JsonPrimitive).content
        return if (shortInteger.matches(text)) JsonNumber.of(text.toLong()) else TextNumber(text)
    }

    override fun string(value: JsonString): JsonElement = JsonPrimitive(JsonString.copyOf(value))

    /** The text of the exact value keeps each number exact, also with a very large exponent. */
    @OptIn(ExperimentalSerializationApi::class)
    override fun number(value: JsonNumber): Maybe<JsonElement> = Maybe.some(JsonUnquotedLiteral(value.exactValue().toString()))

    override fun bool(value: Boolean): JsonElement = JsonPrimitive(value)

    override fun nullValue(): JsonElement = JsonNull

    override fun array(elements: List<JsonElement>): JsonElement = JsonArray(elements)

    override fun `object`(members: List<Property<JsonElement>>): JsonElement =
        JsonObject(members.associateTo(LinkedHashMap()) { JsonString.copyOf(it.name()) to it.value() })

    /**
     * A number that keeps the text of its JSON primitive. It reads the text only when the evaluator
     * needs the exact value. [kind] has checked that the text is a JSON number.
     */
    private class TextNumber(private val text: String) : JsonNumber {
        override fun exactValue(): JsonDecimal = JsonDecimal.parse(text).orElse(JsonDecimal.ZERO)
    }
}
