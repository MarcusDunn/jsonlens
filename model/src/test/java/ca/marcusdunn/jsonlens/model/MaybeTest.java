package ca.marcusdunn.jsonlens.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.testsupport.Requirement;
import org.junit.jupiter.api.Test;

@Requirement("lib/absence-as-values")
class MaybeTest {

    private static final Maybe<Integer> SOME = Maybe.some(2);
    private static final Maybe<Integer> NONE = Maybe.none();

    @Test
    void patternMatchIsExhaustive() {
        String text = switch (NONE) {
            case Maybe.Some(Integer value) -> "some " + value;
            case Maybe.None() -> "none";
        };
        assertEquals("none", text);
    }

    @Test
    void isSomeAndIsNone() {
        assertTrue(SOME.isSome());
        assertFalse(SOME.isNone());
        assertTrue(NONE.isNone());
        assertFalse(NONE.isSome());
        assertEquals(Maybe.none(), NONE);
    }

    @Test
    void mapAndFlatMap() {
        assertEquals(Maybe.some(3), SOME.map(v -> v + 1));
        assertEquals(NONE, NONE.map(v -> v + 1));
        assertEquals(Maybe.some(4), SOME.flatMap(v -> Maybe.some(v * 2)));
        assertEquals(NONE, SOME.flatMap(v -> Maybe.none()));
        assertEquals(NONE, NONE.flatMap(v -> Maybe.some(v * 2)));
    }

    @Test
    void fallbacks() {
        assertEquals(2, SOME.orElse(0));
        assertEquals(0, NONE.orElse(0));
        assertEquals(2, SOME.orElseGet(() -> 0));
        assertEquals(0, NONE.orElseGet(() -> 0));
    }

    @Test
    void toResult() {
        assertEquals(Result.ok(2), SOME.toResult(() -> "missing"));
        assertEquals(Result.err("missing"), NONE.toResult(() -> "missing"));
    }
}
