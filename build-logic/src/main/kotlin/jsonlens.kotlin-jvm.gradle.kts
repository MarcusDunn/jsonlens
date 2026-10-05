// The Kotlin compiler for a project that is not a published library, for example the benchmarks.
// The bytecode target is the Java release of the build.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(libs.findVersion("java-release").get().requiredVersion)
    }
}
