plugins {
    `java-platform`
    id("jsonlens.publishing")
}

description = "Bill of materials for the jsonlens modules."

dependencies {
    constraints {
        api(projects.model)
        api(projects.pathCore)
        api(projects.pathParser)
        api(projects.pathEvaluator)
        api(projects.jackson)
        api(projects.kotlinxSerialization)
        api(projects.mapped)
        api(projects.pointer)
        api(projects.patch)
        api(projects.mergePatch)
        api(projects.modelTestkit)
    }
}

publishing {
    publications {
        create<MavenPublication>("bom") {
            from(components["javaPlatform"])
        }
    }
}
