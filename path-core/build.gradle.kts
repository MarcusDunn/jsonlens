plugins {
    id("jsonlens.library")
}

description = "The JSONPath types of jsonlens: the query syntax tree, function signatures, and Normalized Paths."

dependencies {
    api(projects.model)
}
