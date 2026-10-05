package ca.marcusdunn.jsonlens.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.mapped.MappedJson;
import ca.marcusdunn.jsonlens.model.JsonKind;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import org.junit.jupiter.api.Test;

@Requirement("lib/testkit-random-json")
class RandomJsonTest {

    @Test
    void theSameSeedGivesTheSameTexts() {
        RandomJson a = RandomJson.withSeed(42);
        RandomJson b = RandomJson.withSeed(42);
        for (int i = 0; i < 20; i++) {
            assertEquals(a.next(), b.next());
        }
        assertNotEquals(RandomJson.withSeed(1).next(), RandomJson.withSeed(2).next());
    }

    @Test
    void givesTheSameTextsInEachVersion() {
        // A change of these texts changes the documents of each contract test.
        RandomJson random = RandomJson.withSeed(0);
        assertEquals("{\"😀\":false,\"b\":[],\"\":9007199254740992,\"k2\":\"\"}", random.next());
        assertEquals("[3775962213208117092,[1,\"a\\t\"],{\"\\u00e9x\":false},7593930394342328515,\"\\\\\\n\\\\\"]", random.next());
    }

    @Test
    void eachTextIsAnObjectOrAnArrayWithTheDifficultCases() {
        RandomJson random = RandomJson.withSeed(7);
        StringBuilder all = new StringBuilder();
        int deepest = 0;
        for (int i = 0; i < 2000; i++) {
            String text = random.next();
            MappedJson json = Documents.mapped(text);
            JsonKind kind = json.model().kind(json.root());
            assertTrue(kind == JsonKind.OBJECT || kind == JsonKind.ARRAY, text);
            deepest = Math.max(deepest, depth(text));
            all.append(text).append('\n');
        }
        assertEquals(5, deepest);
        for (String sample : new String[] {"\\ud83d\\ude00", "1e400", "1E+2", "123456789012345678901234567890", "\"\":", "{}", "[]",
                "true", "false", "null", "\"\\u00e9x\":"}) {
            assertTrue(all.indexOf(sample) >= 0, sample);
        }
    }

    /// The deepest nesting of brackets outside strings.
    private static int depth(String text) {
        int depth = 0;
        int deepest = 0;
        boolean string = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (string) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    string = false;
                }
            } else if (c == '"') {
                string = true;
            } else if (c == '[' || c == '{') {
                deepest = Math.max(deepest, ++depth);
            } else if (c == ']' || c == '}') {
                depth--;
            }
        }
        return deepest;
    }
}
