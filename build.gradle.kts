// The release bundle for the Maven Central Portal.
//
// "./gradlew clean centralBundle" publishes each module to build/staging-deploy, with checksums
// and (when the signing key is present) signatures, and zips that directory into
// build/central-bundle.zip. Upload the zip on the Central Portal, or with its API.

plugins {
    base
}

// The projects that publish an artifact. The BOM gives their versions.
val publishedProjects = listOf(
    "model", "model-testkit", "path-core", "path-parser", "path-evaluator",
    "jackson", "kotlinx-serialization", "mapped", "pointer", "patch", "bom",
)

tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Makes the bundle of all published modules for the Maven Central Portal."
    dependsOn(publishedProjects.map { ":$it:publishAllPublicationsToStagingRepository" })
    from(layout.buildDirectory.dir("staging-deploy")) {
        // The Central Portal makes its own metadata.
        exclude("**/maven-metadata*")
    }
    archiveFileName = "central-bundle.zip"
    destinationDirectory = layout.buildDirectory
    val releaseVersion = project.version.toString()
    doFirst {
        check(!releaseVersion.endsWith("-SNAPSHOT")) { "Maven Central does not take a SNAPSHOT version: $releaseVersion" }
    }
}
