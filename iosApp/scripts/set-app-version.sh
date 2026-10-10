#!/bin/sh
set -eu

fail() {
    echo "ERROR: $1" >&2
    exit 1
}

# Local builds can supply a version without changing repository tags.
version=${CORE_APP_VERSION:-}
if [ -z "$version" ]; then
    version=$(git -C "${SRCROOT}/.." describe --tags --abbrev=0 2>/dev/null || true)
fi
if [ -z "$version" ]; then
    if [ "${CONFIGURATION:-Debug}" = Debug ]; then
        version=0.0.0
    else
        fail "Set CORE_APP_VERSION or provide a numeric version tag for a release build."
    fi
fi
case "$version" in
    *[!0-9.]*|.*|*.|*..*) fail "App version must be X.Y.Z or X.Y.Z.B with integer components." ;;
esac
previous_ifs=$IFS
IFS=.
set -- $version
IFS=$previous_ifs
case $# in
    3) build=0 ;;
    4) build=$4 ;;
    *) fail "App version must contain three or four integer components." ;;
esac
short_version=$1.$2.$3
build=${CORE_APP_BUILD_NUMBER:-$build}
case "$build" in
    ''|*[!0-9]*) fail "CORE_APP_BUILD_NUMBER must be a nonnegative integer." ;;
esac

plist=${TARGET_BUILD_DIR}/${INFOPLIST_PATH}
current_version=$(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" "$plist" 2>/dev/null || true)
current_build=$(/usr/libexec/PlistBuddy -c "Print :CFBundleVersion" "$plist" 2>/dev/null || true)
if [ "$current_version" != "$short_version" ]; then
    /usr/libexec/PlistBuddy -c "Delete :CFBundleShortVersionString" "$plist" 2>/dev/null || true
    /usr/libexec/PlistBuddy -c "Add :CFBundleShortVersionString string $short_version" "$plist"
fi
if [ "$current_build" != "$build" ]; then
    /usr/libexec/PlistBuddy -c "Delete :CFBundleVersion" "$plist" 2>/dev/null || true
    /usr/libexec/PlistBuddy -c "Add :CFBundleVersion string $build" "$plist"
fi
