//! Full-engine bridge: every operation is one `Automation::call(tool, args)` over the engine's
//! tool catalogue (the table behind the CLI and MCP server), so engine features reach Android
//! without bridge changes.
//!
//! The engine lives on its own thread per session. Kotlin talks to it through a channel, so the
//! engine never has to be `Send`, calls from several JVM threads are serialised, and a panic is
//! caught and reported instead of unwinding into the JVM.

use std::collections::HashMap;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::mpsc::{Sender, channel};
use std::sync::{Mutex, OnceLock};

use base64::Engine as _;
use jni::JNIEnv;
use jni::objects::{JObject, JString};
use jni::sys::{jlong, jstring};
use pdfcraft_automation::{Automation, Content, tools};
use serde_json::{Value, json};

type Job = (String, Value, Sender<Value>);

fn sessions() -> &'static Mutex<HashMap<i64, Sender<Job>>> {
    static S: OnceLock<Mutex<HashMap<i64, Sender<Job>>>> = OnceLock::new();
    S.get_or_init(|| Mutex::new(HashMap::new()))
}

fn fail(msg: impl std::fmt::Display) -> Value {
    json!({ "ok": false, "error": msg.to_string() })
}

fn run(a: &mut Automation, tool: &str, args: &Value) -> Value {
    match catch_unwind(AssertUnwindSafe(|| a.call(tool, args))) {
        Err(_) => fail("internal error (panic) in the PDF engine"),
        Ok(Err(e)) => fail(e),
        Ok(Ok(parts)) => {
            let content: Vec<Value> = parts
                .into_iter()
                .map(|c| match c {
                    Content::Json(v) => json!({ "type": "json", "value": v }),
                    Content::Png { data, width, height } => json!({
                        "type": "png", "width": width, "height": height,
                        "data": base64::engine::general_purpose::STANDARD.encode(data),
                    }),
                })
                .collect();
            json!({ "ok": true, "content": content })
        }
    }
}

/// Starts an engine thread confined to `root` and returns its session handle.
pub fn create_session(root: &str) -> Result<i64, String> {
    static NEXT: AtomicI64 = AtomicI64::new(1);
    let (tx, rx) = channel::<Job>();
    let (ready_tx, ready_rx) = channel::<Result<(), String>>();
    let root = root.to_owned();
    std::thread::Builder::new()
        .name("pdfcraft-engine".into())
        .stack_size(16 << 20)
        .spawn(move || {
            let mut a = match Automation::new().with_root(&root) {
                Ok(a) => a,
                Err(e) => {
                    let _ = ready_tx.send(Err(format!("{root}: {e}")));
                    return;
                }
            };
            let _ = ready_tx.send(Ok(()));
            for (tool, args, reply) in rx {
                let _ = reply.send(run(&mut a, &tool, &args));
            }
        })
        .map_err(|e| e.to_string())?;
    ready_rx.recv().map_err(|e| e.to_string())??;
    let id = NEXT.fetch_add(1, Ordering::Relaxed);
    sessions().lock().map_err(|_| "session table poisoned".to_string())?.insert(id, tx);
    Ok(id)
}

pub fn destroy_session(handle: i64) {
    if let Ok(mut s) = sessions().lock() {
        s.remove(&handle);
    }
}

/// Runs one tool; always returns `{ok: true, content: [...]}` or `{ok: false, error}`.
pub fn call_tool(handle: i64, tool: &str, args_json: &str) -> Value {
    let tx = match sessions().lock() {
        Ok(s) => s.get(&handle).cloned(),
        Err(_) => None,
    };
    let Some(tx) = tx else { return fail("unknown or closed session") };
    let args: Value = match serde_json::from_str(args_json) {
        Ok(v) => v,
        Err(e) => return fail(format!("bad arguments JSON: {e}")),
    };
    let (rtx, rrx) = channel();
    if tx.send((tool.to_owned(), args, rtx)).is_err() {
        return fail("the engine thread has stopped");
    }
    rrx.recv().unwrap_or_else(|_| fail("the engine thread stopped while working"))
}

pub fn catalogue() -> Value {
    Value::Array(
        tools()
            .into_iter()
            .map(|t| {
                json!({ "name": t.name, "title": t.title, "description": t.description,
                        "inputSchema": t.input_schema, "readOnly": t.read_only, "destructive": t.destructive })
            })
            .collect(),
    )
}

fn out(env: &mut JNIEnv, v: Value) -> jstring {
    env.new_string(v.to_string()).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

fn read(env: &mut JNIEnv, s: &JString) -> String {
    env.get_string(s).map(String::from).unwrap_or_default()
}

// Kotlin: package ai.storyteller.pdfcraft.android, object NativeEngine.

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativeEngine_nativeCreate(mut env: JNIEnv, _this: JObject, root: JString) -> jlong {
    let root = read(&mut env, &root);
    catch_unwind(AssertUnwindSafe(|| create_session(&root).unwrap_or(0))).unwrap_or(0)
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativeEngine_nativeDestroy(_env: JNIEnv, _this: JObject, handle: jlong) {
    destroy_session(handle);
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativeEngine_nativeCall(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    tool: JString,
    args: JString,
) -> jstring {
    let (t, a) = (read(&mut env, &tool), read(&mut env, &args));
    let v = catch_unwind(AssertUnwindSafe(|| call_tool(handle, &t, &a))).unwrap_or_else(|_| fail("internal error"));
    out(&mut env, v)
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_android_NativeEngine_nativeTools(mut env: JNIEnv, _this: JObject) -> jstring {
    out(&mut env, catalogue())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn session() -> i64 {
        let dir = std::env::temp_dir().join(format!("pdfcraft-android-test-{}", std::process::id()));
        std::fs::create_dir_all(&dir).expect("temp dir");
        create_session(dir.to_str().expect("utf-8 path")).expect("session")
    }

    #[test]
    fn unknown_session_is_an_error_not_a_panic() {
        assert_eq!(call_tool(987_654, "doc_list", "{}")["ok"], false);
    }

    #[test]
    fn bad_json_and_unknown_tool_are_errors() {
        let h = session();
        assert_eq!(call_tool(h, "doc_list", "{not json")["ok"], false);
        assert_eq!(call_tool(h, "no_such_tool", "{}")["ok"], false);
        destroy_session(h);
    }

    #[test]
    fn doc_list_works_and_closed_session_fails() {
        let h = session();
        let r = call_tool(h, "doc_list", "{}");
        assert_eq!(r["ok"], true, "{r}");
        destroy_session(h);
        assert_eq!(call_tool(h, "doc_list", "{}")["ok"], false);
    }

    #[test]
    fn catalogue_lists_the_core_tools() {
        let c = catalogue();
        let names: Vec<&str> = c.as_array().expect("array").iter().filter_map(|t| t["name"].as_str()).collect();
        for n in ["doc_open", "page_render", "doc_save", "text_find"] {
            assert!(names.contains(&n), "{n} missing");
        }
        assert!(names.len() > 100, "only {} tools", names.len());
    }
}
