package jsonlens.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Compares the requirement catalog with the `@Requirement` annotations in the Java and Kotlin test sources.
 *
 * The catalog has one requirement on each line, in this form:
 * `id | type | owner | statement`. The type is `requirement`, `option`, or `declined`.
 * Lines that start with `#` and empty lines are comments.
 *
 * The task always fails when a test refers to an identifier that is not in the catalog.
 * When [enforceCoverage] is true, the task also fails when a `requirement` or `option`
 * that this module owns has no test.
 */
@CacheableTask
abstract class VerifyTraceability : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val catalog: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSources: ConfigurableFileCollection

    @get:Input
    abstract val owner: Property<String>

    @get:Input
    abstract val enforceCoverage: Property<Boolean>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val entries = parseCatalog(catalog.get().asFile)
        val known = entries.associateBy { it.id }
        val references = scanTests()

        val problems = mutableListOf<String>()
        references.filter { it.id !in known }.forEach {
            problems += "${it.location}: unknown requirement '${it.id}'"
        }

        val owned = entries.filter { it.owner == owner.get() }
        val testsById = references.groupBy { it.id }
        val uncovered = owned.filter { it.type != "declined" && it.id !in testsById }
        if (enforceCoverage.get()) {
            uncovered.forEach { problems += "requirement '${it.id}' has no test" }
        }

        writeReport(owned, testsById, uncovered.size)

        if (problems.isNotEmpty()) {
            throw GradleException(
                "Traceability check failed for '${owner.get()}':\n  " + problems.joinToString("\n  ")
            )
        }
        if (uncovered.isNotEmpty()) {
            logger.warn("${uncovered.size} requirements owned by '${owner.get()}' have no test (coverage not enforced).")
        }
    }

    private fun parseCatalog(file: File): List<Entry> {
        val entries = mutableListOf<Entry>()
        val problems = mutableListOf<String>()
        file.readLines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val fields = line.split("|", limit = 4).map { it.trim() }
            if (fields.size != 4 || fields.any { it.isEmpty() }) {
                problems += "${file.name}:${index + 1}: expected 'id | type | owner | statement'"
                return@forEachIndexed
            }
            val (id, type, entryOwner, statement) = fields
            if (type !in TYPES) {
                problems += "${file.name}:${index + 1}: type must be one of $TYPES"
            }
            if (entries.any { it.id == id }) {
                problems += "${file.name}:${index + 1}: duplicate requirement '$id'"
            }
            entries += Entry(id, type, entryOwner, statement)
        }
        if (problems.isNotEmpty()) {
            throw GradleException("The requirement catalog is not valid:\n  " + problems.joinToString("\n  "))
        }
        return entries
    }

    private fun scanTests(): List<Reference> {
        val references = mutableListOf<Reference>()
        testSources.asFileTree.matching { include("**/*.java", "**/*.kt") }.files.sortedBy { it.path }.forEach { file ->
            val pending = mutableListOf<Pair<String, Int>>()
            file.readLines().forEachIndexed { index, line ->
                ANNOTATION.findAll(line).forEach { pending += it.groupValues[1] to index + 1 }
                val declaration = DECLARATION.find(line)
                if (pending.isNotEmpty() && declaration != null) {
                    val (keyword, name) = declaration.destructured
                    val test = if (keyword == "void" || keyword == "fun") "${file.nameWithoutExtension}.$name" else name
                    pending.forEach { (id, lineNumber) ->
                        references += Reference(id, test, "${file.name}:$lineNumber")
                    }
                    pending.clear()
                }
            }
        }
        return references
    }

    private fun writeReport(owned: List<Entry>, testsById: Map<String, List<Reference>>, uncovered: Int) {
        val text = buildString {
            appendLine("# Requirement traceability: ${owner.get()}")
            appendLine()
            appendLine("${owned.size} requirements, $uncovered without a test.")
            appendLine()
            appendLine("| Requirement | Type | Statement | Tests |")
            appendLine("|---|---|---|---|")
            owned.forEach { entry ->
                val tests = testsById[entry.id].orEmpty().map { it.test }.distinct()
                val cell = when {
                    entry.type == "declined" -> "not applicable"
                    tests.isEmpty() -> "**none**"
                    else -> tests.joinToString("<br>") { "`$it`" }
                }
                appendLine("| `${entry.id}` | ${entry.type} | ${entry.statement.replace("|", "\\|")} | $cell |")
            }
        }
        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText(text)
        }
    }

    private data class Entry(val id: String, val type: String, val owner: String, val statement: String)

    private data class Reference(val id: String, val test: String, val location: String)

    private companion object {
        val TYPES = setOf("requirement", "option", "declined")
        val ANNOTATION = Regex("""@Requirement\(\s*"([^"]*)"\s*\)""")
        val DECLARATION = Regex("""\b(void|fun|class|record|interface|enum|object)\s+([A-Za-z_][A-Za-z0-9_]*)""")
    }
}
