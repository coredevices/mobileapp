#!/bin/bash
# Vendored libcactus_engine.a ships LLVM bitcode, so ARC codegen is deferred to link time and uses
# the bitcode's own module triple (arm64-apple-ios16.4.0) rather than our deployment target. The
# framework then imports iOS 16-only libobjc entrypoints and dyld kills the app at launch on iOS 15.
# Run this after dropping in a new cactus release, then commit the rewritten archives.
set -euo pipefail

DEPLOYMENT_TARGET=15.6
LIB_DIR="$(cd "$(dirname "$0")/../src/commonMain/resources/ios/lib" && pwd)"

for slice in ios-arm64 ios-arm64-simulator; do
    archive="$LIB_DIR/$slice/libcactus_engine.a"
    [ -f "$archive" ] || { echo "missing $archive" >&2; exit 1; }

    case "$slice" in
        ios-arm64) sdk=iphoneos; target="arm64-apple-ios$DEPLOYMENT_TARGET" ;;
        *)         sdk=iphonesimulator; target="arm64-apple-ios$DEPLOYMENT_TARGET-simulator" ;;
    esac

    work="$(mktemp -d)"
    (cd "$work" && ar x "$archive" && rm -f __.SYMDEF*)

    converted=0
    for member in "$work"/*.o; do
        file -b "$member" | grep -q 'LLVM bitcode' || continue
        xcrun -sdk "$sdk" clang -target "$target" -O2 -x ir -c \
            -Wno-override-module "$member" -o "$member.native"
        mv "$member.native" "$member"
        converted=$((converted + 1))
    done

    if [ "$converted" -gt 0 ]; then
        xcrun libtool -static -o "$archive" "$work"/*.o
        echo "$slice: recompiled $converted bitcode members for $target"
    else
        echo "$slice: already native, nothing to do"
    fi
    rm -rf "$work"

    bad="$(nm -u "$archive" | sort -u | grep -E '_objc_(release|retain)_x[0-9]+|_objc_claimAutoreleasedReturnValue' || true)"
    [ -z "$bad" ] || { echo "$slice: still imports newer-OS entrypoints:" >&2; echo "$bad" >&2; exit 1; }
done
