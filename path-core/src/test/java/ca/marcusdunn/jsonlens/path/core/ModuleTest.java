package ca.marcusdunn.jsonlens.path.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ca.marcusdunn.jsonlens.testsupport.ModuleDescriptors;
import ca.marcusdunn.jsonlens.testsupport.Requirement;
import java.lang.module.ModuleDescriptor;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleTest {

    private static final ModuleDescriptor MODULE = ModuleDescriptors.named("ca.marcusdunn.jsonlens.path.core");

    @Test
    @Requirement("lib/core-dependencies")
    void requiresOnlyTheModelModuleAtRuntime() {
        assertEquals(Set.of("ca.marcusdunn.jsonlens.model"), ModuleDescriptors.runtimeModules(MODULE));
        assertEquals(Set.of("org.jspecify"), ModuleDescriptors.compileOnlyModules(MODULE));
    }

    @Test
    @Requirement("lib/core-null-marked")
    void allExportedPackagesAreNullMarked() {
        assertEquals(Set.of(), ModuleDescriptors.exportedPackagesWithoutNullMarked(MODULE));
    }

    @Test
    @Requirement("lib/no-json-types-in-api")
    void publicApiHasNoJsonValueTypes() {
        // A JSON value type in this list is a design error. Values are always the caller's type N.
        assertEquals(
                Set.of(
                        "ca.marcusdunn.jsonlens.path.core.function.FunctionSignature",
                        "ca.marcusdunn.jsonlens.path.core.function.FunctionType",
                        "ca.marcusdunn.jsonlens.path.core.path.NormalizedPath",
                        "ca.marcusdunn.jsonlens.path.core.path.NormalizedPath$Element",
                        "ca.marcusdunn.jsonlens.path.core.path.NormalizedPath$Member",
                        "ca.marcusdunn.jsonlens.path.core.path.NormalizedPath$Root",
                        "ca.marcusdunn.jsonlens.path.core.query.ComparableExpression",
                        "ca.marcusdunn.jsonlens.path.core.query.ComparisonOperator",
                        "ca.marcusdunn.jsonlens.path.core.query.FilterQuery",
                        "ca.marcusdunn.jsonlens.path.core.query.FunctionArgument",
                        "ca.marcusdunn.jsonlens.path.core.query.FunctionArgument$Logical",
                        "ca.marcusdunn.jsonlens.path.core.query.FunctionArgument$Nodes",
                        "ca.marcusdunn.jsonlens.path.core.query.FunctionArgument$Value",
                        "ca.marcusdunn.jsonlens.path.core.query.FunctionCall",
                        "ca.marcusdunn.jsonlens.path.core.query.FunctionCall$Logical",
                        "ca.marcusdunn.jsonlens.path.core.query.FunctionCall$Nodes",
                        "ca.marcusdunn.jsonlens.path.core.query.FunctionCall$Value",
                        "ca.marcusdunn.jsonlens.path.core.query.Identifier",
                        "ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery",
                        "ca.marcusdunn.jsonlens.path.core.query.Literal",
                        "ca.marcusdunn.jsonlens.path.core.query.Literal$BooleanLiteral",
                        "ca.marcusdunn.jsonlens.path.core.query.Literal$NullLiteral",
                        "ca.marcusdunn.jsonlens.path.core.query.Literal$NumberLiteral",
                        "ca.marcusdunn.jsonlens.path.core.query.Literal$StringLiteral",
                        "ca.marcusdunn.jsonlens.path.core.query.LogicalExpression",
                        "ca.marcusdunn.jsonlens.path.core.query.LogicalExpression$And",
                        "ca.marcusdunn.jsonlens.path.core.query.LogicalExpression$Comparison",
                        "ca.marcusdunn.jsonlens.path.core.query.LogicalExpression$Not",
                        "ca.marcusdunn.jsonlens.path.core.query.LogicalExpression$Or",
                        "ca.marcusdunn.jsonlens.path.core.query.NodesExpression",
                        "ca.marcusdunn.jsonlens.path.core.query.Segment",
                        "ca.marcusdunn.jsonlens.path.core.query.Segment$Child",
                        "ca.marcusdunn.jsonlens.path.core.query.Segment$Descendant",
                        "ca.marcusdunn.jsonlens.path.core.query.Selector",
                        "ca.marcusdunn.jsonlens.path.core.query.Selector$Filter",
                        "ca.marcusdunn.jsonlens.path.core.query.Selector$Index",
                        "ca.marcusdunn.jsonlens.path.core.query.Selector$Name",
                        "ca.marcusdunn.jsonlens.path.core.query.Selector$Slice",
                        "ca.marcusdunn.jsonlens.path.core.query.Selector$Wildcard",
                        "ca.marcusdunn.jsonlens.path.core.query.SingularQuery",
                        "ca.marcusdunn.jsonlens.path.core.query.SingularSegment",
                        "ca.marcusdunn.jsonlens.path.core.query.SingularSegment$Index",
                        "ca.marcusdunn.jsonlens.path.core.query.SingularSegment$Name"),
                ModuleDescriptors.publicExportedTypes(ca.marcusdunn.jsonlens.path.core.query.JsonPathQuery.class));
    }
}
