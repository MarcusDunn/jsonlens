package jsonlens.buildlogic

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider

/**
 * Gives javac the argument `--patch-module <module>=<classes>`, so that module-info.java
 * compiles together with the classes of another compiler, for example Kotlin.
 */
abstract class PatchModuleArgument : CommandLineArgumentProvider {
    @get:Input
    abstract val moduleName: Property<String>

    @get:Classpath
    abstract val classes: DirectoryProperty

    override fun asArguments(): Iterable<String> =
        listOf("--patch-module", "${moduleName.get()}=${classes.get().asFile.path}")
}
