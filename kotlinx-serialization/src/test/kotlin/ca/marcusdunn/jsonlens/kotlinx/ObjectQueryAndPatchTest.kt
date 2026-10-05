package ca.marcusdunn.jsonlens.kotlinx

import ca.marcusdunn.jsonlens.path.evaluator.EvaluationError
import ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator
import ca.marcusdunn.jsonlens.path.evaluator.Node
import ca.marcusdunn.jsonlens.model.Result
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser
import ca.marcusdunn.jsonlens.patch.JsonPatch
import ca.marcusdunn.jsonlens.patch.PatchError
import ca.marcusdunn.jsonlens.testsupport.Requirement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@Serializable
data class OrderLine(val sku: String, val quantity: Int, val unitPrice: Double)

@Serializable
data class Order(val id: Long, val customer: String, val lines: List<OrderLine>, val status: Color = Color.RED)

class ObjectQueryAndPatchTest {

    private val order = Order(42, "Ada", listOf(OrderLine("a", 1, 9.5), OrderLine("b", 3, 2.0), OrderLine("c", 12, 0.25)))

    @Test
    @Requirement("lib/kotlinx-object-model")
    fun jsonPathQueriesGiveYourOwnObjects() {
        val model = KotlinxObjectModel(Json)
        val query = (JsonPathParser.standard().parse("$.lines[?@.quantity > 2]") as Result.Ok).value()
        val nodes = (JsonPathEvaluator.standard().evaluate(query, model.node(order, Order.serializer()), model)
            as Result.Ok<List<Node<KotlinxNode>>, EvaluationError>).value()
        assertEquals(listOf("$['lines'][1]", "$['lines'][2]"), nodes.map { it.path().toString() })
        assertSame(order.lines[1], nodes[0].value().value)
        assertSame(order.lines[2], nodes[1].value().value)
    }

    @Test
    @Requirement("lib/kotlinx-object-model")
    fun aTypedPatchEncodesPatchesAndDecodes() {
        val json = Json
        val patch = (JsonPatch.parse(json.parseToJsonElement("""
            [{"op": "replace", "path": "/lines/0/quantity", "value": 2},
             {"op": "remove", "path": "/lines/2"},
             {"op": "test", "path": "/customer", "value": "Ada"}]"""), KotlinxJsonModel) as Result.Ok).value()

        // Encode, patch a copy, and decode: the decoder checks the types of the result.
        val patched = patch.applyToCopy(json.encodeToJsonElement(Order.serializer(), order), KotlinxJsonModel)
            .map { json.decodeFromJsonElement(Order.serializer(), it) }
        assertEquals(Result.ok<Order, PatchError>(order.copy(lines = listOf(OrderLine("a", 2, 9.5), OrderLine("b", 3, 2.0)))), patched)

        // A patch that gives a value of the wrong type fails in the decoder.
        val wrongType = (JsonPatch.parse(json.parseToJsonElement("""[{"op": "replace", "path": "/id", "value": "x"}]"""),
            KotlinxJsonModel) as Result.Ok).value()
        val element: JsonElement = (wrongType.applyToCopy(json.encodeToJsonElement(Order.serializer(), order), KotlinxJsonModel)
            as Result.Ok).value()
        assertEquals(true, runCatching { json.decodeFromJsonElement(Order.serializer(), element) }.isFailure)
    }
}
