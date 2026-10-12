use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, MutexGuard, OnceLock};

use pdfcraft_automation::{Automation, Content, ToolDef, ToolError, tools as automation_tools};
use pdfcraft_engine::DocId;
use serde_json::{Value, json};

const MAX_TRANSFER_CHUNK: usize = 1024 * 1024;

struct AndroidWorkspace {
    automation: Automation,
    root: PathBuf,
    next_file: u64,
}

type SharedWorkspace = Arc<Mutex<AndroidWorkspace>>;

#[derive(Clone)]
enum TransferPurpose {
    Save { doc_id: u64, full: bool },
    Export,
    Artifact,
}

#[derive(Clone)]
struct PendingTransfer {
    workspace_id: u64,
    bytes: Arc<Vec<u8>>,
    purpose: TransferPurpose,
}

static WORKSPACES: OnceLock<Mutex<HashMap<u64, SharedWorkspace>>> = OnceLock::new();
static TRANSFERS: OnceLock<Mutex<HashMap<u64, PendingTransfer>>> = OnceLock::new();
static TOOL_DEFS: OnceLock<Vec<ToolDef>> = OnceLock::new();
static NEXT_WORKSPACE_ID: AtomicU64 = AtomicU64::new(1);
static NEXT_TRANSFER_ID: AtomicU64 = AtomicU64::new(1);

fn workspaces() -> &'static Mutex<HashMap<u64, SharedWorkspace>> {
    WORKSPACES.get_or_init(|| Mutex::new(HashMap::new()))
}

fn transfers() -> &'static Mutex<HashMap<u64, PendingTransfer>> {
    TRANSFERS.get_or_init(|| Mutex::new(HashMap::new()))
}

fn definitions() -> &'static [ToolDef] {
    TOOL_DEFS.get_or_init(automation_tools).as_slice()
}

fn lock<T>(mutex: &Mutex<T>) -> MutexGuard<'_, T> {
    mutex.lock().unwrap_or_else(std::sync::PoisonError::into_inner)
}

#[derive(Debug)]
pub(crate) struct BridgeError {
    pub(crate) code: &'static str,
    pub(crate) message: String,
}

impl BridgeError {
    fn new(code: &'static str, message: impl Into<String>) -> Self {
        Self { code, message: message.into() }
    }

    fn value(self) -> Value {
        json!({"status":"error","code":self.code,"message":self.message})
    }
}

fn tool_error(error: ToolError) -> BridgeError {
    match error {
        ToolError::UnknownTool(name) => BridgeError::new("unknown_tool", format!("Unknown PdfCraft tool: {name}")),
        ToolError::InvalidArgs(message) => BridgeError::new("invalid_arguments", message),
        ToolError::Failed(message) => {
            let lower = message.to_ascii_lowercase();
            if lower.contains("password") && lower.contains("wrong") {
                BridgeError::new("wrong_password", message)
            } else if lower.contains("password") && (lower.contains("required") || lower.contains("need")) {
                BridgeError::new("needs_password", message)
            } else {
                BridgeError::new("operation_failed", message)
            }
        }
    }
}

fn workspace_ref(handle: i64) -> Result<(u64, SharedWorkspace), BridgeError> {
    let id = u64::try_from(handle).map_err(|_| BridgeError::new("invalid_workspace", "Invalid Android workspace id."))?;
    if id == 0 {
        return Err(BridgeError::new("invalid_workspace", "Invalid Android workspace id."));
    }
    let shared = lock(workspaces()).get(&id).cloned().ok_or_else(|| BridgeError::new("workspace_closed", "The PDF workspace is no longer open."))?;
    Ok((id, shared))
}

fn safe_file_name(name: &str) -> String {
    let basename = Path::new(name).file_name().and_then(|part| part.to_str()).unwrap_or("document.pdf");
    let cleaned: String =
        basename.chars().map(|c| if c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_') { c } else { '_' }).take(120).collect();
    if cleaned.is_empty() || cleaned == "." || cleaned == ".." { "document.pdf".to_string() } else { cleaned }
}

fn next_id(counter: &AtomicU64, label: &'static str) -> Result<u64, BridgeError> {
    let id = counter.fetch_add(1, Ordering::Relaxed);
    if id == 0 || id > i64::MAX as u64 { Err(BridgeError::new("id_limit", format!("No more {label} ids can be allocated."))) } else { Ok(id) }
}

fn content_json(contents: Vec<Content>) -> Result<Value, BridgeError> {
    contents
        .into_iter()
        .find_map(|content| match content {
            Content::Json(value) => Some(value),
            Content::Png { .. } => None,
        })
        .ok_or_else(|| BridgeError::new("empty_tool_result", "The PdfCraft tool returned no structured result."))
}

fn call_json(workspace: &mut AndroidWorkspace, name: &str, args: &Value) -> Result<Value, BridgeError> {
    let contents = workspace.automation.call(name, args).map_err(tool_error)?;
    content_json(contents)
}

fn insert_transfer(workspace_id: u64, bytes: Arc<Vec<u8>>, purpose: TransferPurpose) -> Result<u64, BridgeError> {
    let token = next_id(&NEXT_TRANSFER_ID, "file transfer")?;
    lock(transfers()).insert(token, PendingTransfer { workspace_id, bytes, purpose });
    Ok(token)
}

fn tool_catalog_value() -> Value {
    let tools: Vec<Value> = definitions()
        .iter()
        .map(|tool| {
            let category = tool.name.split('_').next().unwrap_or(tool.name);
            json!({
                "name": tool.name,
                "title": tool.title,
                "category": category,
                "description": tool.description,
                "readOnly": tool.read_only,
                "destructive": tool.destructive,
                "command": tool.command,
                "inputSchema": tool.input_schema.clone(),
            })
        })
        .collect();
    json!({"status":"ok","count":tools.len(),"tools":tools})
}

pub fn tool_catalog() -> Value {
    tool_catalog_value()
}

pub fn create_workspace(parent: String) -> Value {
    let result = (|| -> Result<Value, BridgeError> {
        let id = next_id(&NEXT_WORKSPACE_ID, "workspace")?;
        let root = PathBuf::from(parent).join(format!("workspace-{id}"));
        fs::create_dir_all(&root)
            .map_err(|error| BridgeError::new("workspace_io", format!("Could not create the private PDF workspace: {error}")))?;
        let automation = Automation::new()
            .with_root(&root)
            .map_err(|error| BridgeError::new("workspace_io", format!("Could not initialize the private PDF workspace: {error}")))?;
        let root =
            root.canonicalize().map_err(|error| BridgeError::new("workspace_io", format!("Could not resolve the private PDF workspace: {error}")))?;
        let root_string = root.to_string_lossy().into_owned();
        let shared = Arc::new(Mutex::new(AndroidWorkspace { automation, root, next_file: 1 }));
        lock(workspaces()).insert(id, shared);
        Ok(json!({"status":"ok","workspaceId":id,"workspaceRoot":root_string,"toolCount":definitions().len()}))
    })();
    result.unwrap_or_else(BridgeError::value)
}

pub fn close_workspace(handle: i64) -> Value {
    let result = (|| -> Result<Value, BridgeError> {
        let (id, shared) = workspace_ref(handle)?;
        let mut workspace = lock(&shared);
        let listing = call_json(&mut workspace, "doc_list", &json!({}))?;
        let dirty = listing
            .get("documents")
            .and_then(Value::as_array)
            .is_some_and(|documents| documents.iter().any(|document| document.get("dirty").and_then(Value::as_bool) == Some(true)));
        if dirty {
            return Err(BridgeError::new("unsaved_changes", "Save or explicitly discard every unsaved document before closing this workspace."));
        }
        let root = workspace.root.clone();
        drop(workspace);
        lock(workspaces()).remove(&id);
        lock(transfers()).retain(|_, transfer| transfer.workspace_id != id);
        fs::remove_dir_all(&root)
            .map_err(|error| BridgeError::new("workspace_io", format!("The workspace closed, but temporary files could not be removed: {error}")))?;
        Ok(json!({"status":"ok","closed":id}))
    })();
    result.unwrap_or_else(BridgeError::value)
}

pub fn reserve_input_path(handle: i64, name: String) -> Value {
    let result = (|| -> Result<Value, BridgeError> {
        let (workspace_id, shared) = workspace_ref(handle)?;
        let mut workspace = lock(&shared);
        let file_id = workspace.next_file;
        workspace.next_file = workspace.next_file.saturating_add(1);
        if workspace.next_file == file_id {
            return Err(BridgeError::new("file_limit", "No more staged files can be created."));
        }
        let safe_name = safe_file_name(&name);
        let folder = format!("inputs/{file_id}");
        fs::create_dir_all(workspace.root.join(&folder))
            .map_err(|error| BridgeError::new("workspace_io", format!("Could not prepare the private input directory: {error}")))?;
        Ok(json!({"status":"ok","workspaceId":workspace_id,"path":format!("{folder}/{safe_name}"),"name":safe_name}))
    })();
    result.unwrap_or_else(BridgeError::value)
}

pub fn validate_input_path(handle: i64, relative_path: String) -> Value {
    let result = (|| -> Result<Value, BridgeError> {
        let (workspace_id, shared) = workspace_ref(handle)?;
        let workspace = lock(&shared);
        let resolved = workspace.automation.resolve_input_path(&relative_path).map_err(tool_error)?;
        let metadata =
            fs::metadata(&resolved).map_err(|error| BridgeError::new("input_not_found", format!("The selected input is unavailable: {error}")))?;
        if !metadata.is_file() {
            return Err(BridgeError::new("invalid_input", "The staged input must be a file."));
        }
        Ok(json!({"status":"ok","workspaceId":workspace_id,"path":relative_path,"size":metadata.len()}))
    })();
    result.unwrap_or_else(BridgeError::value)
}

pub fn open_document(handle: i64, relative_path: String, password: String) -> Value {
    let result = (|| -> Result<Value, BridgeError> {
        let (workspace_id, shared) = workspace_ref(handle)?;
        let mut workspace = lock(&shared);
        let resolved = workspace.automation.resolve_input_path(&relative_path).map_err(tool_error)?;
        if !resolved.is_file() {
            return Err(BridgeError::new("input_not_found", "The selected PDF is not present in the private workspace."));
        }
        let mut args = json!({"path":relative_path});
        if !password.is_empty() {
            args["password"] = Value::String(password);
        }
        let document = call_json(&mut workspace, "doc_open", &args)?;
        Ok(json!({"status":"ok","workspaceId":workspace_id,"document":document}))
    })();
    result.unwrap_or_else(BridgeError::value)
}

pub fn list_documents(handle: i64) -> Value {
    let result = (|| -> Result<Value, BridgeError> {
        let (_, shared) = workspace_ref(handle)?;
        let mut workspace = lock(&shared);
        let listing = call_json(&mut workspace, "doc_list", &json!({}))?;
        Ok(json!({"status":"ok","documents":listing.get("documents").cloned().unwrap_or_else(|| json!([]))}))
    })();
    result.unwrap_or_else(BridgeError::value)
}

pub fn render_page(handle: i64, document_id: i64, page_index: i32, dpi: f32) -> Result<Vec<u8>, BridgeError> {
    if page_index < 0 {
        return Err(BridgeError::new("invalid_page", "Page index must not be negative."));
    }
    if !(dpi.is_finite() && (1.0..=600.0).contains(&dpi)) {
        return Err(BridgeError::new("invalid_dpi", "Render resolution must be between 1 and 600 DPI."));
    }
    let doc = u64::try_from(document_id).map_err(|_| BridgeError::new("invalid_document", "Invalid document id."))?;
    if doc == 0 {
        return Err(BridgeError::new("invalid_document", "Invalid document id."));
    }
    let (_, shared) = workspace_ref(handle)?;
    let mut workspace = lock(&shared);
    let contents = workspace.automation.call("page_render", &json!({"doc":doc,"page":i64::from(page_index) + 1,"dpi":dpi})).map_err(tool_error)?;
    contents
        .into_iter()
        .find_map(|content| match content {
            Content::Png { data, .. } => Some(data),
            Content::Json(_) => None,
        })
        .ok_or_else(|| BridgeError::new("render_failed", "The renderer did not return a PNG image."))
}

pub fn call_tool(handle: i64, active_document_id: i64, name: String, arguments: String) -> Value {
    let result = (|| -> Result<Value, BridgeError> {
        let mut args: Value = serde_json::from_str(&arguments)
            .map_err(|error| BridgeError::new("invalid_json", format!("Tool arguments are not valid JSON: {error}")))?;
        if !args.is_object() {
            return Err(BridgeError::new("invalid_arguments", "Tool arguments must be a JSON object."));
        }
        let definition = definitions()
            .iter()
            .find(|tool| tool.name == name)
            .ok_or_else(|| BridgeError::new("unknown_tool", format!("Unknown PdfCraft tool: {name}")))?;
        let has_doc_argument =
            definition.input_schema.get("properties").and_then(Value::as_object).is_some_and(|properties| properties.contains_key("doc"));
        if has_doc_argument && args.get("doc").is_none() && active_document_id > 0 {
            args["doc"] = json!(active_document_id);
        }
        let (workspace_id, shared) = workspace_ref(handle)?;
        let mut workspace = lock(&shared);
        let contents = workspace.automation.call(&name, &args).map_err(tool_error)?;
        let mut results = Vec::with_capacity(contents.len());
        for content in contents {
            match content {
                Content::Json(value) => results.push(json!({"kind":"json","value":value})),
                Content::Png { data, width, height } => {
                    let token = insert_transfer(workspace_id, Arc::new(data), TransferPurpose::Artifact)?;
                    results.push(json!({"kind":"png","width":width,"height":height,"transferToken":token}));
                }
            }
        }
        Ok(json!({"status":"ok","tool":name,"readOnly":definition.read_only,"results":results}))
    })();
    result.unwrap_or_else(BridgeError::value)
}

pub fn begin_save(handle: i64, document_id: i64, full: bool) -> Value {
    let result = (|| -> Result<Value, BridgeError> {
        let doc = u64::try_from(document_id).map_err(|_| BridgeError::new("invalid_document", "Invalid document id."))?;
        if doc == 0 {
            return Err(BridgeError::new("invalid_document", "Invalid document id."));
        }
        let (workspace_id, shared) = workspace_ref(handle)?;
        let workspace = lock(&shared);
        let engine_id = DocId(doc);
        let bytes =
            if full { workspace.automation.session().save_full_bytes(engine_id) } else { workspace.automation.session().save_bytes(engine_id) }
                .map_err(|error| BridgeError::new("save_failed", format!("Could not prepare the PDF save: {error}")))?;
        let size = bytes.len();
        let token = insert_transfer(workspace_id, bytes, TransferPurpose::Save { doc_id: doc, full })?;
        Ok(json!({"status":"ok","transferToken":token,"size":size,"full":full}))
    })();
    result.unwrap_or_else(BridgeError::value)
}

pub fn begin_export_file(handle: i64, path: String) -> Value {
    let result = (|| -> Result<Value, BridgeError> {
        let (workspace_id, shared) = workspace_ref(handle)?;
        let workspace = lock(&shared);
        let bytes = workspace.automation.read_output(&path).map_err(tool_error)?;
        let size = bytes.len();
        let file_name = Path::new(&path).file_name().and_then(|part| part.to_str()).unwrap_or("PdfCraft-output.bin").to_string();
        let token = insert_transfer(workspace_id, Arc::new(bytes), TransferPurpose::Export)?;
        Ok(json!({"status":"ok","transferToken":token,"size":size,"fileName":file_name}))
    })();
    result.unwrap_or_else(BridgeError::value)
}

pub fn read_transfer_chunk(token: i64, offset: i64, requested: i32) -> Vec<u8> {
    let result = (|| -> Result<Vec<u8>, BridgeError> {
        let id = u64::try_from(token).map_err(|_| BridgeError::new("invalid_transfer", "Invalid file transfer token."))?;
        let start = usize::try_from(offset).map_err(|_| BridgeError::new("invalid_transfer", "Invalid file transfer offset."))?;
        let length = usize::try_from(requested).map_err(|_| BridgeError::new("invalid_transfer", "Invalid file transfer chunk size."))?;
        if id == 0 || length == 0 || length > MAX_TRANSFER_CHUNK {
            return Err(BridgeError::new("invalid_transfer", "File transfer chunks must be between 1 byte and 1 MiB."));
        }
        let transfers = lock(transfers());
        let pending = transfers.get(&id).ok_or_else(|| BridgeError::new("transfer_expired", "The file transfer has expired."))?;
        if start > pending.bytes.len() {
            return Err(BridgeError::new("invalid_transfer", "File transfer offset is outside the output file."));
        }
        let end = start.saturating_add(length).min(pending.bytes.len());
        let data = &pending.bytes[start..end];
        let data_len = u32::try_from(data.len()).map_err(|_| BridgeError::new("invalid_transfer", "File transfer chunk is too large."))?;
        let mut packet = Vec::with_capacity(4 + data.len());
        packet.extend_from_slice(&data_len.to_le_bytes());
        packet.extend_from_slice(data);
        Ok(packet)
    })();
    match result {
        Ok(packet) => packet,
        Err(error) => {
            let message = error.message.as_bytes();
            let length = u32::try_from(message.len()).unwrap_or(u32::MAX - 1);
            let mut packet = Vec::with_capacity(8 + message.len());
            packet.extend_from_slice(&u32::MAX.to_le_bytes());
            packet.extend_from_slice(&length.to_le_bytes());
            packet.extend_from_slice(message);
            packet
        }
    }
}

pub fn finish_save(handle: i64, document_id: i64, token: i64) -> Value {
    let result = (|| -> Result<Value, BridgeError> {
        let workspace_id = u64::try_from(handle).map_err(|_| BridgeError::new("invalid_workspace", "Invalid Android workspace id."))?;
        let doc = u64::try_from(document_id).map_err(|_| BridgeError::new("invalid_document", "Invalid document id."))?;
        let transfer_id = u64::try_from(token).map_err(|_| BridgeError::new("invalid_transfer", "Invalid file transfer token."))?;
        if workspace_id == 0 || doc == 0 || transfer_id == 0 {
            return Err(BridgeError::new("invalid_transfer", "Invalid save identifiers."));
        }
        let pending =
            lock(transfers()).get(&transfer_id).cloned().ok_or_else(|| BridgeError::new("transfer_expired", "The PDF save transfer has expired."))?;
        if pending.workspace_id != workspace_id {
            return Err(BridgeError::new("invalid_transfer", "The save transfer belongs to another workspace."));
        }
        let full = match pending.purpose {
            TransferPurpose::Save { doc_id, full } if doc_id == doc => full,
            TransferPurpose::Save { .. } => return Err(BridgeError::new("invalid_transfer", "The save transfer belongs to another document.")),
            TransferPurpose::Export | TransferPurpose::Artifact => {
                return Err(BridgeError::new("invalid_transfer", "This transfer is not a document save."));
            }
        };
        let (_, shared) = workspace_ref(handle)?;
        let mut workspace = lock(&shared);
        let relative = format!("exports/document-{doc}.pdf");
        let saved_path = workspace.automation.write_output(&relative, pending.bytes.as_ref()).map_err(tool_error)?;
        workspace.automation.session_mut().mark_saved(DocId(doc), pending.bytes.clone(), Some(saved_path.to_string_lossy().into_owned())).map_err(
            |error| BridgeError::new("save_failed", format!("The PDF was exported, but PdfCraft could not finalize the saved state: {error}")),
        )?;
        let listing = call_json(&mut workspace, "doc_list", &json!({}))?;
        let document = listing
            .get("documents")
            .and_then(Value::as_array)
            .and_then(|docs| docs.iter().find(|item| item.get("doc").and_then(Value::as_u64) == Some(doc)))
            .cloned()
            .unwrap_or(Value::Null);
        lock(transfers()).remove(&transfer_id);
        Ok(json!({"status":"ok","document":document,"full":full}))
    })();
    result.unwrap_or_else(BridgeError::value)
}

pub fn finish_transfer(token: i64) {
    if let Ok(id) = u64::try_from(token) {
        if id > 0 {
            lock(transfers()).remove(&id);
        }
    }
}

pub fn abort_transfer(token: i64) {
    finish_transfer(token);
}

#[cfg(test)]
mod tests {
    use super::{definitions, safe_file_name};
    use serde_json::{Value, json};
    use std::collections::HashSet;

    #[test]
    fn android_catalog_exposes_every_unique_automation_tool_schema() {
        let defs = definitions();
        assert_eq!(defs.len(), 153);
        let names: HashSet<&str> = defs.iter().map(|tool| tool.name).collect();
        assert_eq!(names.len(), defs.len());
        for name in ["doc_open", "doc_info", "text_find", "edit_undo", "edit_redo", "page_render", "command_list", "command_run"] {
            assert!(names.contains(name), "missing tool {name}");
        }
        assert!(defs.iter().all(|tool| tool.input_schema.get("type").and_then(Value::as_str) == Some("object")));
    }

    #[test]
    fn android_coverage_audit_uses_the_complete_engine_command_registry() {
        use pdfcraft_automation::{Automation, Content};

        let output = Automation::new().call("command_list", &json!({})).expect("command_list should be available");
        let catalog = output
            .into_iter()
            .find_map(|item| match item {
                Content::Json(value) => Some(value),
                Content::Png { .. } => None,
            })
            .expect("command_list should return JSON");
        let commands = catalog.get("commands").and_then(Value::as_array).expect("commands array");
        assert_eq!(commands.len(), 190);
        assert_eq!(commands.len(), pdfcraft_engine::commands::COMMANDS.len());
        let linked = commands.iter().filter(|command| command.get("tool").and_then(Value::as_str).is_some()).count();
        let unlinked = commands.len() - linked;
        assert_eq!(linked, 62, "update the Android parity inventory when command-tool links change");
        assert_eq!(unlinked, 128, "do not claim parity while registered commands have no implementation route");
    }

    #[test]
    fn staged_file_names_cannot_escape_workspace() {
        assert_eq!(safe_file_name("../../a strange name.pdf"), "a_strange_name.pdf");
        assert_eq!(safe_file_name("/"), "document.pdf");
    }
}
