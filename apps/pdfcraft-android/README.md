# PdfCraft Android port — work in progress

This branch is a **partial native Android port**, not a production-ready or feature-parity release. It keeps PdfCraft's Rust parsing/editing/automation code as the engine and adds a Kotlin/Jetpack Compose shell with a persistent Rust Automation workspace through JNI.

## Implemented in this increment

- Android SAF input staging, password-aware document open, multiple open documents, page raster/navigation, basic text search, undo/redo, and incremental/full-save transfer through `CreateDocument`.
- JNI access to the source Automation catalog: **153 JSON-schema tools** rendered through native Compose forms.
- A Compose view of the source's **190 registered commands**. The source currently links **62** commands to headless tools; **128** have no direct `command_run` mapping and remain explicit implementation gaps. A tool form or visible command row is not counted as proof of feature parity.
- Rust host bridge tests: **3 passed** as of 2026-10-12.

## Known gaps — do not ship as complete

- The page renderer still produces a full-page bitmap at a fixed scale; tile rendering, high-resolution zoom/cache, multi-page layouts, and many accessibility interactions are not yet integrated.
- The mobile command palette currently routes only a small set of native actions. Specialized mobile workflows for annotations, forms, signing, redaction, OCR, page organization, printing, recents, and other commands still need implementation and round-trip tests.
- The 128 unlinked source commands are tracked in [`docs/android-command-coverage-ar.md`](../../docs/android-command-coverage-ar.md). Some may map to existing tools, while others require Android-specific behavior or new tested Rust APIs; no equivalence is assumed.
- SAF save/export is chunked and avoids loading full outputs into Kotlin memory, but provider atomicity, crash recovery, complete save/reopen parity, lifecycle/process-death behavior, security integration, and Android instrumentation are not proven.
- Strict Clippy currently reports an existing upstream `manual_range_contains` lint at `crates/engine/src/camera_edges.rs:235`; the Android APK for this expanded branch still needs a fresh CI build.

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

The APK is written to `apps/pdfcraft-android/app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions uploads a debug-signed APK as a 14-day artifact after a successful build; it is not store-ready. The earlier artifact predates the Automation/session expansion and is not a valid APK for this current increment.
