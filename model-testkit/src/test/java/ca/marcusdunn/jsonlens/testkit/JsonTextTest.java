package ca.marcusdunn.jsonlens.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ca.marcusdunn.jsonlens.testing.JavaCollectionsModel;
import ca.marcusdunn.jsonlens.testkit.FaultyModel.Fault;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.List;
import org.junit.jupiter.api.Test;

@Requirement("lib/testkit-suites")
class JsonTextTest {

    private static final JavaCollectionsModel MODEL = JavaCollectionsModel.INSTANCE;

    @Test
    void writesEachKind() {
        String json = "{\"a\":[1,25E-1,true,false,null],\"b\":{},\"c\":[],\"\":\"x\"}";
        assertEquals(json, JsonText.write(MODEL, Documents.collections(json)));
    }

    @Test
    void escapesQuotesBackslashesControlCharactersAndUnpairedSurrogates() {
        String value = "\"\\\u001f \ud7ff\udfff\ud800\ue000😀";
        assertEquals("\"\\\"\\\\\\u001f \ud7ff\\udfff\\ud800\ue000😀\"", JsonText.write(MODEL, value));
        // The text reads back as the same value.
        assertEquals(List.of(value), Documents.collections("[" + JsonText.write(MODEL, value) + "]"));
    }

    @Test
    void aMissingElementIsAnError() {
        Object array = Documents.collections("[1, 2]");
        assertThrows(IllegalArgumentException.class, () -> JsonText.write(new FaultyModel(Fault.LAST_ELEMENT_IS_MISSING), array));
    }
}
