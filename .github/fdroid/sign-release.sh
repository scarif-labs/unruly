#!/bin/bash
# Signs the unsigned APK from the "F-Droid reference build" workflow with the release key, in the
# one way F-Droid can verify: it copies only our signature block onto its own build, so the signed
# APK must be the unsigned one plus that block and nothing else.
#
#   --alignment-preserved true   apksigner re-aligns stored entries by default, which shifts every
#                                byte after the first one and breaks F-Droid's digest check.
#   --v1-signing-enabled false   JAR signing adds zip entries; minSdk 24 doesn't need it.
#
# The key is ~/.config/unruly/release.jks; its password is in the macOS Keychain
# ("unruly-release-keystore"). Neither ever goes to CI.
#
# Usage: sign-release.sh <unsigned.apk> <out.apk>
set -euo pipefail

IN=$1
OUT=$2
BT=$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/* | sort -V | tail -1)

KSPW=$(security find-generic-password -s unruly-release-keystore -w)
export KSPW
"$BT/apksigner" sign \
    --alignment-preserved true \
    --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
    --ks "$HOME/.config/unruly/release.jks" --ks-key-alias unruly \
    --ks-pass env:KSPW --key-pass env:KSPW \
    --out "$OUT" "$IN"
unset KSPW

"$BT/apksigner" verify --print-certs "$OUT" | grep "SHA-256 digest"
# Same check F-Droid runs, if apksigcopier is installed.
if command -v apksigcopier >/dev/null; then
    PATH="$BT:$PATH" apksigcopier compare "$OUT" --unsigned "$IN" && echo "apksigcopier: OK"
fi
