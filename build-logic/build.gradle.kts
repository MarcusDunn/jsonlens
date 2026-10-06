plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.errorprone.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.pitest.gradle.plugin)
    implementation(libs.cyclonedx.gradle.plugin)
    implementation(libs.dokka.gradle.plugin)
}
