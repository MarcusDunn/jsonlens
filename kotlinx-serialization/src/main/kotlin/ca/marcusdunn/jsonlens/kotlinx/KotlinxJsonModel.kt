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

    override fun kind(node: JsonElement): JsonKind = when (node) {
        is JsonObject -> JsonKind.OBJECT
        is JsonArray -> JsonKind.ARRAY
        is JsonNull -> JsonKind.NULL
        is JsonPrimitive -> when {
            node.isString -> JsonKind.STRING
            node.content == "true" -> JsonKind.TRUE
            node.content == "false" -> JsonKind.FALSE
            numberText(node.content) != NumberText.NONE -> JsonKind.NUMBER
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

    /**
     * The number in its cheapest exact form: a `long`, a `double` that has exactly the value of
     * the text, or the text itself. The first two compare without a conversion.
     */
    override fun numberValue(number: JsonElement): JsonNumber {
        val text = (number as JsonPrimitive).content
        return when (numberText(text)) {
            NumberText.LONG -> JsonNumber.of(text.toLong())
            // NumberText.DOUBLE is finite, so the fallback is only for completeness.
            NumberText.DOUBLE -> JsonNumber.of(text.toDouble()).orElse(TextNumber(text))
            else -> TextNumber(text)
        }
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
