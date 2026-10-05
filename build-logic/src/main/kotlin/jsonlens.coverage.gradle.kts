// Line, branch, and mutation coverage for a library module.
//
// - JaCoCo measures line and branch coverage. "check" fails below 100 %.
// - PIT measures mutation coverage. "check" runs it, and it fails below 100 %.
//
// Each module must cover its own code with its own tests.

plugins {
    java
    jacoco
    id("info.solidsoft.pitest")
}

val libs = the<VersionCatalogsExtension>().named("libs")

fun catalogVersion(alias: String): String = libs.findVersion(alias).get().requiredVersion

jacoco {
    toolVersion = catalogVersion("jacoco")
}

val jacocoTestReport = tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required = true
        html.required = true
    }
}

val jacocoTestCoverageVerification = tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(jacocoTestReport, jacocoTestCoverageVerification, tasks.named("pitest"))
}

pitest {
    pitestVersion = catalogVersion("pitest")
    junit5PluginVersion = catalogVersion("pitest-junit5")
    targetClasses = setOf("ca.marcusdunn.jsonlens.*")
    threads = Runtime.getRuntime().availableProcessors()
    outputFormats = setOf("HTML", "XML")
    timestampedReports = false
    mutationThreshold = 100
    coverageThreshold = 100
    timeoutConstInMillis = 10_000
}
