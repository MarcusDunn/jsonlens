// Settings for all projects that publish a Maven artifact.
//
// - The POM has the metadata that Maven Central requires. The values are in gradle.properties.
// - Each publication is signed when the signing key is present: the Gradle properties
//   "signingKey" (an ASCII-armored private key) and "signingPassword", for example from the
//   environment variables ORG_GRADLE_PROJECT_signingKey and ORG_GRADLE_PROJECT_signingPassword.
//   A release must have them; a local build does not. For a key with only the signing subkey
//   (the recommended export for CI), also set "signingKeyId" to the short ID of that subkey:
//   the last 8 hex digits of its fingerprint. Gradle does not take the long ID.
// - "publishAllPublicationsToStagingRepository" writes the artifacts, their checksums, and their
//   signatures to build/staging-deploy of the root project. The task "centralBundle" of the root
//   project makes the bundle for the Central Portal from that directory.

plugins {
    `maven-publish`
    signing
}

fun property(name: String): Provider<String> = providers.gradleProperty(name)

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = "jsonlens-${project.name}"
        pom {
            name = "jsonlens-${project.name}"
            description = provider { project.description }
            url = property("pom.url")
            inceptionYear = "2026"
            licenses {
                license {
                    name = "MIT License"
                    url = "https://opensource.org/license/mit"
                    distribution = "repo"
                }
            }
            developers {
                developer {
                    id = property("pom.developer.id")
                    name = property("pom.developer.name")
                    url = property("pom.developer.url")
                }
            }
            scm {
                url = property("pom.url")
                connection = property("pom.scm.connection")
                developerConnection = property("pom.scm.developerConnection")
            }
        }
    }
    repositories {
        maven {
            name = "staging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

signing {
    val key = property("signingKey")
    val password = property("signingPassword")
    // The ID of the signing subkey, when the key has its own primary key only as a stub (an export
    // of the subkey only). Without it, Gradle signs with the first key, which is the primary key.
    val keyId = property("signingKeyId")
    isRequired = key.isPresent
    if (key.isPresent) {
        if (keyId.isPresent) {
            useInMemoryPgpKeys(keyId.get(), key.get(), password.getOrElse(""))
        } else {
            useInMemoryPgpKeys(key.get(), password.getOrElse(""))
        }
        sign(publishing.publications)
    }
}
