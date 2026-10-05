plugins {
    id("jsonlens.library")
}

description = "A read-only jsonlens JsonModel over UTF-8 JSON bytes, for example a memory-mapped file."

dependencies {
    api(projects.model)
    testImplementation(testFixtures(projects.model))
    testImplementation(projects.modelTestkit)
}
