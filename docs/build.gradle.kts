// The code samples of the API documentation, and the API documentation of all Java modules.
//
// - The tests in src/test/java are the external snippets (JEP 413) of the Javadoc comments.
//   "check" compiles and runs them, so the samples in the documentation always work.
// - "javadocAll" documents the Java library modules together into build/docs/javadoc of the
//   root directory, with links between the modules.

import jsonlens.buildlogic.AggregateJavadoc

plugins {
    id("jsonlens.java-conventions")
}

description = "Code samples and API documentation of jsonlens. It is not published."

dependencies {
    testImplementation(projects.pathCore)
    testImplementation(projects.pathParser)
    testImplementation(projects.pathEvaluator)
    testImplementation(projects.jackson)
    testImplementation(projects.mapped)
    testImplementation(projects.pointer)
    testImplementation(projects.patch)
    testImplementation(projects.mergePatch)
    testImplementation(projects.modelTestkit)
}

val libs = the<VersionCatalogsExtension>().named("libs")

// The libraries that the documented modules require.
val javadocModulePath = configurations.create("javadocModulePath") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

dependencies {
    javadocModulePath(libs.findLibrary("jspecify").get())
    javadocModulePath(libs.findLibrary("jackson-databind").get())
    // The test kit requires JUnit Jupiter.
    javadocModulePath(platform(libs.findLibrary("junit-bom").get()))
    javadocModulePath(libs.findLibrary("junit-jupiter-api").get())
}

val documentedModules = mapOf(
    "ca.marcusdunn.jsonlens.model" to "model",
    "ca.marcusdunn.jsonlens.path.core" to "path-core",
    "ca.marcusdunn.jsonlens.path.parser" to "path-parser",
    "ca.marcusdunn.jsonlens.path.evaluator" to "path-evaluator",
    "ca.marcusdunn.jsonlens.jackson" to "jackson",
    "ca.marcusdunn.jsonlens.mapped" to "mapped",
    "ca.marcusdunn.jsonlens.pointer" to "pointer",
    "ca.marcusdunn.jsonlens.patch" to "patch",
    "ca.marcusdunn.jsonlens.mergepatch" to "merge-patch",
    "ca.marcusdunn.jsonlens.testkit" to "model-testkit",
)

tasks.register<AggregateJavadoc>("javadocAll") {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Documents the Java library modules together into build/docs/javadoc."
    javadocTool = javaToolchains.javadocToolFor {
        languageVersion = JavaLanguageVersion.of(libs.findVersion("java-toolchain").get().requiredVersion)
    }
    val root = layout.settingsDirectory
    documentedModules.forEach { (module, directory) ->
        val sourceDirectory = root.dir("$directory/src/main/java")
        modules.put(module, sourceDirectory.asFile.path)
        sources.from(sourceDirectory)
    }
    modulePath.from(javadocModulePath)
    snippetPath = root.dir("docs/src/test/java")
    overview = layout.projectDirectory.file("overview.md")
    title = "jsonlens API"
    release = libs.findVersion("java-release").get().requiredVersion.toInt()
    destination = root.dir("build/docs/javadoc")
}
