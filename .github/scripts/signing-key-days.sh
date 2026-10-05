#!/usr/bin/env bash
# Prints the number of days until a signing key of a PGP key expires, or "never".
#
# Usage: signing-key-days.sh <user-id> [<signing-subkey-fingerprint>]
#
# The key must be in the keyring of GNUPGHOME. With a fingerprint, the script checks only that
# signing key; without one, it takes the valid signing key that expires last. The result is the
# earlier of that expiry and the expiry of the primary key. The script fails (exit 2) when the
# primary key or the signing key is revoked, expired, or missing.
set -euo pipefail

user_id="$1"
want="${2:-}"

gpg --batch --with-colons --list-keys "$user_id" | awk -F: -v want="$want" -v now="$(date +%s)" '
    function candidate(fingerprint, expires) {
        if (want != "" && fingerprint != want) return
        if (expires == "") best = "never"
        else if (best != "never" && (best == "" || expires > best)) best = expires
    }
    # Validity: r = revoked, e = expired, i = invalid. Capabilities: s = can sign.
    $1 == "pub" { record = "pub"; primary_valid = ($2 !~ /[rei]/); primary_expires = $7; capabilities = $12; next }
    $1 == "sub" { record = "sub"; valid = ($2 !~ /[rei]/); expires = $7; capabilities = $12; next }
    $1 == "fpr" {
        if (record == "pub" && primary_valid && capabilities ~ /s/) candidate($10, primary_expires)
        if (record == "sub" && valid && capabilities ~ /s/) candidate($10, expires)
        record = ""
        next
    }
    END {
        if (!primary_valid) { print "The primary key is revoked, expired, or missing." > "/dev/stderr"; exit 2 }
        if (best == "") { print "There is no valid signing key." > "/dev/stderr"; exit 2 }
        end = best
        if (primary_expires != "" && (end == "never" || primary_expires < end)) end = primary_expires
        if (end == "never") print "never"
        else print int((end - now) / 86400)
    }
'
