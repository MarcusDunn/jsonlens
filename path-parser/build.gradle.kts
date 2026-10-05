plugins {
    id("jsonlens.library")
}

description = "RFC 9535 JSONPath parser for jsonlens. It makes and validates a query from text."

dependencies {
    api(projects.pathCore)
}


dependencies {
    // The compliance test suite is JSON text. Read it with the Jackson adapter module.
    testImplementation(projects.jackson)
}

sourceSets {
    test {
        resources.srcDir(layout.settingsDirectory.dir("spec/cts"))
    }
}
