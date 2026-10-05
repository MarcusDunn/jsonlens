// Settings for a published library module written in Kotlin.
// The module descriptor is src/main/java/module-info.java.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("jsonlens.library")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.dokka")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(libs.findVersion("java-release").get().requiredVersion)
        allWarningsAsErrors = true
        // Apply the JSpecify annotations of the core module as strict Kotlin nullness.
        freeCompilerArgs.add("-Xjspecify-annotations=strict")
        // Do not add runtime null checks for the results of Java calls. The Java APIs that this build
        // calls do not return null, so such checks are dead code (and equivalent mutants for PIT).
        freeCompilerArgs.add("-Xno-call-assertions")
    }
}

// Compile module-info.java with the Kotlin classes, so that javac can check the module.
// The module name is "ca.marcusdunn.jsonlens." and the first word of the project name.
tasks.named<JavaCompile>("compileJava") {
    options.compilerArgumentProviders.add(objects.newInstance<jsonlens.buildlogic.PatchModuleArgument>().apply {
        moduleName = "ca.marcusdunn.jsonlens." + project.name.substringBefore('-')
        classes = tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin")
            .flatMap { it.destinationDirectory }
    })
}

// Javadoc cannot read Kotlin sources. Dokka documents the module, and the javadoc JAR has the
// Dokka HTML pages.
tasks.named<Javadoc>("javadoc") {
    enabled = false
}

dokka {
    moduleName = "jsonlens-${project.name}"
    dokkaSourceSets.configureEach {
        // The module page: Module.md in the project directory.
        includes.from(layout.projectDirectory.file("Module.md"))
        jdkVersion = libs.findVersion("java-release").get().requiredVersion.toInt()
    }
    // A fixed footer: the default has the current year, so the javadoc JAR would not be reproducible.
    pluginsConfiguration.html {
        footerMessage = "jsonlens. MIT License."
    }
}

tasks.named<Jar>("javadocJar") {
    from(tasks.named<org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask>("dokkaGeneratePublicationHtml").flatMap { it.outputDirectory })
}
