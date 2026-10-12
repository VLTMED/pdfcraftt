mod engine;

use std::sync::Arc;

use jni::JNIEnv;
use jni::objects::{JByteArray, JObject, JString};
use jni::sys::jstring;
use pdfcraft_cos::{CosError, Document};
use pdfcraft_model::pages;

/// Minimal native vertical slice: parse a PDF and report its version and page count.
/// This deliberately does not claim to render or edit documents.
pub fn inspect_pdf(bytes: Vec<u8>, password: &str) -> Result<String, String> {
    let document = match Document::open_with_password(Arc::new(bytes), Some(password)) {
        Ok(document) => document,
        Err(CosError::NeedsPassword) => return Err("NEEDS_PASSWORD".to_owned()),
        Err(CosError::WrongPassword) => return Err("WRONG_PASSWORD".to_owned()),
        Err(error) => return Err(error.to_string()),
    };

    Ok(format!("OK|{}|{}", document.version(), pages(&document).len()))
}

// JNI resolves this one symbol by name; the body uses safe jni-rs APIs only.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_inspectPdf(
    mut env: JNIEnv,
    _this: JObject,
    bytes: JByteArray,
    password: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let bytes = env.convert_byte_array(&bytes).map_err(|_| "INVALID_INPUT".to_owned())?;
        let password: String = env.get_string(&password).map_err(|_| "INVALID_PASSWORD_ENCODING".to_owned())?.into();
        inspect_pdf(bytes, &password)
    }));

    let message = match result {
        Ok(Ok(value)) => value,
        Ok(Err(error)) => format!("ERR|{error}"),
        Err(_) => "ERR|NATIVE_PANIC".to_owned(),
    };

    match env.new_string(message) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[cfg(test)]
mod tests {
    use super::inspect_pdf;
    use std::io::Write as _;

    fn one_page_pdf() -> Vec<u8> {
        let objects = [
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 /MediaBox [0 0 612 792] >>",
            "<< /Type /Page /Parent 2 0 R >>",
        ];
        let mut bytes = b"%PDF-1.7\n".to_vec();
        let mut offsets = Vec::new();
        for (index, object) in objects.iter().enumerate() {
            offsets.push(bytes.len());
            write!(&mut bytes, "{} 0 obj\n{}\nendobj\n", index + 1, object).expect("writing to a Vec cannot fail");
        }
        let xref = bytes.len();
        write!(&mut bytes, "xref\n0 4\n0000000000 65535 f \n").expect("writing to a Vec cannot fail");
        for offset in offsets {
            writeln!(&mut bytes, "{offset:010} 00000 n ").expect("writing to a Vec cannot fail");
        }
        write!(&mut bytes, "trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n").expect("writing to a Vec cannot fail");
        bytes
    }

    #[test]
    fn inspects_pdf_version_and_page_count() {
        assert_eq!(inspect_pdf(one_page_pdf(), "").unwrap(), "OK|1.7|1");
    }

    #[test]
    fn rejects_non_pdf_input() {
        assert!(inspect_pdf(b"not a pdf".to_vec(), "").is_err());
    }
}
