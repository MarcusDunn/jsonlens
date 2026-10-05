package jsonlens.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavadocTool
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Documents more than one JPMS module in one Javadoc site.
 *
 * The task gives javadoc one `--module-source-path` for each module, so that the links between
 * the modules work. Other modules, for example libraries, come from [modulePath].
 */
@CacheableTask
abstract class AggregateJavadoc @Inject constructor(
    private val exec: ExecOperations,
    private val files: FileSystemOperations,
) : DefaultTask() {

    /** The javadoc tool of the toolchain. */
    @get:Nested
    abstract val javadocTool: Property<JavadocTool>

    /** The module names and their source directories. [sources] tracks the files. */
    @get:Internal
    abstract val modules: MapProperty<String, String>

    /** The names of the modules to document. */
    @get:Input
    val moduleNames: List<String>
        get() = modules.get().keys.sorted()

    /** The source files of all modules. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** The modules that the documented modules require, as JAR files. */
    @get:Classpath
    abstract val modulePath: ConfigurableFileCollection

    /** The directory of the external snippets (JEP 413). */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val snippetPath: DirectoryProperty

    /** The overview page. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val overview: RegularFileProperty

    /** The title of the site. */
    @get:Input
    abstract val title: Property<String>

    /** The Java release of the sources. */
    @get:Input
    abstract val release: Property<Int>

    /** The output directory. */
    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    @TaskAction
    fun document() {
        val output = destination.get().asFile
        files.delete { delete(output) }
        output.mkdirs()
        val arguments = mutableListOf(
            "-d", output.path,
            "--release", release.get().toString(),
            "-Xdoclint:all", "-Werror",
            "-quiet",
            "-encoding", "UTF-8",
            "-doctitle", title.get(),
            "-windowtitle", title.get(),
            "-overview", overview.get().asFile.path,
            "--snippet-path", snippetPath.get().asFile.path,
            "--module-path", modulePath.asPath,
            "--module", moduleNames.joinToString(","),
        )
        modules.get().forEach { (name, directory) ->
            arguments += listOf("--module-source-path", "$name=$directory")
        }
        exec.exec {
            executable = javadocTool.get().executablePath.asFile.path
            args(arguments)
        }
    }
}
