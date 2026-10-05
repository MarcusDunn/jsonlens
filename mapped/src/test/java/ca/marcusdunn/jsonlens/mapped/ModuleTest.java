package ca.marcusdunn.jsonlens.mapped;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.testsupport.ModuleDescriptors;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.lang.module.ModuleDescriptor;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleTest {

    private static final ModuleDescriptor MODULE = ModuleDescriptors.named("ca.marcusdunn.jsonlens.mapped");

    @Test
    @Requirement("lib/mapped-dependencies")
    void requiresOnlyTheCoreModuleAtRuntime() {
        assertEquals(Set.of("ca.marcusdunn.jsonlens.model"), ModuleDescriptors.runtimeModules(MODULE));
        assertEquals(Set.of("org.jspecify"), ModuleDescriptors.compileOnlyModules(MODULE));
    }

    @Test
    @Requirement("lib/mapped-null-marked")
    void allExportedPackagesAreNullMarked() {
        assertEquals(Set.of("ca.marcusdunn.jsonlens.mapped"), Set.copyOf(MODULE.packages()));
        assertEquals(Set.of(), ModuleDescriptors.exportedPackagesWithoutNullMarked(MODULE));
    }

    @Test
    void publicApi() {
        assertEquals(
                Set.of(
                        "ca.marcusdunn.jsonlens.mapped.MappedJson",
                        "ca.marcusdunn.jsonlens.mapped.MappedJsonError",
                        "ca.marcusdunn.jsonlens.mapped.MappedJsonError$FileTooLarge",
                        "ca.marcusdunn.jsonlens.mapped.MappedJsonError$InvalidJson",
                        "ca.marcusdunn.jsonlens.mapped.MappedJsonError$InvalidUtf8",
                        "ca.marcusdunn.jsonlens.mapped.MappedJsonError$IoFailure",
                        "ca.marcusdunn.jsonlens.mapped.MappedNode"),
                ModuleDescriptors.publicExportedTypes(MappedJson.class));
    }
}
