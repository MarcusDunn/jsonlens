#!/usr/bin/env bash
# Compares the release bundle of the build job with an independent rebuild of the same tag.
#
# Usage: compare-bundles.sh <central-bundle.zip> <staging directory of the rebuild>
#
# Each JAR, POM, and Gradle module file must have the same bytes in both, and both must have the
# same files. Signatures (.asc) and checksums differ or follow from these files, and an SBOM has a
# random serial number, so the script does not compare them. It fails (exit 1) on any difference.
set -euo pipefail

bundle="$1"
rebuild="$2"

work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT
unzip -q "${bundle}" -d "${work}/bundle"

# The SHA-256 of each JAR, POM, and module file, by path, sorted.
sums() {
    (cd "$1" && find . -type f \( -name '*.jar' -o -name '*.pom' -o -name '*.module' \) -exec sha256sum {} + | sort -k2)
}

sums "${work}/bundle" > "${work}/bundle.txt"
sums "${rebuild}" > "${work}/rebuild.txt"

count="$(wc -l < "${work}/bundle.txt")"
if [ "${count}" -eq 0 ]; then
    echo "The bundle has no JAR, POM, or module file." >&2
    exit 1
fi
if ! diff "${work}/bundle.txt" "${work}/rebuild.txt"; then
    echo "The rebuild differs from the bundle (lines with < are the bundle, > the rebuild)." >&2
    exit 1
fi
echo "The rebuild matches the bundle: ${count} JAR, POM, and module files have the same bytes."
