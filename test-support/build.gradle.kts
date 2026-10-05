plugins {
    id("jsonlens.java-conventions")
}

description = "Support for the tests of this build. It is not published."

dependencies {
    // Read at runtime to check @NullMarked on the exported packages.
    implementation(libs.jspecify)
}
