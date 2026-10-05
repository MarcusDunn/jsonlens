plugins {
    id("jsonlens.library")
}

description = "RFC 6902 JSON Patch for jsonlens. It applies patches in place to the JSON values of any editable JsonModel."

dependencies {
    api(projects.model)
    api(projects.pointer)
    testImplementation(testFixtures(projects.model))
    // The tests run on real JSON trees of the adapters.
    testImplementation(projects.jackson)
    testImplementation(projects.kotlinxSerialization)
    testImplementation(projects.mapped)
}

sourceSets {
    test {
        resources.srcDir(layout.settingsDirectory.dir("spec/json-patch-tests"))
    }
}
