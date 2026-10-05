package ca.marcusdunn.jsonlens.pointer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.testsupport.ModuleDescriptors;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.lang.module.ModuleDescriptor;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleTest {

    private static final ModuleDescriptor MODULE = ModuleDescriptors.named("ca.marcusdunn.jsonlens.pointer");

    @Test
    @Requirement("lib/pointer-dependencies")
    void requiresOnlyTheModelModuleAtRuntime() {
        assertEquals(Set.of("ca.marcusdunn.jsonlens.model"), ModuleDescriptors.runtimeModules(MODULE));
        assertEquals(Set.of("org.jspecify"), ModuleDescriptors.compileOnlyModules(MODULE));
    }

    @Test
    @Requirement("lib/pointer-null-marked")
    void allExportedPackagesAreNullMarked() {
        assertEquals(Set.of(), ModuleDescriptors.exportedPackagesWithoutNullMarked(MODULE));
    }

    @Test
    void publicApi() {
        assertEquals(
                Set.of(
                        "ca.marcusdunn.jsonlens.pointer.JsonPointer",
                        "ca.marcusdunn.jsonlens.pointer.PointerError",
                        "ca.marcusdunn.jsonlens.pointer.PointerError$DuplicateName",
                        "ca.marcusdunn.jsonlens.pointer.PointerError$IndexOutOfRange",
                        "ca.marcusdunn.jsonlens.pointer.PointerError$InvalidEscape",
                        "ca.marcusdunn.jsonlens.pointer.PointerError$InvalidFragment",
                        "ca.marcusdunn.jsonlens.pointer.PointerError$InvalidIndex",
                        "ca.marcusdunn.jsonlens.pointer.PointerError$MemberNotFound",
                        "ca.marcusdunn.jsonlens.pointer.PointerError$MissingSlash",
                        "ca.marcusdunn.jsonlens.pointer.PointerError$NotAContainer"),
                ModuleDescriptors.publicExportedTypes(JsonPointer.class));
    }
}
