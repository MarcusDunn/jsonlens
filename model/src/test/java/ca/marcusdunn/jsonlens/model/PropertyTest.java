package ca.marcusdunn.jsonlens.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import ca.marcusdunn.jsonlens.testsupport.Requirement;
import org.junit.jupiter.api.Test;

@Requirement("lib/model-member-names")
class PropertyTest {

    /** A name that is not a TextString: it gives the scalar values of a Java string. */
    private static JsonString custom(String value) {
        return () -> value.codePoints().iterator();
    }

    @Test
    void namesInDifferentRepresentationsAreEqual() {
        Property<Integer> text = new Property<>(JsonString.of("né"), 1);
        Property<Integer> other = new Property<>(custom("né"), 1);
        assertEquals(text, other);
        assertEquals(other, text);
        assertEquals(text.hashCode(), other.hashCode());
        assertEquals(31 * "né".hashCode() + 1, text.hashCode());
    }

    @Test
    void differentNamesOrValuesAreNotEqual() {
        Property<Integer> property = new Property<>(JsonString.of("a"), 1);
        assertNotEquals(property, new Property<>(custom("b"), 1));
        assertNotEquals(property, new Property<>(custom("a"), 2));
        assertNotEquals(property, (Object) "a");
    }

    @Test
    void textShowsTheDecodedName() {
        assertEquals("Property[name=né, value=1]", new Property<>(custom("né"), 1).toString());
    }
}
