plugins {
    id("jsonlens.library")
}

description = "RFC 9535 JSONPath evaluator for jsonlens. It applies a parsed query to a JSON value through a JsonModel."

dependencies {
    api(projects.pathCore)
    testImplementation(testFixtures(projects.model))
    // The tests parse queries with the parser, and run on real JSON trees of both adapters.
    testImplementation(projects.pathParser)
    testImplementation(projects.jackson)
    testImplementation(projects.kotlinxSerialization)
    testImplementation(projects.mapped)
}

sourceSets {
    test {
        resources.srcDir(layout.settingsDirectory.dir("spec/cts"))
    }
}
