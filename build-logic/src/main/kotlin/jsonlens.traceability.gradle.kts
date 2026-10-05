// Connects the tests of a module to the requirement catalog.
// The "check" task fails when a test refers to an unknown requirement,
// or when a requirement that the module owns has no test.

import jsonlens.buildlogic.TraceabilityExtension
import jsonlens.buildlogic.VerifyTraceability

plugins {
    java
}

val traceability = extensions.create<TraceabilityExtension>("traceability").apply {
    catalog.convention(layout.settingsDirectory.file("spec/requirements.txt"))
    owner.convention(project.name)
    enforceCoverage.convention(true)
}

val verifyTraceability = tasks.register<VerifyTraceability>("verifyTraceability") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies that the tests of this module cover the requirements that it owns."
    catalog = traceability.catalog
    owner = traceability.owner
    enforceCoverage = traceability.enforceCoverage
    testSources.from(sourceSets.named("test").map { it.allSource })
    report = layout.buildDirectory.file("reports/traceability/requirements.md")
}

dependencies {
    // Supplies the @Requirement annotation.
    testImplementation(project(":test-support"))
}

tasks.named("check") {
    dependsOn(verifyTraceability)
}
