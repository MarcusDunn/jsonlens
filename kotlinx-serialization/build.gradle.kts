plugins {
    id("jsonlens.kotlin-library")
}

description = "A jsonlens JsonModel for kotlinx.serialization JsonElement values."

dependencies {
    api(projects.model)
    api(libs.kotlinx.serialization.json)
    testImplementation(testFixtures(projects.model))
    testImplementation(projects.modelTestkit)
}
