plugins {
    id("jsonlens.library")
}

description = "Tests for a jsonlens JsonModel: contract tests, a verifier of the model rules, and the RFC 9535 and RFC 6902 test suites."

dependencies {
    api(projects.model)
    // The contract tests are JUnit tests, and the suites are JUnit dynamic tests.
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter.api)
    // Reads the suite files, and runs the suites.
    implementation(projects.pathCore)
    implementation(projects.mapped)
    implementation(projects.pathParser)
    implementation(projects.pathEvaluator)
    implementation(projects.patch)
    testImplementation(testFixtures(projects.model))
    testImplementation(libs.junit.platform.launcher)
}

// The suite files go into the JAR, with their licenses.
val suites = tasks.register<Sync>("suiteResources") {
    from(layout.settingsDirectory.dir("spec/cts")) {
        include("cts.json", "LICENSE")
        into("ca/marcusdunn/jsonlens/testkit/cts")
    }
    from(layout.settingsDirectory.dir("spec/json-patch-tests")) {
        include("tests.json", "spec_tests.json", "README.md")
        into("ca/marcusdunn/jsonlens/testkit/json-patch-tests")
    }
    into(layout.buildDirectory.dir("generated/suite-resources"))
}

sourceSets {
    main {
        resources.srcDir(suites)
    }
}

pitest {
    // The JUnit classes assert; the tests in BrokenModelTest show that they find faults.
    excludedClasses = setOf(
        "ca.marcusdunn.jsonlens.testkit.JsonModelContract*",
        "ca.marcusdunn.jsonlens.testkit.JsonFactoryContract*",
        "ca.marcusdunn.jsonlens.testkit.JsonEditorContract*",
        "ca.marcusdunn.jsonlens.testkit.ComplianceKit*")
}

// The same classes have failure paths that only a broken suite can reach, for example a parse
// error of a valid query. Line coverage does not apply to them; BrokenModelTest and the adapter
// tests run them instead.
val assertionClasses = listOf(
    "ca/marcusdunn/jsonlens/testkit/JsonModelContract*",
    "ca/marcusdunn/jsonlens/testkit/JsonFactoryContract*",
    "ca/marcusdunn/jsonlens/testkit/JsonEditorContract*",
    "ca/marcusdunn/jsonlens/testkit/ComplianceKit*")
tasks.withType<JacocoReportBase>().configureEach {
    classDirectories.setFrom(sourceSets.main.get().output.classesDirs.asFileTree.matching { exclude(assertionClasses) })
}
