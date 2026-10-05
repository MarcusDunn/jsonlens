package ca.marcusdunn.jsonlens.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.testsupport.ModuleDescriptors;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.lang.module.ModuleDescriptor;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleTest {

    private static final ModuleDescriptor MODULE = ModuleDescriptors.named("ca.marcusdunn.jsonlens.model");

    @Test
    @Requirement("lib/model-dependencies")
    void requiresOnlyJavaBaseAtRuntime() {
        assertEquals(Set.of(), ModuleDescriptors.runtimeModules(MODULE));
        assertEquals(Set.of("org.jspecify"), ModuleDescriptors.compileOnlyModules(MODULE));
    }

    @Test
    @Requirement("lib/model-null-marked")
    void allExportedPackagesAreNullMarked() {
        assertEquals(Set.of(), ModuleDescriptors.exportedPackagesWithoutNullMarked(MODULE));
    }

    @Test
    @Requirement("lib/no-json-types-in-api")
    void publicApiHasNoJsonValueTypes() {
        // A JSON value type in this list is a design error. Values are always the caller's type N.
        assertEquals(
                Set.of(
                        "ca.marcusdunn.jsonlens.model.Maybe",
                        "ca.marcusdunn.jsonlens.model.Maybe$None",
                        "ca.marcusdunn.jsonlens.model.Maybe$Some",
                        "ca.marcusdunn.jsonlens.model.Result",
                        "ca.marcusdunn.jsonlens.model.Result$Err",
                        "ca.marcusdunn.jsonlens.model.Result$Ok",
                        "ca.marcusdunn.jsonlens.model.JsonDecimal",
                        "ca.marcusdunn.jsonlens.model.JsonEditor",
                        "ca.marcusdunn.jsonlens.model.JsonFactory",
                        "ca.marcusdunn.jsonlens.model.JsonKind",
                        "ca.marcusdunn.jsonlens.model.JsonModel",
                        "ca.marcusdunn.jsonlens.model.JsonNumber",
                        "ca.marcusdunn.jsonlens.model.JsonString",
                        "ca.marcusdunn.jsonlens.model.MemberCursor",
                        "ca.marcusdunn.jsonlens.model.Property"),
                ModuleDescriptors.publicExportedTypes(Result.class));
    }
}
