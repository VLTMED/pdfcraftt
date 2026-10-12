# PdfCraft Android integration prototype

This is an **integration prototype**, not a complete PdfCraft Android port. It opens a document via Android's system document picker, reports PDF version/page count through the COS/model crates, and uses the existing `pdfcraft-render::PageRenderer` through JNI to rasterize a page for a Compose `Bitmap` viewer with previous/next navigation. Page rendering currently reparses the source for each request at a fixed scale; there is no persistent document session, tile-based zoom, editing, saving, or full desktop feature set.

The prototype intentionally labels this limitation in the UI and in the [Android feasibility assessment](../../docs/android-port-assessment-ar.md). Do not distribute it as a feature-complete or parity release.

## Build prerequisites

- JDK 21
- Android SDK platform 35 and build tools 35.0.0
- Android NDK 27.2.12479018
- Rust stable with `aarch64-linux-android` and `x86_64-linux-android` targets
- Gradle 8.11.1

## Build

From the repository root, set `ANDROID_HOME` to the SDK directory and either set `ANDROID_NDK_HOME` explicitly or install the NDK at `$ANDROID_HOME/ndk/27.2.12479018`:

```sh
rustup target add aarch64-linux-android x86_64-linux-android
bash apps/pdfcraft-android/scripts/build-native.sh
gradle --no-daemon -p apps/pdfcraft-android :app:assembleDebug
```

The APK is written to `apps/pdfcraft-android/app/build/outputs/apk/debug/app-debug.apk`. The GitHub Actions workflow builds the same APK and publishes it as a 14-day Actions artifact; it is a debug-signed artifact, not a store-ready release.
