package ca.marcusdunn.jsonlens.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.model.JsonDecimal;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonNumber;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import java.math.BigDecimal;
import java.math.BigInteger;
import ca.marcusdunn.jsonlens.testkit.JsonEditorContract;
import ca.marcusdunn.jsonlens.testsupport.ModuleDescriptors;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.MissingNode;

@Requirement("lib/jackson-contract")
class JacksonJsonModelTest extends JsonEditorContract<JsonNode, JacksonJsonModel> {

    private static final JsonMapper MAPPER =
            JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    @Override
    protected JacksonJsonModel factory() {
        return JacksonJsonModel.INSTANCE;
    }

    @Override
    protected JsonNode parse(String json) {
        return MAPPER.readTree(json);
    }

    @Test
    @Requirement("lib/jackson-dependencies")
    void requiresOnlyTheModelAndJacksonAtRuntime() {
        assertEquals(
                Set.of("ca.marcusdunn.jsonlens.model", "tools.jackson.databind"),
                ModuleDescriptors.runtimeModules(ModuleDescriptors.named("ca.marcusdunn.jsonlens.jackson")));
    }

    @Test
    @Requirement("lib/jackson-non-json-nodes")
    void classifiesNodesThatAreNotJson() {
        JsonModel<JsonNode> model = model();
        JsonNode nan = NODES.numberNode(Double.NaN);
        JsonNode infinity = NODES.numberNode(Float.NEGATIVE_INFINITY);
        JsonNode binary = NODES.binaryNode(new byte[] {1, 2, 3});
        JsonNode pojo = NODES.pojoNode(java.time.Duration.ofSeconds(1));
        JsonNode nullPojo = NODES.pojoNode(null);

        assertEquals(JsonKind.STRING, model.kind(nan));
        assertEquals("NaN", JsonString.copyOf(model.stringValue(nan)));
        assertEquals(JsonKind.STRING, model.kind(infinity));
        assertEquals("-Infinity", JsonString.copyOf(model.stringValue(infinity)));
        assertEquals(JsonKind.STRING, model.kind(binary));
        assertEquals("AQID", JsonString.copyOf(model.stringValue(binary)));
        assertEquals(JsonKind.STRING, model.kind(pojo));
        assertEquals("PT1S", JsonString.copyOf(model.stringValue(pojo)));
        assertEquals(JsonKind.NULL, model.kind(nullPojo));
        assertEquals(JsonKind.NULL, model.kind(MissingNode.getInstance()));
        assertEquals(JsonKind.NUMBER, model.kind(NODES.numberNode(1.5f)));
        assertEquals(JsonKind.NUMBER, model.kind(NODES.numberNode(2.5d)));
    }

    @Test
    @Requirement("lib/model-number-representation")
    void numbersKeepTheirJacksonType() {
        JsonModel<JsonNode> model = model();
        assertEquals(JsonDecimal.of(7), model.numberValue(NODES.numberNode(7)).exactValue());
        assertEquals(JsonDecimal.of(7), model.numberValue(NODES.numberNode(7L)).exactValue());
        assertEquals(JsonDecimal.of(7), model.numberValue(NODES.numberNode((short) 7)).exactValue());
        assertEquals(JsonDecimal.of(new BigDecimal("1.5")), model.numberValue(NODES.numberNode(1.5)).exactValue());
        assertEquals(JsonDecimal.of(new BigDecimal("1.5")), model.numberValue(NODES.numberNode(1.5f)).exactValue());
        assertEquals(JsonDecimal.of(new BigDecimal("98765432109876543210")), model.numberValue(NODES.numberNode(new BigInteger("98765432109876543210"))).exactValue());
        assertEquals(JsonDecimal.of(new BigDecimal("0.10")), model.numberValue(NODES.numberNode(new BigDecimal("0.10"))).exactValue());
        assertEquals(0, model.compareNumbers(model.numberValue(JacksonJsonModel.INSTANCE.number(JsonNumber.of(0)).orElse(NODES.nullNode())), JsonNumber.of(0)));
    }

    @Test
    @Requirement("lib/model-factory")
    void numbersThatABigDecimalCannotHoldAreNone() {
        JsonNumber huge = JsonNumber.of(JsonDecimal.parse("1e99999999999").orElse(JsonDecimal.ZERO));
        assertEquals(Maybe.none(), JacksonJsonModel.INSTANCE.number(huge));
    }
}
