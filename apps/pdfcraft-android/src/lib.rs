use std::sync::Arc;

use jni::JNIEnv;
use jni::objects::{JByteArray, JObject, JString};
use jni::sys::{jbyteArray, jfloat, jint, jstring};
use pdfcraft_cos::{CosError, Document};
use pdfcraft_model::pages;
use pdfcraft_render::{PageRenderer, RenderConfig, RenderRequest};

/// Parse a PDF with the COS/model layer and return its version and page count.
pub fn inspect_pdf(bytes: Vec<u8>, password: &str) -> Result<String, String> {
    let document = match Document::open_with_password(Arc::new(bytes), Some(password)) {
        Ok(document) => document,
        Err(CosError::NeedsPassword) => return Err("NEEDS_PASSWORD".to_owned()),
        Err(CosError::WrongPassword) => return Err("WRONG_PASSWORD".to_owned()),
        Err(error) => return Err(error.to_string()),
    };

    Ok(format!("OK|{}|{}", document.version(), pages(&document).len()))
}

/// Render one page with PdfCraft's existing renderer.
/// The packet is width:u32 LE, height:u32 LE, then premultiplied BGRA8 bytes for Android Bitmap.
pub fn render_pdf_page(bytes: Vec<u8>, password: &str, page: usize, scale: f32) -> Result<Vec<u8>, String> {
    if !(scale.is_finite() && scale > 0.0) {
        return Err("Invalid render scale".to_owned());
    }
    let config =
        RenderConfig { password: (!password.is_empty()).then(|| Arc::<str>::from(password)), reject_oversize: true, ..RenderConfig::default() };
    let mut renderer = PageRenderer::new(Arc::new(bytes), config);
    if page >= renderer.page_count() {
        return Err(format!("Page {} does not exist", page + 1));
    }

    let rendered = renderer.render(RenderRequest { page, scale, ..RenderRequest::default() });
    if let Some(error) = rendered.error {
        return Err(error);
    }
    let rgba = rendered.rgba.as_ref();
    let byte_count = (rendered.width as usize)
        .checked_mul(rendered.height as usize)
        .and_then(|pixels| pixels.checked_mul(4))
        .ok_or_else(|| "Rendered page dimensions overflow".to_owned())?;
    if rgba.len() != byte_count {
        return Err("Renderer returned an incomplete pixel buffer".to_owned());
    }

    let mut packet = Vec::with_capacity(8 + byte_count);
    packet.extend_from_slice(&rendered.width.to_le_bytes());
    packet.extend_from_slice(&rendered.height.to_le_bytes());
    for pixel in rgba.chunks_exact(4) {
        // Android ARGB_8888 uses BGRA byte order in memory on supported little-endian Android ABIs.
        packet.extend_from_slice(&[pixel[2], pixel[1], pixel[0], pixel[3]]);
    }
    Ok(packet)
}

// JNI resolves these symbols by name; their bodies use safe jni-rs APIs only.
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

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_renderPage(
    mut env: JNIEnv,
    _this: JObject,
    bytes: JByteArray,
    password: JString,
    page: jint,
    scale: jfloat,
) -> jbyteArray {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        if page < 0 {
            return Err("Page index must not be negative".to_owned());
        }
        let bytes = env.convert_byte_array(&bytes).map_err(|_| "INVALID_INPUT".to_owned())?;
        let password: String = env.get_string(&password).map_err(|_| "INVALID_PASSWORD_ENCODING".to_owned())?.into();
        render_pdf_page(bytes, &password, page as usize, scale)
    }));

    let packet = match result {
        Ok(Ok(packet)) => packet,
        Ok(Err(error)) => error_packet(&error),
        Err(_) => error_packet("Native renderer failed unexpectedly"),
    };
    match env.byte_array_from_slice(&packet) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn error_packet(message: &str) -> Vec<u8> {
    let mut packet = Vec::with_capacity(8 + message.len());
    packet.extend_from_slice(&0u32.to_le_bytes());
    packet.extend_from_slice(&0u32.to_le_bytes());
    packet.extend_from_slice(message.as_bytes());
    packet
}

#[cfg(test)]
mod tests {
    use super::{inspect_pdf, render_pdf_page};
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
    fn renders_a_pdf_page_to_a_dimensioned_pixel_packet() {
        let packet = render_pdf_page(one_page_pdf(), "", 0, 0.5).unwrap();
        let width = u32::from_le_bytes(packet[0..4].try_into().unwrap());
        let height = u32::from_le_bytes(packet[4..8].try_into().unwrap());
        assert!(width > 0 && height > 0);
        assert_eq!(packet.len(), 8 + width as usize * height as usize * 4);
    }

    #[test]
    fn rejects_non_pdf_input() {
        assert!(inspect_pdf(b"not a pdf".to_vec(), "").is_err());
    }
}
