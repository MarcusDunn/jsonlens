package ca.marcusdunn.jsonlens.kotlinx

import ca.marcusdunn.jsonlens.model.JsonDecimal
import ca.marcusdunn.jsonlens.model.JsonKind
import ca.marcusdunn.jsonlens.model.Maybe
import ca.marcusdunn.jsonlens.model.JsonModel
import ca.marcusdunn.jsonlens.model.JsonNumber
import ca.marcusdunn.jsonlens.model.JsonString
import ca.marcusdunn.jsonlens.testkit.JsonFactoryContract
import ca.marcusdunn.jsonlens.testsupport.ModuleDescriptors
import ca.marcusdunn.jsonlens.testsupport.Requirement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@Requirement("lib/kotlinx-contract")
class KotlinxJsonModelTest : JsonFactoryContract<JsonElement, KotlinxJsonModel>() {

    override fun factory(): KotlinxJsonModel = KotlinxJsonModel

    override fun parse(json: String): JsonElement = Json.parseToJsonElement(json)

    override fun readsAnyExponent(): Boolean = true

    @Test
    @Requirement("lib/kotlinx-dependencies")
    fun requiresOnlyCoreKotlinxAndStdlibAtRuntime() {
        assertEquals(
            setOf("ca.marcusdunn.jsonlens.model", "kotlinx.serialization.json", "kotlin.stdlib"),
            ModuleDescriptors.runtimeModules(ModuleDescriptors.named("ca.marcusdunn.jsonlens.kotlinx")),
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    @Requirement("lib/kotlinx-non-json-literals")
    fun classifiesLiteralsThatAreNotJson() {
        val model = model()
        for (element in listOf(
            JsonPrimitive(Double.NaN),
            JsonPrimitive(Double.NEGATIVE_INFINITY),
            JsonUnquotedLiteral("abc"),
            JsonUnquotedLiteral("01"),
        )) {
            assertEquals(JsonKind.STRING, model.kind(element), element.toString())
            assertEquals(element.toString(), JsonString.copyOf(model.stringValue(element)))
        }
        assertEquals(JsonKind.NUMBER, model.kind(JsonUnquotedLiteral("1e5")))
        assertEquals(JsonKind.STRING, model.kind(JsonPrimitive("1")))
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    @Requirement("lib/model-number-representation")
    fun everyJsonNumberIsANumber() {
        val model = model()
        val huge = JsonUnquotedLiteral("1e99999999999")
        assertEquals(JsonKind.NUMBER, model.kind(huge))
        assertEquals(exact("1e99999999999"), model.numberValue(huge).exactValue())
        assertEquals(exact("12.5"), model.numberValue(JsonPrimitive(12.5)).exactValue())
        assertEquals(exact("123456789012345678"), model.numberValue(JsonPrimitive(123456789012345678L)).exactValue())
        assertEquals(exact("1234567890123456789"), model.numberValue(JsonPrimitive(1234567890123456789L)).exactValue())
        assertEquals(0, model.compareNumbers(model.numberValue(JsonPrimitive(-7)), JsonNumber.of(-7)))
    }

    @Test
    @Requirement("lib/model-factory")
    fun buildsNumbersWithAnyExponent() {
        val huge = JsonNumber.of(exact("-1e99999999999"))
        val node = (KotlinxJsonModel.number(huge) as Maybe.Some<JsonElement>).value()
        assertEquals(JsonKind.NUMBER, KotlinxJsonModel.kind(node))
        assertEquals(huge.exactValue(), KotlinxJsonModel.numberValue(node).exactValue())
    }

    @Test
    fun numberValueOfANonNumberIsZero() {
        // Outside the contract of JsonModel, but without an exception.
        assertEquals(JsonDecimal.ZERO, KotlinxJsonModel.numberValue(JsonPrimitive("abc")).exactValue())
    }

    private fun exact(text: String): JsonDecimal = (JsonDecimal.parse(text) as Maybe.Some<JsonDecimal>).value()
}
