# PdfCraft Android port — work in progress

This branch is a **partial native Android port**, not a production-ready or feature-parity release. It keeps PdfCraft's Rust parsing/editing/automation code as the engine and adds a Kotlin/Jetpack Compose shell with a persistent Rust Automation workspace through JNI.

## Implemented in this increment

- Password-aware PDF open now hands the SAF `ParcelFileDescriptor` to JNI and reads its owned descriptor into the byte representation required by the Rust engine, without an intermediate staged PDF. The bridge allocates only a root-confined private default-save target so `doc_save` without an explicit path remains usable; tool inputs remain privately staged. Also includes multiple open documents, page raster/navigation, basic text search, undo/redo, and incremental/full-save transfer through `CreateDocument`.
- JNI access to the source Automation catalog: **153 JSON-schema tools** rendered through native Compose forms.
- A Compose view of the source's **190 registered commands**. The source currently links **62** commands to headless tools; **128** have no direct `command_run` mapping and remain explicit implementation gaps. A tool form or visible command row is not counted as proof of feature parity.
- Rust host suite for the bridge, SAF adapter, and Automation: **158 tests passed**; targeted Clippy with `--no-deps` passed. A strict whole-workspace Clippy run still hits the pre-existing `manual_range_contains` lint in `crates/engine/src/camera_edges.rs:235`.

## Known gaps — do not ship as complete

- The page renderer still produces a full-page bitmap at a fixed scale; tile rendering, high-resolution zoom/cache, multi-page layouts, and many accessibility interactions are not yet integrated.
- The mobile command palette currently routes only a small set of native actions. Specialized mobile workflows for annotations, forms, signing, redaction, OCR, page organization, printing, recents, and other commands still need implementation and round-trip tests.
- The 128 unlinked source commands are tracked in [`docs/android-command-coverage-ar.md`](../../docs/android-command-coverage-ar.md). Some may map to existing tools, while others require Android-specific behavior or new tested Rust APIs; no equivalence is assumed.
- SAF save/export is chunked and avoids loading full outputs into Kotlin memory, but provider atomicity, crash recovery, complete save/reopen parity, lifecycle/process-death behavior, security integration, and Android instrumentation are not proven.
- CI built the current debug APK for commit `a8aac5f` ([workflow run](https://github.com/VLTMED/pdfcraftt/actions/runs/38209774871)); archive integrity and `arm64-v8a`/`x86_64` native libraries were verified. It has not been instrumented or run on a physical device and is not store-ready.

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

The APK is written to `apps/pdfcraft-android/app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions uploads a debug-signed APK as a 14-day artifact after a successful build; it is not store-ready. Use the linked workflow run above for the current increment; older artifacts predate the SAF descriptor/default-save-target changes.
