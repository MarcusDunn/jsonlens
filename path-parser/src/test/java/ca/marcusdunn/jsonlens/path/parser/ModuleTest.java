package ca.marcusdunn.jsonlens.path.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.testsupport.ModuleDescriptors;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.lang.module.ModuleDescriptor;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleTest {

    private static final ModuleDescriptor MODULE = ModuleDescriptors.named("ca.marcusdunn.jsonlens.path.parser");

    @Test
    @Requirement("lib/parser-dependencies")
    void requiresOnlyTheCoreModuleAtRuntime() {
        assertEquals(Set.of("ca.marcusdunn.jsonlens.path.core"), ModuleDescriptors.runtimeModules(MODULE));
        assertEquals(Set.of("org.jspecify"), ModuleDescriptors.compileOnlyModules(MODULE));
    }

    @Test
    @Requirement("lib/parser-null-marked")
    void allExportedPackagesAreNullMarked() {
        assertEquals(Set.of("ca.marcusdunn.jsonlens.path.parser"), Set.copyOf(MODULE.packages()));
        assertEquals(Set.of(), ModuleDescriptors.exportedPackagesWithoutNullMarked(MODULE));
    }

    @Test
    void publicApi() {
        assertEquals(
                Set.of(
                        "ca.marcusdunn.jsonlens.path.parser.FunctionRegistrationError",
                        "ca.marcusdunn.jsonlens.path.parser.FunctionRegistrationError$DuplicateName",
                        "ca.marcusdunn.jsonlens.path.parser.FunctionRegistrationError$InvalidName",
                        "ca.marcusdunn.jsonlens.path.parser.JsonPathParser",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$ArgumentTypeMismatch",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$ControlCharacter",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$IntegerOutOfRange",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$InvalidEscape",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$InvalidInteger",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$InvalidUtf8",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$LiteralWithoutComparison",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$NestingTooDeep",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$NonSingularQuery",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$NotComparable",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$NumberOutOfRange",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$UnexpectedCharacter",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$UnexpectedEnd",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$UnknownFunction",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$UnpairedSurrogate",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$ValueTypeInTest",
                        "ca.marcusdunn.jsonlens.path.parser.ParseError$WrongArgumentCount"),
                ModuleDescriptors.publicExportedTypes(JsonPathParser.class));
    }
}
