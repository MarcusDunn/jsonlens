plugins {
    id("jsonlens.library")
    `java-test-fixtures`
}

description = "The JSON model interface of jsonlens: read, build, and edit the JSON values of any JSON library."

// The test fixtures are only for the tests of this build. Do not publish them.
val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations.testFixturesApiElements.get()) { skip() }
javaComponent.withVariantsFromConfiguration(configurations.testFixturesRuntimeElements.get()) { skip() }

dependencies {
    // The JsonModel contract test kit in the test fixtures uses JUnit.
    testFixturesImplementation(platform(libs.junit.bom))
    testFixturesImplementation(libs.junit.jupiter.api)
}
