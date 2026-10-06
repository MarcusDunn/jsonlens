plugins {
    id("jsonlens.library")
}

description = "RFC 7396 JSON Merge Patch for jsonlens. It merges a patch into the JSON values of any JsonModel, in place or to a copy."

dependencies {
    api(projects.model)
    testImplementation(testFixtures(projects.model))
    // The tests run on real JSON trees of the adapters.
    testImplementation(projects.jackson)
    testImplementation(projects.kotlinxSerialization)
    testImplementation(projects.mapped)
}
