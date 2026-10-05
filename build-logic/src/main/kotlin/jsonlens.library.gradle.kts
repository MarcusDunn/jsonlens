// Settings for a published library module.
// A library has no runtime dependencies other than other modules of this build, except an
// adapter, which also requires its JSON library.

import org.cyclonedx.gradle.CyclonedxDirectTask

plugins {
    id("jsonlens.java-conventions")
    id("jsonlens.publishing")
    id("jsonlens.traceability")
    id("jsonlens.coverage")
    id("org.cyclonedx.bom")
}

base {
    archivesName = "jsonlens-${project.name}"
}

java {
    withSourcesJar()
    withJavadocJar()
}

// The SBOM (CycloneDX) of the module: the module and its runtime dependencies. It is published
// with the classifier "cyclonedx", as other CycloneDX tools for the JVM do.
val directSbom = tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
    xmlOutput.unsetConvention()
    includeConfigs = listOf("runtimeClasspath")
}

// The modules of this build get their published names in the SBOM.
val sbom = tasks.register<jsonlens.buildlogic.PublishedSbom>("publishedSbom") {
    input = directSbom.flatMap { it.jsonOutput }
    componentGroup = project.group.toString()
    artifactPrefix = "jsonlens-"
    output = layout.buildDirectory.file("reports/sbom/jsonlens-${project.name}-cyclonedx.json")
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            artifact(sbom.flatMap { it.output }) {
                classifier = "cyclonedx"
                extension = "json"
                builtBy(sbom)
            }
        }
    }
}
