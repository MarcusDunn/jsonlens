package ca.marcusdunn.jsonlens.kotlinx

import ca.marcusdunn.jsonlens.model.JsonKind
import ca.marcusdunn.jsonlens.model.JsonModel
import ca.marcusdunn.jsonlens.model.JsonString
import ca.marcusdunn.jsonlens.model.Maybe
import ca.marcusdunn.jsonlens.testsupport.Requirement
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@Serializable
enum class Color { RED, @SerialName("green") GREEN }

@Serializable
@JvmInline
value class Sku(val code: String)

@Serializable
data class Line(val sku: Sku, val qty: Int, val unitPrice: Double = 1.5, val note: String? = null)

@Serializable
sealed interface Figure {
    @Serializable
    @SerialName("circle")
    data class Circle(val radius: Double) : Figure

    @Serializable
    data class Square(val side: Long) : Figure
}

@Serializable
object Marker

/** A number with all its digits: Json writes it as an unquoted literal. */
object ExactSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = serializer<String>().descriptor

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: BigDecimal) {
        (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
    }

    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
}

/** A serializer that writes two levels in one call. */
object TwoLevelsSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("TwoLevels") {
        element<String>("a")
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, InnerSerializer, value)
        }
    }

    override fun deserialize(decoder: Decoder): String = ""

    object InnerSerializer : KSerializer<String> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Inner") { element<String>("b") }
        override fun serialize(encoder: Encoder, value: String) {
            encoder.encodeStructure(descriptor) { encodeStringElement(descriptor, 0, value) }
        }

        override fun deserialize(decoder: Decoder): String = ""
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Everything(
    val byte: Byte = -1,
    val short: Short = 2,
    val int: Int = 3,
    val long: Long = Long.MAX_VALUE,
    val float: Float = 1.1f,
    val double: Double = 2.5e-7,
    val char: Char = 'x',
    val bool: Boolean = true,
    val text: String = "é中\u0000",
    val nullable: String? = null,
    val color: Color = Color.GREEN,
    val sku: Sku = Sku("A1"),
    val uint: UInt = UInt.MAX_VALUE,
    val ulong: ULong = ULong.MAX_VALUE,
    val ubyte: UByte = 255u,
    val ushort: UShort = 65535u,
    val lines: List<Line> = listOf(Line(Sku("x"), 2), Line(Sku("y"), 0, note = "n")),
    val tags: Set<String> = setOf("a", "b"),
    val byName: Map<String, Line> = mapOf("k" to Line(Sku("z"), 1)),
    val byInt: Map<Int, String> = mapOf(1 to "one", -2 to "minus two"),
    val byDouble: Map<Double, Boolean> = mapOf(1.5 to true),
    val byEnum: Map<Color, List<Int>> = mapOf(Color.RED to listOf(1)),
    val byUnsigned: Map<UInt, Char> = mapOf(UInt.MAX_VALUE to 'u'),
    val figure: Figure = Figure.Circle(1.0),
    val figures: List<Figure> = listOf(Figure.Square(2)),
    val marker: Marker = Marker,
    val element: JsonElement = buildJsonObject { put("x", 1) },
    val primitive: JsonPrimitive = JsonPrimitive(5),
    val ints: List<Int>? = listOf(1, 2),
    val nested: List<List<Int?>> = listOf(listOf(1, null), listOf()),
    @Serializable(with = ExactSerializer::class) val exact: BigDecimal = BigDecimal("1e400"),
    val empty: Map<String, Int> = mapOf(),
    @EncodeDefault val always: String = "always",
)

@Serializable
data class Special(val nan: Double = Double.NaN, val infinity: Float = Float.POSITIVE_INFINITY)

@Serializable
data class StructuredKeys(val byLine: Map<Line, Int> = mapOf(Line(Sku("k"), 1) to 1))

@Serializable
data class TwoLevels(@Serializable(with = TwoLevelsSerializer::class) val value: String = "v", val after: Int = 1)

class KotlinxObjectModelTest {

    /** The cases that Json cannot encode: there is no view to compare. */
    private val skipped = mutableListOf<String>()

    /** Checks that the view of the model is the JSON of Json: the same values, in the same member order. */
    private fun <T> assertSameView(json: Json, value: T, serializer: kotlinx.serialization.KSerializer<T>) {
        val model = KotlinxObjectModel(json)
        val node = model.node(value, serializer)
        // A value that Json cannot encode has no view to compare (the model is not well-formed for it).
        val expected = runCatching { json.encodeToJsonElement(serializer, value) }.getOrNull()
        if (expected == null) {
            skipped += "${configurations.indexOf(json)}:${serializer.descriptor.serialName}"
            return
        }
        assertTrue(JsonModel.equal(model, node, KotlinxJsonModel, expected), "the view differs from $expected")
        assertSameOrder(model, node, expected)
    }

    private fun assertSameOrder(model: KotlinxObjectModel, node: KotlinxNode, expected: JsonElement) {
        when (model.kind(node)) {
            JsonKind.OBJECT -> {
                val names = model.members(node).map { JsonString.copyOf(it.name()) }.toList()
                assertEquals(KotlinxJsonModel.members(expected).map { JsonString.copyOf(it.name()) }.toList(), names)
                for (name in names) {
                    assertSameOrder(model, (model.member(node, JsonString.of(name)) as Maybe.Some).value(),
                        (KotlinxJsonModel.member(expected, JsonString.of(name)) as Maybe.Some).value())
                }
            }
            JsonKind.ARRAY -> for (i in 0 until model.arrayLength(node)) {
                assertSameOrder(model, (model.element(node, i) as Maybe.Some).value(),
                    (KotlinxJsonModel.element(expected, i) as Maybe.Some).value())
            }
            else -> {}
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val configurations: List<Json> = listOf(
        Json,
        Json { encodeDefaults = true },
        Json { encodeDefaults = true; explicitNulls = false },
        Json { encodeDefaults = true; namingStrategy = JsonNamingStrategy.SnakeCase },
        Json { encodeDefaults = true; classDiscriminator = "kind" },
        Json { encodeDefaults = true; classDiscriminatorMode = ClassDiscriminatorMode.ALL_JSON_OBJECTS },
        Json { encodeDefaults = true; classDiscriminatorMode = ClassDiscriminatorMode.NONE },
        Json { encodeDefaults = true; useArrayPolymorphism = true },
    )

    @Test
    @Requirement("lib/kotlinx-object-model")
    fun theViewIsTheJsonOfEachConfiguration() {
        val values = listOf(
            Everything(),
            Everything(nullable = "set", ints = null, figure = Figure.Square(-3), byte = Byte.MIN_VALUE),
        )
        for (json in configurations) {
            for (value in values) {
                assertSameView(json, value, Everything.serializer())
            }
            assertSameView(json, Line(Sku("q"), 7), Line.serializer())
            assertSameView(json, listOf(Color.RED, Color.GREEN), serializer<List<Color>>())
            assertSameView(json, Figure.Circle(2.0), Figure.serializer())
            assertSameView(json, Marker, Marker.serializer())
            assertSameView(json, "text", serializer<String>())
            assertSameView(json, 42u, serializer<UInt>())
            assertSameView(json, Sku("v"), Sku.serializer())
            assertSameView(json, null, serializer<Line?>())
            assertSameView(json, TwoLevels(), TwoLevels.serializer())
            assertSameView(json, mapOf<String, Int?>("a" to null), serializer<Map<String, Int?>>())
        }
        // kotlinx.serialization 1.11 cannot encode Everything with ClassDiscriminatorMode.ALL_JSON_OBJECTS
        // (a NumberFormatException in its JsonTreeListEncoder). All other cases have a view to compare.
        val everything = "5:" + Everything.serializer().descriptor.serialName
        assertEquals(listOf(everything, everything), skipped)
    }

    @Test
    @Requirement("lib/kotlinx-object-model")
    fun specialValuesFollowTheConfiguration() {
        assertSameView(Json { allowSpecialFloatingPointValues = true; encodeDefaults = true }, Special(), Special.serializer())
        assertSameView(Json { allowStructuredMapKeys = true; encodeDefaults = true }, StructuredKeys(), StructuredKeys.serializer())
    }

    @Test
    @Requirement("lib/kotlinx-object-model")
    fun nodesKeepYourValues() {
        val model = KotlinxObjectModel()
        val line = Line(Sku("x"), 2)
        val order = Everything(lines = listOf(line))
        val root = model.node(order, Everything.serializer())
        assertSame(order, root.value)
        val lines = (model.member(root, JsonString.of("lines")) as Maybe.Some).value()
        val first = (model.element(lines, 0) as Maybe.Some).value()
        assertSame(line, first.value)
        // The same node each time.
        assertSame(first, (model.element(lines, 0) as Maybe.Some).value())
        assertEquals("KotlinxNode[$line]", first.toString())
        assertEquals(Maybe.none<KotlinxNode>(), model.member(root, JsonString.of("missing")))
        assertEquals(Maybe.none<KotlinxNode>(), model.element(lines, 1))
    }

    @Test
    @Requirement("lib/kotlinx-object-model")
    fun eachKindOfChildIsCapturedInOneLevel() {
        val json = Json { encodeDefaults = true; allowSpecialFloatingPointValues = true }
        val model = KotlinxObjectModel(json)
        val root = model.node(Probe(), Probe.serializer())
        Counted.Serializer.calls = 0
        // The capture of the root and of the lists and maps does not read the counted children.
        for (name in listOf("list", "byName", "byUnsigned", "byColor")) {
            val child = (model.member(root, JsonString.of(name)) as Maybe.Some).value()
            model.members(child).count()
            if (model.kind(child) == JsonKind.ARRAY) {
                model.element(child, 0)
            }
        }
        assertEquals(0, Counted.Serializer.calls)
        assertSameView(json, Probe(), Probe.serializer())
    }

    @Test
    @Requirement("lib/kotlinx-object-model")
    fun contextualAndNestedValuesUseJson() {
        val contextual = Json {
            encodeDefaults = true
            serializersModule = kotlinx.serialization.modules.SerializersModule { contextual(java.time.LocalDate::class, DateSerializer) }
        }
        assertSameView(contextual, Dated(), Dated.serializer())
        val json = Json { encodeDefaults = true }
        assertSameView(json, HasNested(), HasNested.serializer())
        val model = KotlinxObjectModel(json)
        val nested = (model.member(model.node(HasNested(), HasNested.serializer()), JsonString.of("nested")) as Maybe.Some).value()
        // The node keeps your value. The nodes inside the part that Json writes have JsonElement values.
        assertEquals("x", nested.value)
        assertTrue((model.member(nested, JsonString.of("a")) as Maybe.Some).value().value is JsonElement)
    }

    @Test
    @Requirement("lib/kotlinx-object-model")
    fun serializersCanWriteOtherSerializersOfTheModule() {
        val model = KotlinxObjectModel()
        val root = model.node(Wrapped(Holder("h", Counted("c"))), Wrapped.serializer())
        Counted.Serializer.calls = 0
        assertEquals(listOf("name", "counted"), model.members(root).map { JsonString.copyOf(it.name()) }.toList())
        assertEquals(0, Counted.Serializer.calls)
        assertSameView(Json, Wrapped(Holder("h", Counted("c"))), Wrapped.serializer())
        // A value class whose content one level cannot show: Json writes its parent.
        assertSameView(Json { encodeDefaults = true }, HasWrapNested(), HasWrapNested.serializer())
    }

    /** A value whose serializer counts its calls. */
    @Serializable(with = Counted.Serializer::class)
    class Counted(val text: String) {
        object Serializer : KSerializer<Counted> {
            var calls = 0
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Counted") { element<String>("text") }
            override fun serialize(encoder: Encoder, value: Counted) {
                calls++
                encoder.encodeStructure(descriptor) { encodeStringElement(descriptor, 0, value.text) }
            }

            override fun deserialize(decoder: Decoder): Counted = Counted("")
        }
    }

    @Serializable
    data class Holder(val name: String, val counted: Counted)

    @Test
    @Requirement("lib/kotlinx-object-model")
    fun childrenAreReadOnlyWhenAQueryVisitsThem() {
        val model = KotlinxObjectModel()
        val root = model.node(Holder("h", Counted("c")), Holder.serializer())
        Counted.Serializer.calls = 0
        assertEquals(JsonKind.OBJECT, model.kind(root))
        val counted = (model.member(root, JsonString.of("counted")) as Maybe.Some).value()
        assertEquals(0, Counted.Serializer.calls)
        assertEquals(JsonKind.OBJECT, model.kind(counted))
        assertEquals(1, Counted.Serializer.calls)
        model.members(counted).count()
        assertEquals(1, Counted.Serializer.calls)
    }
}

/** The model passes the contract tests, for JSON element trees (read through Json). */
class KotlinxObjectModelContractTest : ca.marcusdunn.jsonlens.testkit.JsonModelContract<KotlinxNode>() {
    private val model = KotlinxObjectModel()

    override fun model(): JsonModel<KotlinxNode> = model

    override fun parse(json: String): KotlinxNode = model.node(Json.parseToJsonElement(json), JsonElement.serializer())

    override fun readsAnyExponent(): Boolean = true
}

/** Each kind of child, and children that count the calls of their serializer. */
@Serializable
data class Probe(
    val byte: Byte = -1,
    val short: Short = -2,
    val long: Long = -3,
    val float: Float = 0.5f,
    val char: Char = 'c',
    val yes: Boolean = true,
    val no: Boolean = false,
    val nothing: String? = null,
    val color: Color = Color.RED,
    val sku: Sku = Sku("s"),
    val ubyte: UByte = 200u,
    val ushort: UShort = 60000u,
    val uint: UInt = 4000000000u,
    val ulong: ULong = 18000000000000000000u,
    val nan: Double = Double.NaN,
    @Serializable(with = ExactSerializer::class) val exact: BigDecimal = BigDecimal("12345678901234567890.5"),
    val primitive: JsonPrimitive = JsonPrimitive(BigDecimal("12345678901234567890")),
    val figure: Figure = Figure.Square(1),
    val counted: KotlinxObjectModelTest.Counted = KotlinxObjectModelTest.Counted("c"),
    val list: List<KotlinxObjectModelTest.Counted> = listOf(KotlinxObjectModelTest.Counted("l")),
    val byName: Map<String, KotlinxObjectModelTest.Counted> = mapOf("a" to KotlinxObjectModelTest.Counted("a"), "b" to KotlinxObjectModelTest.Counted("b")),
    val byUnsigned: Map<UInt, KotlinxObjectModelTest.Counted> = mapOf(4000000000u to KotlinxObjectModelTest.Counted("u")),
    val byColor: Map<Color, KotlinxObjectModelTest.Counted> = mapOf(Color.GREEN to KotlinxObjectModelTest.Counted("g")),
)

object DateSerializer : KSerializer<java.time.LocalDate> {
    override val descriptor: SerialDescriptor = serializer<String>().descriptor
    override fun serialize(encoder: Encoder, value: java.time.LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): java.time.LocalDate = java.time.LocalDate.parse(decoder.decodeString())
}

@Serializable
data class Dated(@kotlinx.serialization.Contextual val date: java.time.LocalDate = java.time.LocalDate.of(2026, 10, 3), val n: Int = 1)

/** A serializer that writes a structure inside its own structure, through an inline element. */
object NestedSerializer : KSerializer<String> {
    private val inner: SerialDescriptor = buildClassSerialDescriptor("Inner") { element<String>("b") }
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Nested") { element("a", inner) }

    override fun serialize(encoder: Encoder, value: String) {
        val outer = encoder.beginStructure(descriptor)
        val nested = outer.encodeInlineElement(descriptor, 0).beginStructure(inner)
        nested.encodeStringElement(inner, 0, value)
        nested.endStructure(inner)
        outer.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): String = ""
}

@Serializable
data class HasNested(@Serializable(with = NestedSerializer::class) val nested: String = "x", val n: Int = 2)

/** A value whose serializer writes a Holder with the serializer that it finds in the module. */
@Serializable(with = Wrapped.Serializer::class)
class Wrapped(val holder: KotlinxObjectModelTest.Holder) {
    object Serializer : KSerializer<Wrapped> {
        override val descriptor: SerialDescriptor = KotlinxObjectModelTest.Holder.serializer().descriptor
        override fun serialize(encoder: Encoder, value: Wrapped) =
            encoder.encodeSerializableValue(encoder.serializersModule.serializer<KotlinxObjectModelTest.Holder>(), value.holder)

        override fun deserialize(decoder: Decoder): Wrapped = Wrapped(KotlinxObjectModelTest.Holder("", KotlinxObjectModelTest.Counted("")))
    }
}

@Serializable
@JvmInline
value class WrapNested(@Serializable(with = NestedSerializer::class) val text: String)

@Serializable
data class HasWrapNested(val wrapped: WrapNested = WrapNested("w"), val n: Int = 3)
