# Security

## Report a vulnerability

Do not open a public issue for a vulnerability. Use the private report of GitHub: the "Security"
tab of the repository, then "Report a vulnerability". You get an answer within 7 days.

## Supported versions

Only the latest release gets security fixes.

## Verify a release

Each release comes from the workflow `.github/workflows/release.yml`, from a tag `v<version>`. You
can check a release in four ways.

**The PGP signature.** Maven Central has a `.asc` signature for each file. The release key is
`Marcus Dunn <marcus@marcusdunn.ca>`, with the fingerprint
`6539 9874 EABF 904D C152  B8EF 4B50 1BA0 382F 9ADB`. Get it from the
Web Key Directory of `marcusdunn.ca`, and check that the fingerprint is the same:

```sh
gpg --auto-key-locate clear,wkd --locate-external-keys marcus@marcusdunn.ca
gpg --verify jsonlens-model-<version>.jar.asc jsonlens-model-<version>.jar
```

The key is also on `keyserver.ubuntu.com`, where Maven Central reads it.

In a Gradle build, add the fingerprint to the `trusted-keys` of your
`gradle/verification-metadata.xml`. Then Gradle checks the signature of each jsonlens artifact.

**The build provenance.** The release workflow attests each JAR, POM, and SBOM with a SLSA
provenance that Sigstore signs. The attestation names the commit and the workflow run that built the
file:

```sh
gh attestation verify jsonlens-model-<version>.jar --repo MarcusDunn/jsonlens
```

**A rebuild.** The build is reproducible: the same commit gives the same bytes. The Nix flake pins
the JDK and the tools.

```sh
git checkout v<version>
nix develop --command ./gradlew clean centralBundle
sha256sum build/staging-deploy/ca/marcusdunn/jsonlens/jsonlens-model/<version>/jsonlens-model-<version>.jar
```

Compare the checksum with the JAR on Maven Central. The signatures (`.asc`) differ, because each
signature has a time.

**The SBOM.** Each module has a CycloneDX SBOM with the classifier `cyclonedx`, for example
`jsonlens-jackson-<version>-cyclonedx.json`. It lists the module and its runtime dependencies. The
library modules have no runtime dependencies other than jsonlens modules; an adapter also requires
its JSON library.
