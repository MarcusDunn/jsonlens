package jsonlens.buildlogic

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/** Configures the traceability check of one module. */
interface TraceabilityExtension {
    /** The requirement catalog. The default is `spec/requirements.txt` in the root directory. */
    val catalog: RegularFileProperty

    /** The owner name in the catalog. The default is the project name. */
    val owner: Property<String>

    /**
     * If true, the check fails when a requirement that this module owns has no test.
     * Set it to false only while the module is not complete.
     */
    val enforceCoverage: Property<Boolean>
}
