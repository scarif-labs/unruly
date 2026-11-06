#!/bin/bash
# Builds Unruly exactly as F-Droid's buildserver will, so the release APK we sign matches the one
# F-Droid builds (reproducible builds: F-Droid publishes our signature only on a byte-for-byte
# match).
#
# Runs as root inside registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie, and follows the
# "fdroid build" job in fdroiddata's .gitlab-ci.yml step for step. Keep it that way: any custom
# setup here is a difference from the real buildserver.
#
# Usage: reference-build.sh <recipe.yml> <commit> <out-dir>
# Leaves the unsigned APK (and, if the recipe has Binaries, F-Droid's verdict) in <out-dir>.
set -euxo pipefail

RECIPE=$1
COMMIT=$2
OUT=$3
APPID=$(basename "$RECIPE" .yml)

# fdroiddata supplies config.yml and the tooling layout `fdroid build` expects.
DATA=/work/fdroiddata
git clone --depth 1 https://gitlab.com/fdroid/fdroiddata.git "$DATA"
# Only our app, pinned to the commit being released.
sed -e "s/^    commit: .*/    commit: $COMMIT/" "$RECIPE" > "$DATA/metadata/$APPID.yml"
VERCODE=$(sed -n 's/^    versionCode: //p' "$DATA/metadata/$APPID.yml" | tail -1)
cd "$DATA"
# Keep whatever was built, including a failed verification's APK, for comparison.
trap 'mkdir -p "$OUT"; cp -R "$DATA/tmp/." "$OUT/" 2>/dev/null; chmod -R a+rX "$OUT" || true' EXIT

# --- from fdroiddata .gitlab-ci.yml, job "fdroid build" ---
export ANDROID_HOME=/opt/android-sdk
apt-get update
apt-get -y dist-upgrade
test -n "${fdroidserver:-}" || source /etc/profile.d/bsenv.sh
sdkmanager "platform-tools" "build-tools;31.0.0"

rm -rf "$fdroidserver"
git clone --shallow-since=2026-07-13 https://gitlab.com/fdroid/fdroidserver.git "$fdroidserver"
git -C "$fdroidserver" config transfer.fsckObjects true
git -C "$fdroidserver" checkout -B master a35fdfddd9c66823987a410566a6101186e39c84
git -C "$fdroidserver" pull origin master --ff-only
export PATH="$fdroidserver:$PATH"
export PYTHONPATH="$fdroidserver:$fdroidserver/examples"
export PYTHONUNBUFFERED=true
export serverwebroot=/tmp

git -C "$home_vagrant/gradlew-fdroid" pull

git -C /tmp/ clone https://gitlab.com/fdroid/fdroid-bootstrap-buildserver.git
BOOTSTRAP_BUILDSERVER=/tmp/fdroid-bootstrap-buildserver
git -C $BOOTSTRAP_BUILDSERVER config transfer.fsckObjects true
git -C $BOOTSTRAP_BUILDSERVER checkout -B master b33507043e6750065745dd2d8c35d3bb5c633c11
git -C $BOOTSTRAP_BUILDSERVER pull origin master --ff-only
cp $BOOTSTRAP_BUILDSERVER/roles/production_hardening/files/gitconfig "$DATA/../.gitconfig"

for d in logs tmp unsigned build "$home_vagrant/.android" "$home_vagrant/.gradle" "$home_vagrant/metadata"; do
    test -d "$d" || mkdir "$d"
    chown -R vagrant "$d"
done
ln -s "$DATA/tmp" "$home_vagrant/tmp"
ln -s "$DATA/srclibs" "$home_vagrant/srclibs"
sysctl fs.inotify.max_user_watches=524288 || true
export GRADLE_USER_HOME=$home_vagrant/.gradle
fdroid() {
    sudo --preserve-env --user vagrant \
        env PATH="$fdroidserver:$PATH" \
        env PYTHONPATH="$fdroidserver:$fdroidserver/examples" \
        env PYTHONUNBUFFERED=true \
        env TERM="${TERM:-dumb}" \
        env HOME="$home_vagrant" \
        fdroid "$@"
}

apt-get install -y sudo
apt-get install -y openjdk-21-jdk-headless
update-alternatives --set java /usr/lib/jvm/java-21-openjdk-amd64/bin/java
cp -R "$DATA/build" "$home_vagrant/build"
cp "$DATA/metadata/$APPID.yml" "$home_vagrant/metadata"
chown -R vagrant "$home_vagrant" "$DATA"
pushd "$home_vagrant"
ln -s "$DATA" "$home_vagrant/fdroiddata"
ln -s "$DATA/../.gitconfig" "$home_vagrant/.gitconfig"
fdroid fetchsrclibs "$APPID:$VERCODE" --verbose
rm "$home_vagrant/fdroiddata" "$home_vagrant/.gitconfig"
(unset CI; fdroid build --verbose --test --refresh-scanner --on-server --no-tarball "$APPID:$VERCODE")
popd
# --- end of fdroiddata steps ---

ls -laR "$DATA/tmp"
