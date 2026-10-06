package ca.marcusdunn.jsonlens.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.testsupport.ModuleDescriptors;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.lang.module.ModuleDescriptor;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleTest {

    private static final ModuleDescriptor MODULE = ModuleDescriptors.named("ca.marcusdunn.jsonlens.patch");

    @Test
    @Requirement("lib/patch-dependencies")
    void requiresOnlyTheModelAndPointerModulesAtRuntime() {
        assertEquals(Set.of("ca.marcusdunn.jsonlens.model", "ca.marcusdunn.jsonlens.pointer"),
                ModuleDescriptors.runtimeModules(MODULE));
        assertEquals(Set.of("org.jspecify"), ModuleDescriptors.compileOnlyModules(MODULE));
    }

    @Test
    @Requirement("lib/patch-null-marked")
    void allExportedPackagesAreNullMarked() {
        assertEquals(Set.of(), ModuleDescriptors.exportedPackagesWithoutNullMarked(MODULE));
    }

    @Test
    void publicApiHasNoJsonValueTypes() {
        // The values of operations are nodes of the caller's model (type parameter P).
        assertEquals(
                Set.of(
                        "ca.marcusdunn.jsonlens.patch.JsonPatch",
                        "ca.marcusdunn.jsonlens.patch.JsonPatch$Limits",
                        "ca.marcusdunn.jsonlens.patch.Operation",
                        "ca.marcusdunn.jsonlens.patch.Operation$Add",
                        "ca.marcusdunn.jsonlens.patch.Operation$Copy",
                        "ca.marcusdunn.jsonlens.patch.Operation$Move",
                        "ca.marcusdunn.jsonlens.patch.Operation$Remove",
                        "ca.marcusdunn.jsonlens.patch.Operation$Replace",
                        "ca.marcusdunn.jsonlens.patch.Operation$Test",
                        "ca.marcusdunn.jsonlens.patch.PatchError",
                        "ca.marcusdunn.jsonlens.patch.PatchError$CopyLimitExceeded",
                        "ca.marcusdunn.jsonlens.patch.PatchError$DuplicateMember",
                        "ca.marcusdunn.jsonlens.patch.PatchError$FromNotFound",
                        "ca.marcusdunn.jsonlens.patch.PatchError$InvalidPointer",
                        "ca.marcusdunn.jsonlens.patch.PatchError$MissingMember",
                        "ca.marcusdunn.jsonlens.patch.PatchError$MoveIntoChild",
                        "ca.marcusdunn.jsonlens.patch.PatchError$NotAString",
                        "ca.marcusdunn.jsonlens.patch.PatchError$NotAnArray",
                        "ca.marcusdunn.jsonlens.patch.PatchError$NotAnObject",
                        "ca.marcusdunn.jsonlens.patch.PatchError$PathNotFound",
                        "ca.marcusdunn.jsonlens.patch.PatchError$RootRemoved",
                        "ca.marcusdunn.jsonlens.patch.PatchError$TestFailed",
                        "ca.marcusdunn.jsonlens.patch.PatchError$UnknownOperation",
                        "ca.marcusdunn.jsonlens.patch.PatchError$ValueNotRepresentable"),
                ModuleDescriptors.publicExportedTypes(JsonPatch.class));
    }
}
