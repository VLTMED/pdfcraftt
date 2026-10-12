#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
NDK_ROOT="${ANDROID_NDK_HOME:-${ANDROID_HOME:-}/ndk/27.2.12479018}"
TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"

if [[ ! -x "$TOOLCHAIN/aarch64-linux-android24-clang" ]]; then
  echo "Android NDK not found at $NDK_ROOT (expected linux-x86_64 toolchain)." >&2
  exit 2
fi

export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$TOOLCHAIN/aarch64-linux-android24-clang"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$TOOLCHAIN/x86_64-linux-android24-clang"
export CC_aarch64_linux_android="$TOOLCHAIN/aarch64-linux-android24-clang"
export CC_x86_64_linux_android="$TOOLCHAIN/x86_64-linux-android24-clang"
export AR_aarch64_linux_android="$TOOLCHAIN/llvm-ar"
export AR_x86_64_linux_android="$TOOLCHAIN/llvm-ar"

cd "$ROOT"
for target in aarch64-linux-android x86_64-linux-android; do
  cargo build --locked --release --package pdfcraft-android-native --target "$target"
done

APP="$ROOT/apps/pdfcraft-android/app/src/main/jniLibs"
mkdir -p "$APP/arm64-v8a" "$APP/x86_64"
install -m 0644 "$ROOT/target/aarch64-linux-android/release/libpdfcraft_android_native.so" \
  "$APP/arm64-v8a/libpdfcraft_android_native.so"
install -m 0644 "$ROOT/target/x86_64-linux-android/release/libpdfcraft_android_native.so" \
  "$APP/x86_64/libpdfcraft_android_native.so"
