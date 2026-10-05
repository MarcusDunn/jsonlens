// JMH benchmarks with large JSON files, for the Jackson, kotlinx, and memory-mapped models.
// See README.md in this directory.
//
// - "jmh" runs the benchmarks and writes results/<label>.json.
// - "jmhCompare" compares two result files.

import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("jsonlens.java-conventions")
}

description = "JMH benchmarks of jsonlens with large JSON files. It is not published."

dependencies {
    implementation(projects.pathCore)
    implementation(projects.pathParser)
    implementation(projects.pathEvaluator)
    implementation(projects.jackson)
    implementation(projects.kotlinxSerialization)
    implementation(projects.mapped)
    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.generator)
}

tasks.withType<JavaCompile>().configureEach {
    // The JMH annotation processor generates code that does not pass the strict checks.
    options.compilerArgs.remove("-Werror")
    options.errorprone {
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*/build/generated/.*"
    }
}

val dataDir = layout.buildDirectory.dir("benchmark-data")
val resultsPath: String = layout.projectDirectory.dir("results").asFile.absolutePath
val label = providers.gradleProperty("label").orElse("latest")

tasks.register<JavaExec>("jmh") {
    group = "benchmark"
    description = "Runs the JMH benchmarks. Properties: -Plabel, -Pjmh.include, -Pjmh.params, -Pjmh.heap, -Pjmh.args."
    mainClass = "org.openjdk.jmh.Main"
    classpath = sourceSets.main.get().runtimeClasspath
    val include = providers.gradleProperty("jmh.include").orElse(".*")
    val params = providers.gradleProperty("jmh.params").orElse("")
    val heap = providers.gradleProperty("jmh.heap").orElse("12g")
    val extra = providers.gradleProperty("jmh.args").orElse("")
    val data = dataDir.map { it.asFile.absolutePath }
    val results = resultsPath
    val result = label.map { "$results/$it.json" }
    argumentProviders.add(CommandLineArgumentProvider {
        val args = mutableListOf(
            include.get(),
            "-rf", "json", "-rff", result.get(),
            "-prof", "gc",
            "-jvmArgsAppend", "-Xmx${heap.get()} -Djsonlens.benchmark.data=${data.get()}",
        )
        params.get().split(';').filter { it.isNotBlank() }.forEach { args += listOf("-p", it.trim()) }
        args += extra.get().split(' ').filter { it.isNotBlank() }
        args
    })
    doFirst { File(results).mkdirs() }
    // Each run must measure, so the result is never up to date or from the cache.
    outputs.upToDateWhen { false }
}

tasks.register<JavaExec>("jmhCompare") {
    group = "benchmark"
    description = "Compares two result files. Properties: -Pbase (default: baseline), -Plabel (default: latest)."
    mainClass = "ca.marcusdunn.jsonlens.benchmarks.Compare"
    classpath = sourceSets.main.get().runtimeClasspath
    val base = providers.gradleProperty("base").orElse("baseline")
    val results = resultsPath
    val baseFile = base.map { "$results/$it.json" }
    val labelFile = label.map { "$results/$it.json" }
    argumentProviders.add(CommandLineArgumentProvider { listOf(baseFile.get(), labelFile.get()) })
    outputs.upToDateWhen { false }
}

tasks.register<JavaExec>("footprint") {
    group = "benchmark"
    description = "Measures the heap in use after a load and after a full walk. Properties: -Plabel, -Pmegabytes, -Pmodels, -Pjmh.heap."
    mainClass = "ca.marcusdunn.jsonlens.benchmarks.Footprint"
    classpath = sourceSets.main.get().runtimeClasspath
    maxHeapSize = providers.gradleProperty("jmh.heap").orElse("12g").get()
    systemProperty("jsonlens.benchmark.data", dataDir.get().asFile.absolutePath)
    val megabytes = providers.gradleProperty("megabytes").orElse("100")
    val results = resultsPath
    val output = label.map { "$results/$it-footprint.txt" }
    val models = providers.gradleProperty("models").orElse("jackson,kotlinx,mapped")
    argumentProviders.add(CommandLineArgumentProvider { listOf(megabytes.get(), output.get(), models.get()) })
    outputs.upToDateWhen { false }
}
