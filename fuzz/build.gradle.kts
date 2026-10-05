// Coverage-guided fuzz tests (Jazzer). See README.md in this directory.
//
// - "test" runs each fuzz test once for each input in src/test/resources (regression mode).
//   "check" includes it, so each input that once found a problem stays a test.
// - "fuzz" runs one fuzz test in fuzzing mode. Select it with --tests, for example:
//   ./gradlew :fuzz:fuzz --tests '*ParserFuzzTest*'

plugins {
    id("jsonlens.java-conventions")
}

description = "Coverage-guided fuzz tests of jsonlens. It is not published."

dependencies {
    testImplementation(projects.pathCore)
    testImplementation(projects.pathParser)
    testImplementation(projects.pathEvaluator)
    testImplementation(projects.jackson)
    testImplementation(projects.kotlinxSerialization)
    testImplementation(projects.mapped)
    testImplementation(projects.pointer)
    testImplementation(projects.patch)
    testImplementation(testFixtures(projects.model))
    testImplementation(libs.jazzer.junit)
}

tasks.withType<Test>().configureEach {
    // Jazzer loads its instrumentation agent at runtime.
    jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xss8m")
    systemProperty("jazzer.instrument", "ca.marcusdunn.jsonlens.**")
    maxHeapSize = "2g"
}

val test = sourceSets.named("test")
tasks.register<Test>("fuzz") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs one fuzz test in fuzzing mode. Select it with --tests."
    testClassesDirs = files(test.map { it.output.classesDirs })
    classpath = files(test.map { it.runtimeClasspath })
    useJUnitPlatform()
    environment("JAZZER_FUZZ", "1")
    // Each run must fuzz, so the result is never up to date or from the cache.
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    testLogging {
        showStandardStreams = true
    }
}
