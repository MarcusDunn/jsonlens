package jsonlens.buildlogic

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gives the components of this build in an SBOM their published names.
 *
 * The CycloneDX plugin names a module of this build by its Gradle project, for example
 * `ca.marcusdunn.jsonlens:model`, with a package URL that has the qualifier `project_path`. The
 * published artifact is `jsonlens-model`. This task changes the name and each package URL of the
 * components of [group] to the artifact name, and removes the qualifier. It changes each reference
 * in the same way, so that the references stay correct.
 */
abstract class PublishedSbom : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val input: RegularFileProperty

    @get:Input
    abstract val componentGroup: Property<String>

    @get:Input
    abstract val artifactPrefix: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun rewrite() {
        val document = JsonSlurper().parse(input.get().asFile)
        output.get().asFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(rewrite(document))) + "\n")
    }

    private fun rewrite(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries.associate { (key, entry) ->
            key to if (key == "name" && value["group"] == componentGroup.get()) artifact(entry as String) else rewrite(entry)
        }
        is List<*> -> value.map { rewrite(it) }
        is String -> purl(value)
        else -> value
    }

    private fun artifact(name: String): String = if (name.startsWith(artifactPrefix.get())) name else artifactPrefix.get() + name

    /** A package URL of this build, with the artifact name and no qualifiers. */
    private fun purl(value: String): String {
        val start = "pkg:maven/${componentGroup.get()}/"
        if (!value.startsWith(start)) {
            return value
        }
        val name = value.substring(start.length).substringBefore('@')
        val version = value.substringAfter('@').substringBefore('?')
        return start + artifact(name) + "@" + version
    }
}
