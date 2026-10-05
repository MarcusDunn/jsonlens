plugins {
    id("jsonlens.kotlin-library")
    // Generates serializers for the @Serializable classes of the tests.
    id("jsonlens.kotlin-serialization")
}

description = "A jsonlens JsonModel for kotlinx.serialization JsonElement values."

dependencies {
    api(projects.model)
    api(libs.kotlinx.serialization.json)
    testImplementation(testFixtures(projects.model))
    testImplementation(projects.modelTestkit)
    // The end-to-end tests query and patch Kotlin objects.
    testImplementation(projects.pathParser)
    testImplementation(projects.pathEvaluator)
    testImplementation(projects.patch)
}
