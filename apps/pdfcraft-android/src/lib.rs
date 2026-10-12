mod session;

use jni::JNIEnv;
use jni::objects::{JObject, JString};
use jni::sys::{jboolean, jbyteArray, jfloat, jint, jlong, jstring};
use serde_json::{Value, json};
use session::{
    abort_transfer, begin_export_file, begin_save, call_tool, close_workspace, create_workspace, finish_save, finish_transfer, list_documents,
    open_document, read_transfer_chunk, render_page, reserve_input_path, tool_catalog, validate_input_path,
};

fn input_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Result<String, String> {
    env.get_string(value).map(Into::into).map_err(|error| format!("Invalid UTF-8 string input: {error}"))
}

fn json_result(env: &mut JNIEnv<'_>, value: Value) -> jstring {
    match env.new_string(value.to_string()) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn byte_result(env: &mut JNIEnv<'_>, bytes: &[u8]) -> jbyteArray {
    match env.byte_array_from_slice(bytes) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn render_error_packet(message: &str) -> Vec<u8> {
    let mut packet = b"ERR:".to_vec();
    packet.extend_from_slice(message.as_bytes());
    packet
}

fn panic_response() -> Value {
    json!({"status":"error","code":"native_panic","message":"The native PDF engine failed unexpectedly."})
}

// JNI resolves these symbols by name. Unsafe is permitted only for the exported symbols.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_createWorkspace(mut env: JNIEnv, _this: JObject, parent: JString) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| input_string(&mut env, &parent).map(create_workspace)));
    let response = match result {
        Ok(Ok(value)) => value,
        Ok(Err(message)) => json!({"status":"error","code":"invalid_input","message":message}),
        Err(_) => panic_response(),
    };
    json_result(&mut env, response)
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_closeWorkspace(mut env: JNIEnv, _this: JObject, workspace: jlong) -> jstring {
    let response = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| close_workspace(workspace as i64))).unwrap_or_else(|_| panic_response());
    json_result(&mut env, response)
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_reserveInputPath(
    mut env: JNIEnv,
    _this: JObject,
    workspace: jlong,
    name: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        input_string(&mut env, &name).map(|name| reserve_input_path(workspace as i64, name))
    }));
    let response = match result {
        Ok(Ok(value)) => value,
        Ok(Err(message)) => json!({"status":"error","code":"invalid_input","message":message}),
        Err(_) => panic_response(),
    };
    json_result(&mut env, response)
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_validateInputPath(
    mut env: JNIEnv,
    _this: JObject,
    workspace: jlong,
    path: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        input_string(&mut env, &path).map(|path| validate_input_path(workspace as i64, path))
    }));
    let response = match result {
        Ok(Ok(value)) => value,
        Ok(Err(message)) => json!({"status":"error","code":"invalid_input","message":message}),
        Err(_) => panic_response(),
    };
    json_result(&mut env, response)
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_openDocument(
    mut env: JNIEnv,
    _this: JObject,
    workspace: jlong,
    relative_path: JString,
    password: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let relative_path = input_string(&mut env, &relative_path)?;
        let password = input_string(&mut env, &password)?;
        Ok::<Value, String>(open_document(workspace as i64, relative_path, password))
    }));
    let response = match result {
        Ok(Ok(value)) => value,
        Ok(Err(message)) => json!({"status":"error","code":"invalid_input","message":message}),
        Err(_) => panic_response(),
    };
    json_result(&mut env, response)
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_listDocuments(mut env: JNIEnv, _this: JObject, workspace: jlong) -> jstring {
    let response = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| list_documents(workspace as i64))).unwrap_or_else(|_| panic_response());
    json_result(&mut env, response)
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_toolCatalog(mut env: JNIEnv, _this: JObject) -> jstring {
    let response = std::panic::catch_unwind(std::panic::AssertUnwindSafe(tool_catalog)).unwrap_or_else(|_| panic_response());
    json_result(&mut env, response)
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_callTool(
    mut env: JNIEnv,
    _this: JObject,
    workspace: jlong,
    active_document: jlong,
    name: JString,
    arguments: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let name = input_string(&mut env, &name)?;
        let arguments = input_string(&mut env, &arguments)?;
        Ok::<Value, String>(call_tool(workspace as i64, active_document as i64, name, arguments))
    }));
    let response = match result {
        Ok(Ok(value)) => value,
        Ok(Err(message)) => json!({"status":"error","code":"invalid_input","message":message}),
        Err(_) => panic_response(),
    };
    json_result(&mut env, response)
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_renderPage(
    mut env: JNIEnv,
    _this: JObject,
    workspace: jlong,
    document: jlong,
    page_index: jint,
    dpi: jfloat,
) -> jbyteArray {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| render_page(workspace as i64, document as i64, page_index, dpi)));
    let bytes = match result {
        Ok(Ok(bytes)) => bytes,
        Ok(Err(error)) => render_error_packet(&format!("{}: {}", error.code, error.message)),
        Err(_) => render_error_packet("native_panic: The native renderer failed unexpectedly."),
    };
    byte_result(&mut env, &bytes)
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_beginSave(
    mut env: JNIEnv,
    _this: JObject,
    workspace: jlong,
    document: jlong,
    full: jboolean,
) -> jstring {
    let response = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| begin_save(workspace as i64, document as i64, full != 0)))
        .unwrap_or_else(|_| panic_response());
    json_result(&mut env, response)
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_beginExportFile(
    mut env: JNIEnv,
    _this: JObject,
    workspace: jlong,
    path: JString,
) -> jstring {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        input_string(&mut env, &path).map(|path| begin_export_file(workspace as i64, path))
    }));
    let response = match result {
        Ok(Ok(value)) => value,
        Ok(Err(message)) => json!({"status":"error","code":"invalid_input","message":message}),
        Err(_) => panic_response(),
    };
    json_result(&mut env, response)
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_readTransferChunk(
    mut env: JNIEnv,
    _this: JObject,
    token: jlong,
    offset: jlong,
    requested: jint,
) -> jbyteArray {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| read_transfer_chunk(token as i64, offset as i64, requested)));
    let packet = result.unwrap_or_else(|_| {
        let mut out = Vec::from(u32::MAX.to_le_bytes());
        let message = b"native_panic: Could not read the PDF transfer buffer.";
        out.extend_from_slice(&(message.len() as u32).to_le_bytes());
        out.extend_from_slice(message);
        out
    });
    byte_result(&mut env, &packet)
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_finishSave(
    mut env: JNIEnv,
    _this: JObject,
    workspace: jlong,
    document: jlong,
    token: jlong,
) -> jstring {
    let response = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| finish_save(workspace as i64, document as i64, token as i64)))
        .unwrap_or_else(|_| panic_response());
    json_result(&mut env, response)
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_finishTransfer(_env: JNIEnv, _this: JObject, token: jlong) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| finish_transfer(token as i64)));
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativePdf_abortTransfer(_env: JNIEnv, _this: JObject, token: jlong) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| abort_transfer(token as i64)));
}
