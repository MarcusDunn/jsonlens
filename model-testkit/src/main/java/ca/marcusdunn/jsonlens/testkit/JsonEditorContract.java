package ca.marcusdunn.jsonlens.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.model.JsonEditor;
import ca.marcusdunn.jsonlens.model.JsonFactory;
import ca.marcusdunn.jsonlens.model.JsonModel;
import ca.marcusdunn.jsonlens.model.JsonString;
import ca.marcusdunn.jsonlens.model.Maybe;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests that a model that also implements {@link JsonEditor} is well-formed: each change gives the
 * expected value, each method returns the value that it replaced or removed, and the reverse
 * changes give the original value again.
 *
 * @param <N> the node type of the model
 * @param <M> the type of the model
 */
public abstract class JsonEditorContract<N, M extends JsonModel<N> & JsonFactory<N> & JsonEditor<N>>
        extends JsonFactoryContract<N, M> {

    /// Makes the contract test. A subclass gives the model and a parser.
    protected JsonEditorContract() {}

    private static JsonString name(String value) {
        return JsonString.of(value);
    }

    private N value(String json) {
        return parse(json);
    }

    private void assertJson(String expected, N actual) {
        assertTrue(factory().equal(parse(expected), actual), () -> "expected " + expected);
    }

    private static <T> T present(Maybe<T> maybe) {
        return maybe.orElseGet(() -> org.junit.jupiter.api.Assertions.fail("expected a present value"));
    }

    @Test
    void putMemberAddsAMember() {
        N object = parse("{\"a\": 1}");
        assertEquals(Maybe.none(), factory().putMember(object, name("b"), value("[2]")));
        assertJson("{\"a\": 1, \"b\": [2]}", object);
        assertEquals(2, factory().memberCount(object));
    }

    @Test
    void putMemberReplacesAValue() {
        N object = parse("{\"a\": 1, \"b\": 2}");
        N previous = present(factory().putMember(object, name("a"), value("\"x\"")));
        assertJson("1", previous);
        assertJson("{\"a\": \"x\", \"b\": 2}", object);
        assertEquals(2, factory().memberCount(object));
    }

    @Test
    void removeMemberRemovesAMember() {
        N object = parse("{\"a\": {\"c\": 3}, \"b\": 2}");
        assertJson("{\"c\": 3}", present(factory().removeMember(object, name("a"))));
        assertJson("{\"b\": 2}", object);
        assertEquals(Maybe.none(), factory().removeMember(object, name("a")));
        assertEquals(Maybe.none(), factory().removeMember(object, name("")));
        assertJson("{\"b\": 2}", object);
    }

    @Test
    void insertElementShiftsTheElementsToTheRight() {
        N array = parse("[1, 2]");
        factory().insertElement(array, 0, value("0"));
        factory().insertElement(array, 2, value("1.5"));
        factory().insertElement(array, 4, value("3"));
        assertJson("[0, 1, 1.5, 2, 3]", array);
        N empty = parse("[]");
        factory().insertElement(empty, 0, value("{}"));
        assertJson("[{}]", empty);
    }

    @Test
    void setElementReplacesAnElement() {
        N array = parse("[1, [2], 3]");
        assertJson("[2]", factory().setElement(array, 1, value("null")));
        assertJson("[1, null, 3]", array);
    }

    @Test
    void removeElementShiftsTheElementsToTheLeft() {
        N array = parse("[1, 2, 3]");
        assertJson("1", factory().removeElement(array, 0));
        assertJson("3", factory().removeElement(array, 1));
        assertJson("[2]", array);
        assertEquals(1, factory().arrayLength(array));
    }

    @Test
    void reverseChangesRestoreTheValue() {
        String json = "{\"a\": [1, 2], \"b\": {\"c\": true}}";
        N document = parse(json);
        N array = present(factory().member(document, name("a")));
        N inner = present(factory().member(document, name("b")));
        // Change, and reverse each change with the returned value.
        Maybe<N> replaced = factory().putMember(inner, name("c"), value("false"));
        Maybe<N> added = factory().putMember(inner, name("d"), value("1"));
        N removed = factory().removeElement(array, 0);
        factory().insertElement(array, 1, value("9"));
        N set = factory().setElement(array, 0, value("7"));
        factory().setElement(array, 0, set);
        factory().removeElement(array, 1);
        factory().insertElement(array, 0, removed);
        assertEquals(Maybe.none(), added);
        factory().removeMember(inner, name("d"));
        factory().putMember(inner, name("c"), present(replaced));
        assertJson(json, document);
    }

    @Test
    void copiesCanBeInserted() {
        N source = parse("{\"x\": [1, {\"y\": 2}]}");
        N target = parse("[]");
        N copy = present(factory().copyOf(factory(), present(factory().member(source, name("x")))));
        factory().insertElement(target, 0, copy);
        // A change of the copy does not change the source.
        factory().putMember(present(factory().element(copy, 1)), name("y"), value("3"));
        assertJson("[[1, {\"y\": 3}]]", target);
        assertJson("{\"x\": [1, {\"y\": 2}]}", source);
    }

    /// Runs the JSON Patch test suite on the model, in place.
    ///
    /// @return a test for each test of the suite
    @TestFactory
    Stream<DynamicTest> jsonPatchSuiteInPlace() {
        return ComplianceKit.jsonPatchInPlace(factory(), this::parse);
    }
}
