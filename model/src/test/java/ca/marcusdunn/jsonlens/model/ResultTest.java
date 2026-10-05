package ca.marcusdunn.jsonlens.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.testsupport.Requirement;
import org.junit.jupiter.api.Test;

@Requirement("lib/errors-as-values")
class ResultTest {

    private record Problem(String message) {}

    private static final Result<Integer, Problem> OK = Result.ok(2);
    private static final Result<Integer, Problem> ERR = Result.err(new Problem("bad"));

    @Test
    void patternMatchIsExhaustive() {
        String text = switch (ERR) {
            case Result.Ok(Integer value) -> "ok " + value;
            case Result.Err(Problem problem) -> "err " + problem.message();
        };
        assertEquals("err bad", text);
    }

    @Test
    void isOkAndIsErr() {
        assertTrue(OK.isOk());
        assertFalse(OK.isErr());
        assertTrue(ERR.isErr());
        assertFalse(ERR.isOk());
    }

    @Test
    void mapChangesOnlySuccess() {
        assertEquals(Result.ok(3), OK.map(v -> v + 1));
        assertEquals(ERR, ERR.map(v -> v + 1));
    }

    @Test
    void mapErrChangesOnlyError() {
        assertEquals(OK, OK.mapErr(Problem::message));
        assertEquals(Result.err("bad"), ERR.mapErr(Problem::message));
    }

    @Test
    void flatMapChainsOperations() {
        assertEquals(Result.ok(4), OK.flatMap(v -> Result.ok(v * 2)));
        assertEquals(Result.err(new Problem("odd")), OK.flatMap(v -> Result.err(new Problem("odd"))));
        assertEquals(ERR, ERR.flatMap(v -> Result.ok(v * 2)));
    }

    @Test
    void foldAndOrElse() {
        assertEquals("2", OK.fold(String::valueOf, Problem::message));
        assertEquals("bad", ERR.fold(String::valueOf, Problem::message));
        assertEquals(2, OK.orElse(0));
        assertEquals(0, ERR.orElse(0));
    }
}
