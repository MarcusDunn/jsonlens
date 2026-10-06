pluginManagement {
    includeBuild("build-logic")
}

plugins {
    // Downloads a JDK toolchain when the local machine has no match.
    // The Nix dev shell supplies the JDK, so this has no effect there.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "jsonlens"

include("model", "model-testkit", "path-core", "path-parser", "path-evaluator", "jackson", "kotlinx-serialization", "mapped", "pointer", "patch", "merge-patch", "bom", "test-support", "docs", "fuzz", "benchmarks")
