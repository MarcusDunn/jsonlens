package ca.marcusdunn.jsonlens.mergepatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.testsupport.ModuleDescriptors;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.lang.module.ModuleDescriptor;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleTest {

    private static final ModuleDescriptor MODULE = ModuleDescriptors.named("ca.marcusdunn.jsonlens.mergepatch");

    @Test
    @Requirement("lib/merge-patch-dependencies")
    void requiresOnlyTheModelModuleAtRuntime() {
        assertEquals(Set.of("ca.marcusdunn.jsonlens.model"), ModuleDescriptors.runtimeModules(MODULE));
        assertEquals(Set.of("org.jspecify"), ModuleDescriptors.compileOnlyModules(MODULE));
    }

    @Test
    @Requirement("lib/merge-patch-null-marked")
    void allExportedPackagesAreNullMarked() {
        assertEquals(Set.of(), ModuleDescriptors.exportedPackagesWithoutNullMarked(MODULE));
    }

    @Test
    void publicApiHasNoJsonValueTypes() {
        // The patch document and the targets are nodes of the caller's models.
        assertEquals(
                Set.of(
                        "ca.marcusdunn.jsonlens.mergepatch.JsonMergePatch",
                        "ca.marcusdunn.jsonlens.mergepatch.MergePatchError",
                        "ca.marcusdunn.jsonlens.mergepatch.MergePatchError$DuplicateName",
                        "ca.marcusdunn.jsonlens.mergepatch.MergePatchError$TargetDuplicateName",
                        "ca.marcusdunn.jsonlens.mergepatch.MergePatchError$ValueNotRepresentable"),
                ModuleDescriptors.publicExportedTypes(JsonMergePatch.class));
    }
}
