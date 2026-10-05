package ca.marcusdunn.jsonlens.testkit;

import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testsupport.Requirement;

@Requirement("lib/testkit-contracts")
@Requirement("lib/testkit-suites")
class JavaCollectionsContractTest extends JsonEditorContract<Object, JavaCollectionsModel> {

    @Override
    protected JavaCollectionsModel factory() {
        return JavaCollectionsModel.INSTANCE;
    }

    @Override
    protected Object parse(String json) {
        return Documents.collections(json);
    }

    @Override
    protected boolean readsAnyExponent() {
        return true;
    }
}
