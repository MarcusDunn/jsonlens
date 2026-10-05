package ca.marcusdunn.jsonlens.docs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.model.Maybe;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.path.core.function.FunctionSignature;
import ca.marcusdunn.jsonlens.path.core.function.FunctionType;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery;
import ca.marcusdunn.jsonlens.path.evaluator.BuildingEvaluator;
import ca.marcusdunn.jsonlens.path.evaluator.BuildingFunctionExtension;
import ca.marcusdunn.jsonlens.path.evaluator.ExtensionError;
import ca.marcusdunn.jsonlens.path.evaluator.FunctionExtension;
import ca.marcusdunn.jsonlens.path.evaluator.Instance;
import ca.marcusdunn.jsonlens.path.evaluator.JsonPathEvaluator;
import ca.marcusdunn.jsonlens.path.evaluator.Node;
import ca.marcusdunn.jsonlens.jackson.JacksonJsonModel;
import ca.marcusdunn.jsonlens.path.parser.FunctionRegistrationError;
import ca.marcusdunn.jsonlens.path.parser.JsonPathParser;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Function extensions, from the signature to the evaluation. */
class ExtensionSnippets {

    // @start region="extension"
    // first(NodesType): ValueType. The first node of a nodelist, or Nothing.
    // A read-only extension: it runs with each JsonModel, also with a read-only model.
    static final class First implements FunctionExtension {

        static final FunctionSignature SIGNATURE =
                new FunctionSignature("first", FunctionType.VALUE, List.of(FunctionType.NODES));

        @Override
        public FunctionSignature signature() {
            return SIGNATURE;
        }

        @Override
        public <N> Instance<N> apply(List<Instance<N>> arguments, JsonModel<N> model) {
            // The evaluator gives one instance for each parameter, of the declared type.
            List<Node<N>> nodes = ((Instance.NodesInstance<N>) arguments.getFirst()).nodes();
            // A ValueType result is an existing node, or Nothing.
            return new Instance.ValueInstance<>(nodes.isEmpty() ? Maybe.none() : Maybe.some(nodes.getFirst().value()));
        }
    }
    // @end region="extension"

    @Test
    void register() {
        JsonNode root = JsonMapper.builder().build().readTree("[[3, 4], [5], []]");
        // @start region="register"
        // The parser needs the signature, to check that queries are well-typed.
        Result<JsonPathParser, FunctionRegistrationError> parser =
                JsonPathParser.withFunctions(List.of(First.SIGNATURE));
        // The evaluator needs the implementation.
        Result<JsonPathEvaluator, ExtensionError> evaluator =
                JsonPathEvaluator.withFunctions(List.of(new First()));

        JsonPathQuery query = parser.orElse(JsonPathParser.standard())
                .parse("$[?first(@.*) > 4]")
                .orElse(null);
        List<Node<JsonNode>> nodes = evaluator.orElse(JsonPathEvaluator.standard())
                .evaluate(query, root, JacksonJsonModel.INSTANCE)
                .orElse(List.of());
        // nodes: the path $[1]
        // @end region="register"
        assertEquals(List.of("$[1]"), nodes.stream().map(node -> node.path().toString()).toList());
    }

    // @start region="building"
    // upper(ValueType): ValueType. The upper case of a string, or Nothing for other values.
    // It makes a new string, so it needs a model that can build nodes.
    static final class Upper implements BuildingFunctionExtension {

        static final FunctionSignature SIGNATURE =
                new FunctionSignature("upper", FunctionType.VALUE, List.of(FunctionType.VALUE));

        @Override
        public FunctionSignature signature() {
            return SIGNATURE;
        }

        @Override
        public <N, M extends JsonModel<N> & JsonFactory<N>> Instance<N> apply(List<Instance<N>> arguments, M model) {
            // A ValueType argument is a node. A literal such as 'abc' arrives as a built node.
            if (arguments.getFirst() instanceof Instance.ValueInstance<N>(Maybe.Some<N>(N node))
                    && model.kind(node) == JsonKind.STRING) {
                String upper = JsonString.copyOf(model.stringValue(node)).toUpperCase(Locale.ROOT);
                return new Instance.ValueInstance<>(Maybe.some(model.string(JsonString.of(upper))));
            }
            return new Instance.ValueInstance<>(Maybe.none());
        }
    }
    // @end region="building"

    @Test
    void buildingEvaluate() {
        JsonNode root = JsonMapper.builder().build().readTree("[\"abc\", \"ABC\", \"xyz\", 1]");
        // @start region="building-evaluate"
        JsonPathParser parser = JsonPathParser.withFunctions(List.of(Upper.SIGNATURE)).orElse(null);
        BuildingEvaluator evaluator =
                JsonPathEvaluator.withFunctions(List.of(), List.of(new Upper())).orElse(null);

        JsonPathQuery query = parser.parse("$[?upper(@) == upper('abc')]").orElse(null);
        // JacksonJsonModel implements JsonModel and JsonFactory. A read-only model does not compile here.
        List<Node<JsonNode>> nodes = evaluator.evaluate(query, root, JacksonJsonModel.INSTANCE).orElse(List.of());
        // nodes: the paths $[0] and $[1]
        // @end region="building-evaluate"
        assertEquals(List.of("$[0]", "$[1]"), nodes.stream().map(node -> node.path().toString()).toList());
    }

    @Test
    void duplicates() {
        // @start region="duplicate"
        Result<JsonPathParser, FunctionRegistrationError> clash = JsonPathParser.withFunctions(List.of(
                new FunctionSignature("length", FunctionType.VALUE, List.of(FunctionType.VALUE))));
        // clash: Err(DuplicateName[name=length]). A standard function cannot be replaced.
        // @end region="duplicate"
        assertEquals(Result.err(new FunctionRegistrationError.DuplicateName("length")), clash);
    }
}
