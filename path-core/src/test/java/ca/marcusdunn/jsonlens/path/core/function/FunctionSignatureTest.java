package ca.marcusdunn.jsonlens.path.core.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FunctionSignatureTest {

    @Test
    @Requirement("2.4/function-name")
    void functionNames() {
        for (String name : List.of("a", "z", "length", "a_1", "abc9", "z__", "a0_z")) {
            assertTrue(FunctionSignature.isValidName(name), name);
        }
        for (String name : List.of("", "A", "aB", "1a", "_a", "a-b", "a b", "é", "a`", "a{", "a/", "a:", "`a", "{a")) {
            assertFalse(FunctionSignature.isValidName(name), name);
        }
    }

    @Test
    @Requirement("3.2/registry")
    void standardSignatures() {
        assertEquals(
                List.of("length", "count", "match", "search", "value"),
                FunctionSignature.STANDARD.stream().map(FunctionSignature::name).toList());
        assertEquals(List.of(FunctionType.NODES), FunctionSignature.COUNT.parameterTypes());
        assertEquals(FunctionType.LOGICAL, FunctionSignature.SEARCH.resultType());
    }

    @Test
    void parameterTypesAreCopied() {
        List<FunctionType> types = new ArrayList<>(List.of(FunctionType.VALUE));
        FunctionSignature signature = new FunctionSignature("f", FunctionType.VALUE, types);
        types.add(FunctionType.NODES);
        assertEquals(List.of(FunctionType.VALUE), signature.parameterTypes());
    }
}
