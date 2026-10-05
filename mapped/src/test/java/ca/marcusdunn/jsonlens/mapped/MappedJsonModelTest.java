package ca.marcusdunn.jsonlens.mapped;

import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.testkit.JsonModelContract;
import ca.marcusdunn.jsonlens.testsupport.Requirement;

@Requirement("lib/mapped-contract")
class MappedJsonModelTest extends JsonModelContract<MappedNode> {

    @Override
    protected JsonModel<MappedNode> model() {
        return Json.of("0").model();
    }

    @Override
    protected MappedNode parse(String json) {
        return Json.of(json).root();
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
