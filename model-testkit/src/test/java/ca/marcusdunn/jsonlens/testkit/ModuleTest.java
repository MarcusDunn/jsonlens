package ca.marcusdunn.jsonlens.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.testsupport.ModuleDescriptors;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.lang.module.ModuleDescriptor;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleTest {

    private static final ModuleDescriptor MODULE = ModuleDescriptors.named("ca.marcusdunn.jsonlens.testkit");

    @Test
    @Requirement("lib/testkit-dependencies")
    void requiresTheSuiteModulesAndJUnitAtRuntime() {
        assertEquals(Set.of("ca.marcusdunn.jsonlens.model", "ca.marcusdunn.jsonlens.path.core", "ca.marcusdunn.jsonlens.mapped",
                        "ca.marcusdunn.jsonlens.path.parser", "ca.marcusdunn.jsonlens.path.evaluator", "ca.marcusdunn.jsonlens.patch", "org.junit.jupiter.api"),
                ModuleDescriptors.runtimeModules(MODULE));
        assertEquals(Set.of("org.jspecify"), ModuleDescriptors.compileOnlyModules(MODULE));
    }

    @Test
    @Requirement("lib/testkit-null-marked")
    void allExportedPackagesAreNullMarked() {
        assertEquals(Set.of(), ModuleDescriptors.exportedPackagesWithoutNullMarked(MODULE));
    }

    @Test
    void publicApi() {
        assertEquals(
                Set.of(
                        "ca.marcusdunn.jsonlens.testkit.ComplianceKit",
                        "ca.marcusdunn.jsonlens.testkit.JsonEditorContract",
                        "ca.marcusdunn.jsonlens.testkit.JsonFactoryContract",
                        "ca.marcusdunn.jsonlens.testkit.JsonModelContract",
                        "ca.marcusdunn.jsonlens.testkit.ModelVerifier",
                        "ca.marcusdunn.jsonlens.testkit.RandomJson",
                        "ca.marcusdunn.jsonlens.testkit.Rule",
                        "ca.marcusdunn.jsonlens.testkit.Violation"),
                ModuleDescriptors.publicExportedTypes(ModelVerifier.class));
    }
}
