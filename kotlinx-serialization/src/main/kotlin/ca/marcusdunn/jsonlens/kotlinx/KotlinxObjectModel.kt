package ca.marcusdunn.jsonlens.kotlinx

import ca.marcusdunn.jsonlens.model.JsonDecimal
import ca.marcusdunn.jsonlens.model.JsonKind
import ca.marcusdunn.jsonlens.model.JsonModel
import ca.marcusdunn.jsonlens.model.JsonNumber
import ca.marcusdunn.jsonlens.model.JsonString
import ca.marcusdunn.jsonlens.model.Maybe
import ca.marcusdunn.jsonlens.model.MemberCursor
import java.math.BigInteger
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.SerializersModule

/**
 * A read-only [JsonModel] over Kotlin objects, through their kotlinx.serialization serializers.
 *
 * The model reads your objects directly: the JSON view of a node is the JSON that
 * [Json.encodeToJsonElement] gives for it with the same [Json] instance, but the model does not
 * encode a whole tree. When a query visits a node, the serializer of the node writes its direct
 * children into a capturing encoder. A child object is kept as its value and its serializer, and
 * it is read only when a query visits it.
 *
 * ```kotlin
 * val model = KotlinxObjectModel(Json)
 * val nodes = JsonPathEvaluator.standard().evaluate(query, model.node(order, Order.serializer()), model)
 * val line = nodes.first().value().value // your own object
 * ```
 *
 * The view follows the configuration of the [Json] instance: `encodeDefaults`, `explicitNulls`,
 * the naming strategy, enums, value classes, and unsigned numbers. For a polymorphic or
 * contextual value, a map with structured keys, a serializer that writes more than one level, and
 * the class discriminator mode [ClassDiscriminatorMode.ALL_JSON_OBJECTS], the model reads that
 * part through [Json.encodeToJsonElement], so the view is always the JSON of the instance.
 *
 * The model is well-formed for values that [json] can encode. For a value that [json] cannot
 * encode, for example `Double.NaN` without `allowSpecialFloatingPointValues`, the view is not
 * defined, and a read can throw the exception of [json].
 *
 * The model is read-only: Kotlin objects are immutable. To patch an object, encode it, use
 * `JsonPatch.applyToCopy` with [KotlinxJsonModel], and decode the result.
 *
 * @property json the instance whose configuration gives the JSON view
 */
public class KotlinxObjectModel(public val json: Json = Json) : JsonModel<KotlinxNode> {

    /** Returns the node of a value, with its serializer, for example `Order.serializer()`. */
    public fun <T> node(value: T, serializer: SerializationStrategy<T>): KotlinxNode {
        @Suppress("UNCHECKED_CAST")
        val strategy = serializer as SerializationStrategy<Any?>
        return KotlinxNode(value, Capture(this, strategy, value))
    }

    override fun kind(node: KotlinxNode): JsonKind = node.shape().kind

    override fun arrayLength(array: KotlinxNode): Int = array.shape().elements.size

    override fun element(array: KotlinxNode, index: Int): Maybe<KotlinxNode> =
        array.shape().elements.getOrNull(index)?.let { Maybe.some(it) } ?: Maybe.none()

    override fun memberCount(`object`: KotlinxNode): Int = `object`.shape().members.size

    override fun member(`object`: KotlinxNode, name: JsonString): Maybe<KotlinxNode> =
        `object`.shape().members[JsonString.copyOf(name)]?.let { Maybe.some(it) } ?: Maybe.none()

    /** The members of a shape are a map, so they cannot hold duplicate names. */
    override fun hasDuplicate(`object`: KotlinxNode, name: JsonString): Boolean = false

    override fun memberCursor(`object`: KotlinxNode): MemberCursor<KotlinxNode> =
        EntryCursor(`object`.shape().members.entries.iterator(), `object`)

    override fun stringValue(string: KotlinxNode): JsonString = string.shape().string

    override fun numberValue(number: KotlinxNode): JsonNumber = number.shape().number

    /** The shape of a value: its serializer writes its direct children into a [LevelEncoder]. */
    internal fun capture(serializer: SerializationStrategy<Any?>, value: Any?): Shape {
        if (value is JsonElement) {
            // A JsonElement is its own JSON view. (Its serializer writes large numbers as Double.)
            return elementShape(value)
        }
        if (value == null || needsJson(serializer.descriptor)) {
            return elementShape(json.encodeToJsonElement(serializer, value))
        }
        val encoder = LevelEncoder(this)
        serializer.serialize(encoder, value)
        return encoder.shape() ?: elementShape(json.encodeToJsonElement(serializer, value))
    }

    /** Tells if the JSON of a value needs the encoder of [json], for example for a class discriminator. */
    @OptIn(ExperimentalSerializationApi::class)
    private fun needsJson(descriptor: SerialDescriptor): Boolean {
        val kind = descriptor.kind
        if (kind is PolymorphicKind || kind == SerialKind.CONTEXTUAL) {
            return true
        }
        return json.configuration.classDiscriminatorMode == ClassDiscriminatorMode.ALL_JSON_OBJECTS &&
            (kind == StructureKind.CLASS || kind == StructureKind.OBJECT)
    }

    /** The shape of a [JsonElement]: the view of a part that the model reads through [json]. */
    internal fun elementShape(element: JsonElement): Shape = when (element) {
        is JsonObject -> Shape.objectOf(element.entries.associateTo(LinkedHashMap()) { (name, child) ->
            name to KotlinxNode(child, ElementSource(this, child))
        })
        is JsonArray -> Shape.arrayOf(element.map { child -> KotlinxNode(child, ElementSource(this, child)) })
        else -> {
            val primitive = element as JsonPrimitive
            Shape(KotlinxJsonModel.kind(primitive), primitive.content, KotlinxJsonModel.numberValue(primitive))
        }
    }

    /** The JSON name of an element of a class, with the naming strategy of [json]. */
    @OptIn(ExperimentalSerializationApi::class)
    internal fun jsonName(descriptor: SerialDescriptor, index: Int): String {
        val name = descriptor.getElementName(index)
        return json.configuration.namingStrategy?.serialNameForJson(descriptor, index, name) ?: name
    }
}

/**
 * A node of a [KotlinxObjectModel]: a value of your object tree, and its place in the JSON view.
 *
 * @property value your own value: an object, a list, a map, a primitive, or `null`. Inside a part
 *     that the model reads through `Json.encodeToJsonElement`, the value of a node is its [JsonElement].
 */
public class KotlinxNode internal constructor(public val value: Any?, source: ShapeSource) {

    /** The [Shape] of the node, or the source that makes it. A read of a known shape takes no lock. */
    @Volatile
    private var state: ShapeSource = source

    /** The JSON view of the node, made once, on the first visit. The children are the same nodes each time. */
    internal fun shape(): Shape {
        val known = state
        return if (known is Shape) known else capture()
    }

    /**
     * Makes the shape under the lock, so that two threads that visit the node get the same
     * children. If another thread made the shape after the read in [shape], [state] is that
     * shape, and it resolves to itself.
     */
    @Synchronized
    private fun capture(): Shape {
        val shape = state.resolve()
        state = shape
        return shape
    }

    override fun toString(): String = "KotlinxNode[$value]"
}

/** A [Shape], or what makes the shape of a node on its first visit. */
internal abstract class ShapeSource {
    abstract fun resolve(): Shape
}

/** The shape of a value of your object tree: its serializer writes its direct children. */
internal class Capture(
    private val model: KotlinxObjectModel,
    private val serializer: SerializationStrategy<Any?>,
    private val value: Any?,
) : ShapeSource() {
    override fun resolve(): Shape = model.capture(serializer, value)
}

/** The shape of a [JsonElement]: the view of a part that the model reads through Json. */
internal class ElementSource(private val model: KotlinxObjectModel, private val element: JsonElement) : ShapeSource() {
    override fun resolve(): Shape = model.elementShape(element)
}

/**
 * The JSON view of a node: a scalar with its JSON text, an array, or an object.
 *
 * @param fixedText the text of a scalar, or `null` for a number whose text comes from [original]
 * @param original the Kotlin value of a number that has no fixed text: a `Long`, `Int`, `Short`,
 *     `Byte`, `Double`, or `Float`, whose `toString` is the text that Json writes
 */
internal class Shape(
    val kind: JsonKind,
    private val fixedText: String?,
    val number: JsonNumber,
    val elements: List<KotlinxNode> = listOf(),
    val members: Map<String, KotlinxNode> = mapOf(),
    private val original: Any? = null,
) : ShapeSource() {
    /** A shape is its own shape. */
    override fun resolve(): Shape = this

    /** The text of a scalar as Json writes it, for example as a map key. A number makes it only on request. */
    val text: String get() = fixedText ?: original.toString()

    /** The value of a string scalar. It is made when a reader asks for it, not for each scalar. */
    val string: JsonString get() = JsonString.of(text)

    companion object {
        private val ZERO: JsonNumber = JsonNumber.of(0)

        fun scalar(kind: JsonKind, text: String): Shape = Shape(kind, text, ZERO)

        fun number(value: JsonNumber, text: String): Shape = Shape(JsonKind.NUMBER, text, value)

        /** A number whose text is the `toString` of its Kotlin value, made only for a map key. */
        fun numberOf(value: JsonNumber, original: Any): Shape = Shape(JsonKind.NUMBER, null, value, original = original)

        fun arrayOf(elements: List<KotlinxNode>): Shape = Shape(JsonKind.ARRAY, "", ZERO, elements)

        fun objectOf(members: Map<String, KotlinxNode>): Shape = Shape(JsonKind.OBJECT, "", ZERO, members = members)
    }
}

/**
 * Captures the direct children of one value. The serializer of the value writes into this
 * encoder. A child that is not a scalar becomes a [KotlinxNode] that its own serializer reads
 * later, so the capture does not go deeper than one level.
 *
 * [shape] is `null` if the serializer wrote something that one level cannot show, for example a
 * structure inside a structure, or a structured map key. Then the model reads the value through
 * `Json.encodeToJsonElement`.
 */
@OptIn(ExperimentalSerializationApi::class, SealedSerializationApi::class)
internal class LevelEncoder(private val model: KotlinxObjectModel) : AbstractEncoder(), JsonEncoder {

    override val json: Json get() = model.json
    override val serializersModule: SerializersModule get() = model.json.serializersModule

    private var structured = false
    private var structure: SerialDescriptor = NO_STRUCTURE
    private var index = 0
    private var unsigned = false
    private var tooComplex = false
    private var scalar: Shape? = null
    // Made in beginStructure for the kind of the structure. A scalar needs neither.
    private var elements: MutableList<KotlinxNode> = java.util.Collections.emptyList()
    private var members: MutableMap<String, KotlinxNode> = java.util.Collections.emptyMap()
    private var expectingKey = true
    private var key = ""

    fun shape(): Shape? = when {
        tooComplex -> null
        !structured -> scalar
        structure.kind == StructureKind.LIST -> Shape.arrayOf(elements)
        else -> Shape.objectOf(members)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        if (structured) {
            // A structure inside the structure of this value: one level cannot show it.
            tooComplex = true
        }
        structured = true
        structure = descriptor
        if (descriptor.kind == StructureKind.LIST) {
            elements = ArrayList()
        } else {
            members = LinkedHashMap()
        }
        return this
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        this.index = index
        return true
    }

    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean =
        json.configuration.encodeDefaults

    override fun encodeInline(descriptor: SerialDescriptor): Encoder {
        unsigned = descriptor.serialName in UNSIGNED
        return this
    }

    override fun encodeNull() {
        // With explicitNulls = false, Json leaves out a property with the value null.
        if (structure.kind != StructureKind.CLASS || json.configuration.explicitNulls) {
            add(Shape.scalar(JsonKind.NULL, "null"), null)
        }
    }

    override fun encodeBoolean(value: Boolean) =
        add(Shape.scalar(if (value) JsonKind.TRUE else JsonKind.FALSE, value.toString()), value)

    override fun encodeByte(value: Byte) = integer(if (unsigned) value.toUByte().toLong() else value.toLong(), value)

    override fun encodeShort(value: Short) = integer(if (unsigned) value.toUShort().toLong() else value.toLong(), value)

    override fun encodeInt(value: Int) = integer(if (unsigned) value.toUInt().toLong() else value.toLong(), value)

    override fun encodeLong(value: Long) {
        if (unsigned) {
            val text = value.toULong().toString()
            add(Shape.number(JsonNumber.of(BigInteger(text)), text), value)
        } else {
            integer(value, value)
        }
    }

    // Float.toString has at most 9 significant digits, so its double has the same shortest text.
    override fun encodeFloat(value: Float) = decimal(value.toString().toDouble(), value)

    override fun encodeDouble(value: Double) = decimal(value, value)

    override fun encodeChar(value: Char) = add(Shape.scalar(JsonKind.STRING, value.toString()), value)

    override fun encodeString(value: String) = add(Shape.scalar(JsonKind.STRING, value), value)

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        val name = enumDescriptor.getElementName(index)
        add(Shape.scalar(JsonKind.STRING, name), name)
    }

    override fun encodeJsonElement(element: JsonElement) = add(model.elementShape(element), element)

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        if (!structured) {
            // The value of this encoder: its serializer writes it here.
            serializer.serialize(this, value)
            return
        }
        @Suppress("UNCHECKED_CAST")
        val strategy = serializer as SerializationStrategy<Any?>
        val kind = strategy.descriptor.kind
        if (value is JsonElement) {
            addNode(KotlinxNode(value, ElementSource(model, value)), "")
        } else if (kind is PrimitiveKind || kind == SerialKind.ENUM || strategy.descriptor.isInline) {
            // A scalar, an enum, or a value class: capture it now, also for a map key.
            val child = LevelEncoder(model)
            strategy.serialize(child, value)
            val shape = child.shape()
            if (shape == null) {
                tooComplex = true
            } else {
                add(shape, value)
            }
        } else if (structure.kind == StructureKind.MAP && expectingKey) {
            // A structured map key: only Json can write it (allowStructuredMapKeys).
            tooComplex = true
        } else {
            addNode(KotlinxNode(value, Capture(model, strategy, value)), "")
        }
    }

    /** An integer. The original of an unsigned value is signed, so its text is made now. */
    private fun integer(value: Long, original: Any) =
        add(if (unsigned) Shape.number(JsonNumber.of(value), value.toString()) else Shape.numberOf(JsonNumber.of(value), original), original)

    /**
     * A floating-point value. Json writes `Double.toString`, and the exact value of a
     * `JsonNumber.of(double)` is that same decimal, so the number compares without a conversion.
     * NaN and the infinities are not JSON numbers: Json writes them as these words.
     */
    private fun decimal(value: Double, original: Any) {
        when (val number = JsonNumber.of(value)) {
            is Maybe.Some -> add(Shape.numberOf(number.value(), original), original)
            is Maybe.None -> add(Shape.scalar(JsonKind.STRING, original.toString()), original)
        }
    }

    /** Adds a captured shape: the value of this encoder, or a child. */
    private fun add(shape: Shape, original: Any?) {
        unsigned = false
        if (structured) {
            addNode(KotlinxNode(original, shape), if (structure.kind == StructureKind.MAP && expectingKey) shape.text else "")
        } else {
            scalar = shape
        }
    }

    /** Adds a child to the structure. A map key is the JSON text of a scalar. */
    private fun addNode(node: KotlinxNode, text: String) {
        if (tooComplex) {
            // The model reads this value through Json: the children of this level are not used.
            return
        }
        when (structure.kind) {
            StructureKind.LIST -> elements.add(node)
            StructureKind.MAP -> {
                if (expectingKey) {
                    key = text
                } else {
                    members[key] = node
                }
                expectingKey = !expectingKey
            }
            else -> members[model.jsonName(structure, index)] = node
        }
    }

}

/** The descriptor of a [LevelEncoder] before its value starts a structure. */
private val NO_STRUCTURE: SerialDescriptor = PrimitiveSerialDescriptor("ca.marcusdunn.jsonlens.kotlinx.None", PrimitiveKind.STRING)

/** The value classes of the unsigned numbers: Json writes their values without a sign. */
private val UNSIGNED: Set<String> = setOf("kotlin.UByte", "kotlin.UShort", "kotlin.UInt", "kotlin.ULong")
