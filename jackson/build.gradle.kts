plugins {
    id("jsonlens.library")
}

description = "A jsonlens JsonModel for Jackson 3 JsonNode values."

dependencies {
    api(projects.model)
    api(libs.jackson.databind)
    testImplementation(testFixtures(projects.model))
    testImplementation(projects.modelTestkit)
}
