plugins {
    id("jsonlens.library")
}

description = "RFC 6901 JSON Pointer for jsonlens. It resolves pointers against the JSON values of any JsonModel."

dependencies {
    api(projects.model)
    testImplementation(testFixtures(projects.model))
    // The tests run on real JSON trees of the adapters.
    testImplementation(projects.jackson)
    testImplementation(projects.kotlinxSerialization)
    testImplementation(projects.mapped)
}
