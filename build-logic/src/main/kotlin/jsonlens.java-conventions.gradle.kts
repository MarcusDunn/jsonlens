// Settings for all Java projects in this build.

import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    `java-library`
    id("net.ltgt.errorprone")
}

val libs = the<VersionCatalogsExtension>().named("libs")

fun catalogVersion(alias: String): String = libs.findVersion(alias).get().requiredVersion

fun catalogLibrary(alias: String) = libs.findLibrary(alias).get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(catalogVersion("java-toolchain"))
    }
}

dependencies {
    // JSpecify annotations are in the API, but they are not necessary at runtime.
    compileOnlyApi(catalogLibrary("jspecify"))
    testImplementation(catalogLibrary("jspecify"))
    errorprone(catalogLibrary("errorprone-core"))
    errorprone(catalogLibrary("nullaway"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = catalogVersion("java-release").toInt()
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-parameters"))
    options.errorprone {
        // NullAway checks all code in @NullMarked scope, with JSpecify semantics.
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:OnlyNullMarked", "true")
        option("NullAway:JSpecifyMode", "true")
    }
}

// The code samples of the API documentation (JEP 413). They are tests of the :docs project.
val snippets = layout.settingsDirectory.dir("docs/src/test/java")

tasks.withType<Javadoc>().configureEach {
    inputs.dir(snippets).withPropertyName("snippets").withPathSensitivity(PathSensitivity.RELATIVE)
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Werror", true)
        addStringOption("-snippet-path", snippets.asFile.path)
        // No date in the pages, so that the javadoc JAR is reproducible.
        addBooleanOption("notimestamp", true)
        // Colors the code in snippets and code blocks (JDK 23 and later).
        addBooleanOption("-syntax-highlight", true)
    }
}

// Reproducible archives: the same sources give the same bytes, so anyone can rebuild a release
// and compare it with the published JARs.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    // A folder that is empty in one checkout (git does not track folders) must not change the archive.
    includeEmptyDirs = false
    filePermissions { unix("rw-r--r--") }
    dirPermissions { unix("rwxr-xr-x") }
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(catalogVersion("junit"))
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
