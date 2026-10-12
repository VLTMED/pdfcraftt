//! Android platform adapters that do not belong in the PDF parser or engine.
//!
//! Android's Storage Access Framework may return a `ParcelFileDescriptor` for a
//! document. After Java detaches and transfers that descriptor into Rust as an
//! `OwnedFd`, this adapter reads it once into the byte-backed representation
//! required by PdfCraft's current `Document::open` contract.

#![deny(clippy::unwrap_used, clippy::expect_used, clippy::panic, clippy::unimplemented, clippy::todo, clippy::unreachable)]

#[cfg(unix)]
mod unix {
    use std::fs::File;
    use std::io::{self, Read};
    use std::os::fd::OwnedFd;
    use std::sync::Arc;

    /// Read a transferred Android/Unix descriptor into the immutable source bytes used by PdfCraft.
    ///
    /// The `OwnedFd` value represents exclusive ownership. It is closed on success and on every
    /// error path when the local `File` is dropped. The caller should perform this blocking read on
    /// an I/O worker, not the Android UI thread. No new document-size limit is introduced here;
    /// the current engine already retains the complete source PDF in memory.
    pub fn read_owned_fd(fd: OwnedFd) -> io::Result<Arc<Vec<u8>>> {
        let mut file = File::from(fd);
        let mut bytes = Vec::new();
        file.read_to_end(&mut bytes)?;
        Ok(Arc::new(bytes))
    }
}

#[cfg(unix)]
pub use unix::read_owned_fd;

#[cfg(all(test, unix))]
#[allow(clippy::expect_used)]
mod tests {
    use std::fs::{self, File};
    use std::io::Write;
    use std::os::fd::OwnedFd;
    use std::sync::atomic::{AtomicU64, Ordering};

    use super::read_owned_fd;

    static NEXT_FILE: AtomicU64 = AtomicU64::new(1);

    #[test]
    fn reads_owned_descriptor_and_returns_shared_pdf_bytes() {
        let path = std::env::temp_dir().join(format!("pdfcraft-android-fd-{}-{}.pdf", std::process::id(), NEXT_FILE.fetch_add(1, Ordering::Relaxed)));
        let expected = b"%PDF-1.7\nAndroid SAF descriptor fixture\n";
        let mut source = File::create(&path).expect("create descriptor fixture");
        source.write_all(expected).expect("write descriptor fixture");
        drop(source);

        let descriptor: OwnedFd = File::open(&path).expect("open descriptor fixture").into();
        let bytes = read_owned_fd(descriptor).expect("read transferred descriptor");
        fs::remove_file(path).expect("remove descriptor fixture");

        assert_eq!(bytes.as_slice(), expected);
    }
}
