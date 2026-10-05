package ca.marcusdunn.jsonlens.jackson;

import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.Result;
import ca.marcusdunn.jsonlens.testkit.JsonModelContract;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import tools.jackson.databind.json.JsonMapper;

@Requirement("lib/jackson-stream-contract")
class JacksonStreamContractTest extends JsonModelContract<JacksonStreamNode> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Override
    protected JsonModel<JacksonStreamNode> model() {
        return parse("null").stream.model();
    }

    @Override
    protected JacksonStreamNode parse(String json) {
        return switch (JacksonStream.open(MAPPER.createParser(json))) {
            case Result.Ok<JacksonStream, StreamError>(JacksonStream stream) -> stream.root();
            case Result.Err<JacksonStream, StreamError>(StreamError error) -> throw new AssertionError(error.message());
        };
    }

    @Override
    protected boolean readsAnyExponent() {
        return true;
    }

    @Override
    protected boolean keepsDuplicateNames() {
        return true;
    }
}
