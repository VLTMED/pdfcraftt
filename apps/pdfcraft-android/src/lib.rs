//! PdfCraft on Android.
//!
//! ## Status: scaffold, unverified
//!
//! This crate wires the entry point and reuses `pdfcraft-ui-egui` (the exact same shell the
//! desktop and web apps use — no feature is reimplemented or trimmed for Android). It has
//! **not** been built: this environment has no Android NDK and no network access to fetch one,
//! so nothing below has gone through `cargo ndk` / a device. Build and fix on real tooling
//! before trusting it.
//!
//! ## Why `android-game-activity`, not `android-native-activity`
//!
//! `pdfcraft-ui-egui`'s `eframe` dependency already enables the `accesskit` feature (screen
//! reader support — every other platform has it at "partial", per `docs/hardware-parity.md`).
//! eframe's own `lib.rs` has a `compile_error!` that only allows `accesskit` together with
//! `android-game-activity`; `android-native-activity` is incompatible with it upstream. Dropping
//! AccessKit on Android to use the simpler backend would itself be the kind of sacrifice this
//! scaffold is explicitly trying to avoid, so this crate takes the backend that keeps it.
//!
//! ## What's still open (each is its own follow-up, not done here)
//!
//! - **File open/save.** `pdfcraft-ui-egui` picks files through `rfd`, which has no Android
//!   backend. Needs a Storage Access Framework bridge: an `Intent` (`ACTION_OPEN_DOCUMENT` /
//!   `ACTION_CREATE_DOCUMENT`) via JNI, returning a `content://` `Uri` whose file descriptor
//!   (`ContentResolver.openFileDescriptor`) becomes a plain `std::fs::File` via
//!   `FromRawFd` — at which point the PDF engine needs no Android-specific code at all, since it
//!   already reads/writes through `Read`/`Write`, not paths.
//! - **System printing.** `craft-print` has no Android spooler yet (only Windows/macOS/Linux).
//!   Needs a JNI bridge to `android.print.PrintManager`, the same shape as the existing Windows
//!   spooler (#756) — a new backend, not a new architecture.
//! - **Packaging.** `AndroidManifest.xml` and the Gradle wrapper that pulls in the GameActivity
//!   AAR are not included here; `cargo ndk` alone does not produce an installable APK.
//! - **`aws-lc-rs` on `aarch64-linux-android`** (used by `pdfcraft-sign` for RSA signing)
//!   is expected to cross-compile via the NDK toolchain, but that has not been exercised here.
//!
//! Build, once the above exists: `cargo ndk -t arm64-v8a -o <gradle-project>/jniLibs build
//! --release -p pdfcraft-android`.

#[cfg(target_os = "android")]
mod android_entry {
    use android_activity::AndroidApp;
    use eframe::NativeOptions;
    use pdfcraft_ui_egui::PdfCraftApp;
    use winit::platform::android::EventLoopBuilderExtAndroid;

    #[unsafe(no_mangle)]
    fn android_main(app: AndroidApp) {
        android_logger::init_once(android_logger::Config::default().with_max_level(log::LevelFilter::Info));

        let options = NativeOptions {
            event_loop_builder: Some(Box::new(move |builder| {
                builder.with_android_app(app);
            })),
            ..Default::default()
        };

        // Same constructor pdfcraft-web calls (apps/pdfcraft-web/src/main.rs): no Android-only
        // branch, no trimmed-down App type.
        if let Err(err) = eframe::run_native("PdfCraft", options, Box::new(|_cc| Ok(Box::new(PdfCraftApp::new())))) {
            log::error!("PdfCraft: eframe::run_native failed on Android: {err}");
        }
    }
}

// Keeps `cargo xtask ci`'s `cargo clippy --workspace --all-targets` (xtask/src/gates.rs) green
// on host targets, where this crate builds as an empty, unused cdylib: the real entry point
// above only exists under `cfg(target_os = "android")`, matching how apps/pdfcraft-web's
// `main.rs` stubs itself out on `cfg(not(target_arch = "wasm32"))`.
#[cfg(not(target_os = "android"))]
mod android_entry {}
