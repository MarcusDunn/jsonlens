# Releasing jsonlens

## Build

The Nix flake supplies JDK 25 and Gradle 9. With direnv, run `direnv allow` one time. Without
direnv, run `nix develop`.

```sh
./gradlew build                                # compile, test, and check traceability and coverage
./gradlew :path-parser:test                    # test one module
./gradlew :docs:javadocAll                     # API documentation of the Java modules, in build/docs/javadoc
./gradlew clean centralBundle -Pversion=1.2.3  # the release bundle, in build/central-bundle.zip
```

The build uses:

- a version catalog (`gradle/libs.versions.toml`) and convention plugins (`build-logic`);
- the configuration cache and the build cache;
- Error Prone and NullAway, which treat warnings as errors;
- JDK 25 for the build, and Java 21 bytecode for the published artifacts;
- Gradle dependency verification (`gradle/verification-metadata.xml`): the build fails when a
  dependency or plugin does not have the recorded checksum or a trusted signature.

After a change of a dependency, write the new verification entries with an empty Gradle cache, as on
a CI machine, and review the diff before you commit it. With a full cache, Gradle can skip files
that a clean machine needs:

```sh
GRADLE_USER_HOME="$(mktemp -d)" ./gradlew --write-verification-metadata pgp,sha256 --export-keys \
    check :docs:javadocAll centralBundle -Pversion=0.0.0 --no-configuration-cache
```

## Release

The rulesets of the repository allow changes to `main` only through a pull request with a green
`Check`, and let only an administrator push a tag `v*`. A pushed `v*` tag cannot move.

1. In a pull request, set `version` in `gradle.properties` to the release version, without
   `-SNAPSHOT`. Merge it when CI is green.
2. Tag the merged commit, and push the tag:
   ```sh
   git tag -a v<version> -m "jsonlens <version>" && git push origin v<version>
   ```
3. Approve the job "Build, sign, and attest" of the workflow `release.yml` in the Actions tab. This
   is the only approval. Then the workflow runs to the end by itself:
   - "Build, sign, and attest" checks that the tag is the version and checks the release key. It
     builds, checks, signs, and attests the release.
   - "Rebuild and compare" builds the tag again on another runner, without secrets. The release
     stops unless each JAR, POM, and module file has the same bytes as in the bundle.
   - "Upload to Maven Central" uploads the bundle with automatic publication, and waits until the
     Central Portal reports `PUBLISHED`. Maven Central never deletes or replaces a published
     version. The files are on `repo1.maven.org` some minutes after that.
4. In a pull request, set `version` to the next `-SNAPSHOT` version.

Two environments hold the secrets. Only `v*` tags can deploy to them.

| Environment | Name | Kind | Value |
|---|---|---|---|
| `release` (required reviewers) | `SIGNING_KEY` | secret | the signing subkey only, ASCII-armored, protected with `SIGNING_PASSWORD` |
| `release` | `SIGNING_PASSWORD` | secret | a random password that nobody needs to know |
| `release` | `SIGNING_KEY_ID` | variable | `D6F54291`, the short ID of the signing subkey (Gradle does not take the long ID) |
| `central` (no reviewers) | `CENTRAL_TOKEN` | secret | base64 of `<user>:<password>` of a Central Portal user token |

`central` needs no reviewers: its job runs only after the approved build and the successful
compare, and it runs no project code.

## The release key

The release key is `Marcus Dunn <marcus@marcusdunn.ca>`:

| Key | Fingerprint | Use |
|---|---|---|
| primary | `65399874EABF904DC152B8EF4B501BA0382F9ADB` | certifies; kept offline |
| subkey | `447FC3E909C0AA710723D209CCD24806D6F54291` | signs the releases; in the CI secret |

It is published in two places:

- the Web Key Directory of `marcusdunn.ca`, from the repository `MarcusDunn/site`, at
  `static/.well-known/openpgpkey/hu/rt5udt498cuzmffxhbxas8eqhbr7wnae`;
- `keyserver.ubuntu.com`, where Maven Central and the build tools of users read it.

### Extend the release key

Both keys expire. Each week, the workflow `key-expiry.yml` reads the key from the Web Key Directory,
and opens an issue 90 days before the signing key expires. A release also stops when the signing key
expires within 30 days.

To extend the key, use the offline primary key:

```sh
gpg --quick-set-expire 65399874EABF904DC152B8EF4B501BA0382F9ADB 3y
gpg --quick-set-expire 65399874EABF904DC152B8EF4B501BA0382F9ADB 3y 447FC3E909C0AA710723D209CCD24806D6F54291
```

Name the subkey by its fingerprint: `'*'` does not select a subkey that has already expired.

Then publish the new expiry in both places:

```sh
gpg --export --export-options export-minimal \
    --export-filter keep-uid="mbox = marcus@marcusdunn.ca" 65399874EABF904DC152B8EF4B501BA0382F9ADB \
    > rt5udt498cuzmffxhbxas8eqhbr7wnae    # commit to MarcusDunn/site, static/.well-known/openpgpkey/hu/
gpg --keyserver keyserver.ubuntu.com --send-keys 65399874EABF904DC152B8EF4B501BA0382F9ADB
```

The fingerprints do not change, so the trusted keys of users and the CI secret stay the same. Gradle
does not read the expiry in the CI secret, and users read it from the published key. Run the
workflow `key-expiry.yml` by hand to check the result, and close the issue.
