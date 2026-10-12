# pdfcraft-android

Scaffold for an Android build of PdfCraft. **Unbuilt and unverified** — see the module docs in
`src/lib.rs` for exactly what's done, why `android-game-activity` was chosen over
`android-native-activity`, and the three pieces still needed before this installs on a device
(Storage Access Framework file I/O, a `PrintManager` JNI bridge, and the Gradle/Manifest
packaging). No existing crate was changed to add this — it only adds a new workspace member
(picked up automatically by the `apps/*` glob in the root `Cargo.toml`), the same way
`pdfcraft-web` sits beside `pdfcraft` today.

Not in `default-members`, so it is never built by a plain `cargo build` or `cargo check`; it only
builds when asked for explicitly (`-p pdfcraft-android`, or `cargo ndk ... -p pdfcraft-android`).
