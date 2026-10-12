package ai.storyteller.pdfcraft.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

private data class PdfDocument(
    val id: Long,
    val name: String,
    val pageCount: Int,
    val dirty: Boolean,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val editable: Boolean,
)

private data class PdfTool(
    val name: String,
    val title: String,
    val category: String,
    val description: String,
    val readOnly: Boolean,
    val destructive: Boolean,
    val schema: JSONObject,
)

private data class PdfCommand(
    val id: String,
    val label: String,
    val menu: String,
    val shortcut: String?,
    val enabled: Boolean,
    val toolName: String?,
    val schema: JSONObject,
)

private data class SearchMatch(val page: Int, val text: String)
private data class SearchContinuation(val startPage: Int, val skip: Int)
private data class StagedToolInput(val toolName: String, val fieldName: String, val paths: List<String>)
private data class InputRequest(val toolName: String? = null, val fieldName: String? = null, val multiple: Boolean = false)
private data class TransferRequest(
    val token: Long,
    val workspaceId: Long,
    val documentId: Long? = null,
    val size: Long = -1,
    val fileName: String,
    val isSave: Boolean,
)

private enum class AppSection(val label: String) {
    VIEWER("العرض"), COMMANDS("الأوامر"), TOOLS("الأدوات"), SEARCH("بحث"), HISTORY("السجل")
}

private data class PdfUiState(
    val workspaceId: Long = 0,
    val workspaceRoot: String = "",
    val documents: List<PdfDocument> = emptyList(),
    val activeDocumentId: Long? = null,
    val pageIndex: Int = 0,
    val bitmap: Bitmap? = null,
    val tools: List<PdfTool> = emptyList(),
    val commands: List<PdfCommand> = emptyList(),
    val section: AppSection = AppSection.VIEWER,
    val busy: Boolean = false,
    val status: String = "",
    val error: Boolean = false,
    val passwordRequired: Boolean = false,
    val passwordWrong: Boolean = false,
    val pendingOpenUri: String? = null,
    val searchMatches: List<SearchMatch> = emptyList(),
    val searchComplete: Boolean = true,
    val searchContinuation: SearchContinuation? = null,
    val stagedToolInput: StagedToolInput? = null,
    val toolResult: JSONObject? = null,
    val fullSave: Boolean = false,
)

class MainActivity : ComponentActivity() {
    private val ui = mutableStateOf(PdfUiState())
    private var pendingInput: InputRequest? = null
    private var pendingTransfer: TransferRequest? = null
    private lateinit var singleInputPicker: ActivityResultLauncher<Array<String>>
    private lateinit var multipleInputPicker: ActivityResultLauncher<Array<String>>
    private lateinit var pdfOutputPicker: ActivityResultLauncher<String>
    private lateinit var genericOutputPicker: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val workspaceResponse = runCatching {
            JSONObject(NativePdf.createWorkspace(File(filesDir, "pdfcraft").absolutePath))
        }.getOrElse {
            JSONObject().put("status", "error").put("message", it.message ?: "تعذر تهيئة محرك PDF.")
        }
        if (workspaceResponse.optString("status") == "ok") {
            ui.value = ui.value.copy(
                workspaceId = workspaceResponse.optLong("workspaceId"),
                workspaceRoot = workspaceResponse.optString("workspaceRoot"),
                status = "تم تحميل مساحة العمل. جارٍ تحميل سجل الأدوات…",
            )
        } else {
            ui.value = ui.value.copy(status = workspaceResponse.optString("message", "تعذر إنشاء مساحة عمل."), error = true)
        }

        singleInputPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val request = pendingInput
            pendingInput = null
            if (uri != null && request != null) handleSelectedInputs(listOf(uri), request)
        }
        multipleInputPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val request = pendingInput
            pendingInput = null
            if (uris.isNotEmpty() && request != null) handleSelectedInputs(uris, request)
        }
        pdfOutputPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            handleOutputUri(uri)
        }
        genericOutputPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            handleOutputUri(uri)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PdfCraftApp(
                        state = ui.value,
                        onOpenPdf = ::pickPdf,
                        onSubmitPassword = ::submitPassword,
                        onSelectDocument = ::selectDocument,
                        onPageChange = ::changePage,
                        onSectionChange = { section -> ui.value = ui.value.copy(section = section) },
                        onToolRun = ::runTool,
                        onCommandRun = ::runCommand,
                        onSearch = ::searchText,
                        onContinueSearch = ::continueSearch,
                        onUndo = { runTool("edit_undo", JSONObject()) },
                        onRedo = { runTool("edit_redo", JSONObject()) },
                        onSave = ::requestSave,
                        onPickToolInput = ::pickToolInput,
                        onExportPath = ::requestPathExport,
                        onExportArtifact = ::requestArtifactExport,
                        onToggleFullSave = { ui.value = ui.value.copy(fullSave = it) },
                    )
                }
            }
        }

        lifecycleScope.launch {
            try {
                val workspaceId = ui.value.workspaceId
                val loadedTools = withContext(Dispatchers.IO) { parseTools(NativePdf.toolCatalog()) }
                ui.value = ui.value.copy(tools = loadedTools, status = "جاهز — ${loadedTools.size} أداة في السجل الأصلي.", error = false)
                if (workspaceId > 0) refreshDocuments(render = false)
            } catch (error: Exception) {
                ui.value = ui.value.copy(status = error.message ?: "تعذر تحميل سجل أدوات PdfCraft.", error = true)
            } catch (error: LinkageError) {
                ui.value = ui.value.copy(status = "تعذر تحميل مكتبة Rust الأصلية: ${error.message ?: "native library error"}", error = true)
            }
        }
    }

    private fun pickPdf() {
        pendingInput = InputRequest()
        singleInputPicker.launch(arrayOf("application/pdf"))
    }

    private fun pickToolInput(toolName: String, fieldName: String, multiple: Boolean) {
        pendingInput = InputRequest(toolName = toolName, fieldName = fieldName, multiple = multiple)
        if (multiple) multipleInputPicker.launch(arrayOf("*/*")) else singleInputPicker.launch(arrayOf("*/*"))
    }

    private fun handleSelectedInputs(uris: List<Uri>, request: InputRequest) {
        val workspaceId = ui.value.workspaceId
        if (workspaceId <= 0) return
        if (request.toolName == null) {
            val uri = uris.firstOrNull() ?: return
            runCatching {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            openSelectedDocument(uri, "")
            return
        }
        ui.value = ui.value.copy(busy = true, status = "جارٍ نسخ الملف إلى مساحة PdfCraft الخاصة…", error = false)
        lifecycleScope.launch {
            try {
                val staged = withContext(Dispatchers.IO) { uris.map(::stageUri) }
                val paths = staged.map { it.first }
                val field = request.fieldName ?: return@launch
                ui.value = ui.value.copy(
                    busy = false,
                    status = "تم تجهيز ${paths.size} ملف/ملفات كمدخل للأداة.",
                    error = false,
                    stagedToolInput = StagedToolInput(request.toolName, field, paths),
                )
            } catch (error: Exception) {
                ui.value = ui.value.copy(busy = false, status = error.message ?: "تعذر استيراد الملف المحدد.", error = true)
            } catch (error: LinkageError) {
                ui.value = ui.value.copy(busy = false, status = "تعذر الوصول إلى الجسر الأصلي: ${error.message ?: "native error"}", error = true)
            }
        }
    }

    private fun stageUri(uri: Uri): Pair<String, String> {
        val current = ui.value
        val name = displayName(uri)
        val reservation = JSONObject(NativePdf.reserveInputPath(current.workspaceId, name))
        ensureNativeOk(reservation)
        val relative = reservation.getString("path")
        val root = File(current.workspaceRoot).canonicalFile
        val destination = File(root, relative).canonicalFile
        if (!destination.path.startsWith(root.path + File.separator)) throw SecurityException("مسار الملف المرحلي خارج مساحة العمل.")
        val source = contentResolver.openInputStream(uri) ?: throw IOException("تعذر فتح الملف الذي اخترته.")
        source.use { input ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                buffer.fill(0)
            }
        }
        val validation = JSONObject(NativePdf.validateInputPath(current.workspaceId, relative))
        ensureNativeOk(validation)
        return relative to name
    }

    private fun openSelectedDocument(uri: Uri, password: String) {
        val workspaceId = ui.value.workspaceId
        ui.value = ui.value.copy(busy = true, status = "جارٍ فتح PDF وتحليل بنيته…", error = false, passwordRequired = false, passwordWrong = false)
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val descriptor = contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("تعذر فتح الملف الذي اخترته.")
                    descriptor.use { opened ->
                        JSONObject(NativePdf.openDocumentDescriptor(workspaceId, opened, displayName(uri), password))
                    }
                }
                if (response.optString("status") != "ok") {
                    val code = response.optString("code")
                    if (code == "needs_password" || code == "wrong_password") {
                        ui.value = ui.value.copy(
                            busy = false,
                            status = response.optString("message", "هذا المستند محمي بكلمة مرور."),
                            error = code == "wrong_password",
                            passwordRequired = true,
                            passwordWrong = code == "wrong_password",
                            pendingOpenUri = uri.toString(),
                        )
                    } else {
                        ui.value = ui.value.copy(
                            busy = false,
                            status = response.optString("message", "تعذر فتح المستند."),
                            error = true,
                            pendingOpenUri = null,
                        )
                    }
                    return@launch
                }
                val doc = response.optJSONObject("document")
                val openedId = doc?.optLong("doc")?.takeIf { it > 0 }
                ui.value = ui.value.copy(busy = false, passwordRequired = false, pendingOpenUri = null, status = "تم فتح المستند.", error = false)
                refreshDocuments(preferredDocumentId = openedId, render = true)
            } catch (error: Exception) {
                ui.value = ui.value.copy(busy = false, status = error.message ?: "تعذر فتح المستند.", error = true, pendingOpenUri = null)
            } catch (error: LinkageError) {
                ui.value = ui.value.copy(
                    busy = false,
                    status = "تعذر الوصول إلى جسر SAF الأصلي: ${error.message ?: "native error"}",
                    error = true,
                    pendingOpenUri = null,
                )
            }
        }
    }

    private fun submitPassword(password: String) {
        val uri = ui.value.pendingOpenUri?.let { Uri.parse(it) } ?: return
        openSelectedDocument(uri, password)
    }

    private fun selectDocument(documentId: Long) {
        val document = ui.value.documents.firstOrNull { it.id == documentId } ?: return
        ui.value = ui.value.copy(activeDocumentId = documentId, pageIndex = 0, bitmap = null, status = document.name, error = false)
        renderDocumentPage(documentId, 0)
    }

    private fun changePage(pageIndex: Int) {
        val documentId = ui.value.activeDocumentId ?: return
        val count = ui.value.documents.firstOrNull { it.id == documentId }?.pageCount ?: return
        if (pageIndex !in 0 until count || ui.value.busy) return
        ui.value = ui.value.copy(pageIndex = pageIndex, bitmap = null, busy = true, status = "جارٍ رسم الصفحة ${pageIndex + 1}…", error = false)
        renderDocumentPage(documentId, pageIndex)
    }

    private fun renderDocumentPage(documentId: Long, pageIndex: Int) {
        val workspaceId = ui.value.workspaceId
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val packet = NativePdf.renderPage(workspaceId, documentId, pageIndex, 96f)
                    decodePng(packet)
                }
                ui.value = ui.value.copy(
                    activeDocumentId = documentId,
                    pageIndex = pageIndex,
                    bitmap = bitmap,
                    busy = false,
                    status = "الصفحة ${pageIndex + 1} من ${ui.value.documents.firstOrNull { it.id == documentId }?.pageCount ?: "?"}",
                    error = false,
                )
            } catch (error: Exception) {
                ui.value = ui.value.copy(busy = false, bitmap = null, status = error.message ?: "تعذر رسم الصفحة.", error = true)
            } catch (error: LinkageError) {
                ui.value = ui.value.copy(busy = false, bitmap = null, status = "تعذر تحميل محرك العرض: ${error.message ?: "native error"}", error = true)
            }
        }
    }

    private suspend fun refreshDocuments(preferredDocumentId: Long? = null, render: Boolean) {
        val workspaceId = ui.value.workspaceId
        if (workspaceId <= 0) return
        val response = withContext(Dispatchers.IO) { JSONObject(NativePdf.listDocuments(workspaceId)) }
        ensureNativeOk(response)
        val documents = parseDocuments(response)
        val previous = ui.value
        val activeId = preferredDocumentId?.takeIf { id -> documents.any { it.id == id } }
            ?: previous.activeDocumentId?.takeIf { id -> documents.any { it.id == id } }
            ?: documents.firstOrNull()?.id
        val pageCount = documents.firstOrNull { it.id == activeId }?.pageCount ?: 0
        val page = previous.pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        ui.value = previous.copy(documents = documents, activeDocumentId = activeId, pageIndex = page)
        refreshCommandCatalog()
        if (render && activeId != null && pageCount > 0) renderDocumentPage(activeId, page)
    }

    private suspend fun refreshCommandCatalog() {
        val snapshot = ui.value
        if (snapshot.workspaceId <= 0) return
        val arguments = JSONObject().apply { snapshot.activeDocumentId?.let { put("doc", it) } }
        try {
            val response = withContext(Dispatchers.IO) {
                JSONObject(NativePdf.callTool(snapshot.workspaceId, snapshot.activeDocumentId ?: 0L, "command_list", arguments.toString()))
            }
            ensureNativeOk(response)
            val commands = response.optJSONArray("results")?.optJSONObject(0)?.optJSONObject("value")?.optJSONArray("commands") ?: JSONArray()
            ui.value = ui.value.copy(commands = parseCommands(commands))
        } catch (error: Exception) {
            ui.value = ui.value.copy(commands = emptyList(), status = error.message ?: "تعذر تحميل سجل الأوامر.", error = true)
        } catch (error: LinkageError) {
            ui.value = ui.value.copy(commands = emptyList(), status = "تعذر الوصول إلى سجل الأوامر: ${error.message ?: "native error"}", error = true)
        }
    }

    private fun runTool(name: String, arguments: JSONObject) {
        val snapshot = ui.value
        val workspaceId = snapshot.workspaceId
        if (workspaceId <= 0) return
        val activeId = snapshot.activeDocumentId ?: 0L
        ui.value = snapshot.copy(busy = true, status = "جارٍ تنفيذ $name عبر محرك PdfCraft…", error = false)
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    JSONObject(NativePdf.callTool(workspaceId, activeId, name, arguments.toString()))
                }
                val isOk = response.optString("status") == "ok"
                ui.value = ui.value.copy(toolResult = response, busy = false, status = if (isOk) "اكتملت الأداة: $name" else response.optString("message", "فشلت الأداة."), error = !isOk)
                val openedId = if (name == "doc_open") extractCreatedDocumentId(response) else null
                refreshDocuments(preferredDocumentId = openedId, render = activeId > 0 || openedId != null)
            } catch (error: Exception) {
                ui.value = ui.value.copy(busy = false, status = error.message ?: "تعذر تنفيذ الأداة.", error = true)
            } catch (error: LinkageError) {
                ui.value = ui.value.copy(busy = false, status = "تعذر الوصول إلى جسر Rust: ${error.message ?: "native error"}", error = true)
            }
        }
    }

    private fun runCommand(command: PdfCommand, arguments: JSONObject) {
        if (!command.enabled) {
            ui.value = ui.value.copy(status = "الأمر غير مفعّل في حالة المستند الحالية.", error = true)
            return
        }
        when (command.id) {
            "file.open" -> pickPdf()
            "file.save" -> requestSave(ui.value.fullSave)
            "file.save_as" -> requestSave(true)
            "edit.undo" -> runTool("edit_undo", JSONObject())
            "edit.redo" -> runTool("edit_redo", JSONObject())
            "edit.find", "edit.advanced_search" -> ui.value = ui.value.copy(section = AppSection.SEARCH, status = "أدخل عبارة البحث في تبويب البحث.", error = false)
            "view.palette" -> ui.value = ui.value.copy(section = AppSection.COMMANDS)
            "view.layout.single" -> ui.value = ui.value.copy(section = AppSection.VIEWER, status = "عارض Android يعمل حالياً بوضع الصفحة الواحدة.", error = false)
            else -> if (command.toolName != null) {
                runTool("command_run", JSONObject().put("id", command.id).put("params", arguments))
            } else {
                ui.value = ui.value.copy(
                    status = "الأمر ${command.id} مسجّل في PdfCraft لكنه بلا أداة Automation أو معالج Android بعد؛ لم أعتبره منفذاً.",
                    error = true,
                )
            }
        }
    }

    private fun searchText(query: String, continuation: SearchContinuation? = null) {
        val doc = ui.value.activeDocumentId ?: return
        if (query.isBlank()) {
            ui.value = ui.value.copy(searchMatches = emptyList(), status = "أدخل عبارة للبحث.", error = false)
            return
        }
        ui.value = ui.value.copy(busy = true, status = "جارٍ البحث في نص المستند…", error = false)
        val args = JSONObject().put("query", query).put("limit", 500)
        continuation?.let { args.put("start_page", it.startPage).put("skip", it.skip) }
        runSearch(doc, query, args)
    }

    private fun continueSearch(query: String, continuation: SearchContinuation) {
        searchText(query, continuation)
    }

    private fun runSearch(documentId: Long, query: String, args: JSONObject) {
        val workspaceId = ui.value.workspaceId
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    JSONObject(NativePdf.callTool(workspaceId, documentId, "text_find", args.toString()))
                }
                if (response.optString("status") != "ok") {
                    ui.value = ui.value.copy(busy = false, status = response.optString("message", "تعذر البحث."), error = true)
                    return@launch
                }
                val payload = response.optJSONArray("results")?.optJSONObject(0)?.optJSONObject("value") ?: JSONObject()
                val matchesJson = payload.optJSONArray("matches") ?: JSONArray()
                val matches = (0 until matchesJson.length()).mapNotNull { index ->
                    val match = matchesJson.optJSONObject(index) ?: return@mapNotNull null
                    SearchMatch(match.optInt("page", 1), match.optString("text"))
                }
                val next = payload.optJSONObject("next")?.let { SearchContinuation(it.optInt("start_page", 1), it.optInt("skip", 0)) }
                ui.value = ui.value.copy(
                    busy = false,
                    searchMatches = matches,
                    searchComplete = payload.optBoolean("complete", true),
                    searchContinuation = next,
                    status = "${payload.optInt("count", matches.size)} نتيجة للعبارة: $query",
                    error = false,
                )
            } catch (error: Exception) {
                ui.value = ui.value.copy(busy = false, status = error.message ?: "تعذر البحث في المستند.", error = true)
            }
        }
    }

    private fun requestSave(full: Boolean) {
        val document = ui.value.documents.firstOrNull { it.id == ui.value.activeDocumentId } ?: return
        val response = runCatching { JSONObject(NativePdf.beginSave(ui.value.workspaceId, document.id, full)) }.getOrElse {
            ui.value = ui.value.copy(status = it.message ?: "تعذر تجهيز الحفظ.", error = true)
            return
        }
        if (response.optString("status") != "ok") {
            ui.value = ui.value.copy(status = response.optString("message", "تعذر تجهيز الحفظ."), error = true)
            return
        }
        val token = response.optLong("transferToken")
        pendingTransfer = TransferRequest(token, ui.value.workspaceId, document.id, response.optLong("size", -1), outputPdfName(document.name), isSave = true)
        ui.value = ui.value.copy(status = "اختر مكان حفظ نسخة PDF…", error = false)
        pdfOutputPicker.launch(outputPdfName(document.name))
    }

    private fun requestPathExport(path: String) {
        val response = runCatching { JSONObject(NativePdf.beginExportFile(ui.value.workspaceId, path)) }.getOrElse {
            ui.value = ui.value.copy(status = it.message ?: "تعذر تجهيز الملف للتصدير.", error = true)
            return
        }
        if (response.optString("status") != "ok") {
            ui.value = ui.value.copy(status = response.optString("message", "تعذر تجهيز الملف للتصدير."), error = true)
            return
        }
        val name = response.optString("fileName", "PdfCraft-output.bin")
        pendingTransfer = TransferRequest(response.optLong("transferToken"), ui.value.workspaceId, size = response.optLong("size", -1), fileName = name, isSave = false)
        ui.value = ui.value.copy(status = "اختر مكان تصدير $name…", error = false)
        genericOutputPicker.launch(name)
    }

    private fun requestArtifactExport(token: Long, fileName: String) {
        pendingTransfer = TransferRequest(token, ui.value.workspaceId, size = -1, fileName = fileName, isSave = false)
        ui.value = ui.value.copy(status = "اختر مكان تصدير الملف…", error = false)
        genericOutputPicker.launch(fileName)
    }

    private fun handleOutputUri(uri: Uri?) {
        val transfer = pendingTransfer
        pendingTransfer = null
        if (transfer == null) return
        if (uri == null) {
            NativePdf.abortTransfer(transfer.token)
            ui.value = ui.value.copy(status = "تم إلغاء التصدير.", error = false)
            return
        }
        ui.value = ui.value.copy(busy = true, status = "جارٍ نقل الملف إلى المكان المحدد…", error = false)
        lifecycleScope.launch {
            try {
                val finalize = withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        var offset = 0L
                        var complete = false
                        while (!complete) {
                            val packet = NativePdf.readTransferChunk(transfer.token, offset, 256 * 1024)
                            if (packet.size < 4) throw IOException("استجابة نقل الملف غير مكتملة.")
                            val header = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
                            val length = header.int
                            if (length == -1) {
                                if (packet.size < 8) throw IOException("تعذر قراءة خطأ نقل الملف.")
                                val messageLength = header.int.coerceAtLeast(0).coerceAtMost(packet.size - 8)
                                throw IOException(String(packet, 8, messageLength, Charsets.UTF_8))
                            }
                            if (length < 0 || length > packet.size - 4) throw IOException("حجم جزء الملف غير صالح.")
                            if (length == 0) {
                                complete = true
                            } else {
                                output.write(packet, 4, length)
                                offset += length
                                if (transfer.size >= 0 && offset >= transfer.size) complete = true
                            }
                            if (transfer.size >= 0 && offset > transfer.size) throw IOException("حجم الملف المنقول تجاوز الحجم المتوقع.")
                        }
                        if (transfer.size >= 0 && offset != transfer.size) throw IOException("الملف المنقول أقصر من الحجم المتوقع.")
                    } ?: throw IOException("تعذر فتح وجهة الحفظ للكتابة.")
                    if (transfer.isSave) {
                        val documentId = transfer.documentId ?: throw IOException("معرّف المستند غير موجود.")
                        val response = JSONObject(NativePdf.finishSave(transfer.workspaceId, documentId, transfer.token))
                        ensureNativeOk(response)
                        response
                    } else {
                        NativePdf.finishTransfer(transfer.token)
                        JSONObject().put("status", "ok")
                    }
                }
                ui.value = ui.value.copy(busy = false, status = if (transfer.isSave) "تم حفظ PDF وتحديث حالة المستند." else "اكتمل تصدير ${transfer.fileName}.", error = false)
                if (transfer.isSave) refreshDocuments(preferredDocumentId = transfer.documentId, render = false)
            } catch (error: Exception) {
                NativePdf.abortTransfer(transfer.token)
                ui.value = ui.value.copy(busy = false, status = error.message ?: "تعذر نقل الملف كاملاً.", error = true)
            }
        }
    }

    private fun displayName(uri: Uri): String {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        } ?: "document.pdf"
    }

    private fun ensureNativeOk(response: JSONObject) {
        if (response.optString("status") != "ok") throw IOException(response.optString("message", "فشلت عملية PdfCraft."))
    }

    private fun outputPdfName(sourceName: String): String {
        val base = sourceName.substringBeforeLast('.', sourceName).ifBlank { "document" }
        val safe = base.map { c -> if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_' }.joinToString("").take(100)
        return "${safe.ifBlank { "document" }}.pdf"
    }
}

private object NativePdf {
    init {
        System.loadLibrary("pdfcraft_android_native")
    }

    external fun createWorkspace(parent: String): String
    external fun closeWorkspace(workspaceId: Long): String
    external fun reserveInputPath(workspaceId: Long, fileName: String): String
    external fun validateInputPath(workspaceId: Long, relativePath: String): String
    external fun openDocument(workspaceId: Long, relativePath: String, password: String): String
    external fun openDocumentDescriptor(workspaceId: Long, descriptor: ParcelFileDescriptor, name: String, password: String): String
    external fun listDocuments(workspaceId: Long): String
    external fun toolCatalog(): String
    external fun callTool(workspaceId: Long, activeDocumentId: Long, toolName: String, argumentsJson: String): String
    external fun renderPage(workspaceId: Long, documentId: Long, pageIndex: Int, dpi: Float): ByteArray
    external fun beginSave(workspaceId: Long, documentId: Long, full: Boolean): String
    external fun beginExportFile(workspaceId: Long, path: String): String
    external fun readTransferChunk(token: Long, offset: Long, requested: Int): ByteArray
    external fun finishSave(workspaceId: Long, documentId: Long, token: Long): String
    external fun finishTransfer(token: Long)
    external fun abortTransfer(token: Long)
}

private fun decodePng(packet: ByteArray): Bitmap {
    val bitmap = BitmapFactory.decodeByteArray(packet, 0, packet.size)
    if (bitmap != null) return bitmap
    val message = if (packet.decodeToString().startsWith("ERR:")) packet.decodeToString().removePrefix("ERR:") else "تعذر فك صورة الصفحة التي أعادها المحرك."
    throw IOException(message)
}

private fun parseTools(text: String): List<PdfTool> {
    val response = JSONObject(text)
    if (response.optString("status") != "ok") throw IOException(response.optString("message", "تعذر قراءة مخطط الأدوات."))
    val array = response.optJSONArray("tools") ?: JSONArray()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        PdfTool(
            name = item.optString("name"),
            title = item.optString("title", item.optString("name")),
            category = item.optString("category", item.optString("name").substringBefore('_')),
            description = item.optString("description"),
            readOnly = item.optBoolean("readOnly"),
            destructive = item.optBoolean("destructive"),
            schema = item.optJSONObject("inputSchema") ?: JSONObject().put("type", "object").put("properties", JSONObject()),
        )
    }
}

private fun parseCommands(array: JSONArray): List<PdfCommand> {
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
        PdfCommand(
            id = id,
            label = item.optString("label", id),
            menu = item.optString("menu", "Other"),
            shortcut = if (item.isNull("shortcut")) null else item.optString("shortcut").takeIf(String::isNotBlank),
            enabled = item.optBoolean("enabled"),
            toolName = if (item.isNull("tool")) null else item.optString("tool").takeIf(String::isNotBlank),
            schema = item.optJSONObject("params") ?: JSONObject().put("type", "object").put("properties", JSONObject()),
        )
    }
}

private fun parseDocuments(response: JSONObject): List<PdfDocument> {
    val array = response.optJSONArray("documents") ?: JSONArray()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val undo = item.opt("undo")
        val redo = item.opt("redo")
        PdfDocument(
            id = item.optLong("doc"),
            name = item.optString("name", "PDF ${item.optLong("doc")}"),
            pageCount = item.optInt("pages"),
            dirty = item.optBoolean("dirty"),
            canUndo = undo != null && undo != JSONObject.NULL,
            canRedo = redo != null && redo != JSONObject.NULL,
            editable = item.optBoolean("editable", true),
        )
    }.filter { it.id > 0 }
}

private fun extractCreatedDocumentId(response: JSONObject): Long? {
    val results = response.optJSONArray("results") ?: return null
    for (index in 0 until results.length()) {
        val value = results.optJSONObject(index)?.optJSONObject("value") ?: continue
        val id = value.optLong("doc")
        if (id > 0) return id
    }
    return null
}

@Composable
private fun PdfCraftApp(
    state: PdfUiState,
    onOpenPdf: () -> Unit,
    onSubmitPassword: (String) -> Unit,
    onSelectDocument: (Long) -> Unit,
    onPageChange: (Int) -> Unit,
    onSectionChange: (AppSection) -> Unit,
    onToolRun: (String, JSONObject) -> Unit,
    onCommandRun: (PdfCommand, JSONObject) -> Unit,
    onSearch: (String) -> Unit,
    onContinueSearch: (String, SearchContinuation) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: (Boolean) -> Unit,
    onPickToolInput: (String, String, Boolean) -> Unit,
    onExportPath: (String) -> Unit,
    onExportArtifact: (Long, String) -> Unit,
    onToggleFullSave: (Boolean) -> Unit,
) {
    val active = state.documents.firstOrNull { it.id == state.activeDocumentId }
    Scaffold(
        bottomBar = {
            NavigationBar {
                AppSection.entries.forEach { section ->
                    NavigationBarItem(
                        selected = state.section == section,
                        onClick = { onSectionChange(section) },
                        icon = { Text(section.label.take(1)) },
                        label = { Text(section.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("PdfCraft", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenPdf, enabled = !state.busy) { Text("فتح PDF") }
                if (active != null) TextButton(onClick = { onSave(state.fullSave) }, enabled = !state.busy) { Text("حفظ") }
            }
            if (state.documents.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.documents.forEach { document ->
                        val selected = document.id == state.activeDocumentId
                        TextButton(onClick = { onSelectDocument(document.id) }) {
                            Text(
                                text = (if (document.dirty) "● " else "") + document.name,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
            if (state.busy || state.status.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.busy) CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    Text(
                        state.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.passwordRequired) {
                PasswordPanel(state.passwordWrong, onSubmitPassword)
            }
            when (state.section) {
                AppSection.VIEWER -> ViewerPane(state, active, onPageChange, onUndo, onRedo, onSave)
                AppSection.COMMANDS -> CommandsPane(
                    commands = state.commands,
                    tools = state.tools,
                    active = active,
                    stagedInput = state.stagedToolInput,
                    result = state.toolResult,
                    onRun = onCommandRun,
                    onPickFile = onPickToolInput,
                    onExportPath = onExportPath,
                    onExportArtifact = onExportArtifact,
                )
                AppSection.TOOLS -> ToolsPane(
                    tools = state.tools,
                    active = active,
                    stagedInput = state.stagedToolInput,
                    result = state.toolResult,
                    onRun = onToolRun,
                    onPickFile = onPickToolInput,
                    onExportPath = onExportPath,
                    onExportArtifact = onExportArtifact,
                )
                AppSection.SEARCH -> SearchPane(state, active, onSearch, onContinueSearch, onPageChange)
                AppSection.HISTORY -> HistoryPane(state, active, onUndo, onRedo, onSave, onToggleFullSave)
            }
        }
    }
}

@Composable
private fun PasswordPanel(wrong: Boolean, onSubmit: (String) -> Unit) {
    var password by remember(wrong) { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(if (wrong) "كلمة المرور غير صحيحة" else "كلمة مرور PDF") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { val value = password; password = ""; onSubmit(value) }, enabled = password.isNotEmpty()) { Text("فتح") }
    }
}

@Composable
private fun ViewerPane(
    state: PdfUiState,
    active: PdfDocument?,
    onPageChange: (Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: (Boolean) -> Unit,
) {
    if (active == null) {
        EmptyPane("افتح مستند PDF لبدء العرض والتحرير.")
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onPageChange(state.pageIndex - 1) }, enabled = state.pageIndex > 0 && !state.busy) { Text("‹ السابقة") }
            Text("${state.pageIndex + 1} / ${active.pageCount}", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onPageChange(state.pageIndex + 1) }, enabled = state.pageIndex + 1 < active.pageCount && !state.busy) { Text("التالية ›") }
        }
        if (state.bitmap != null) {
            ZoomablePage(state.bitmap, active.id, state.pageIndex)
        } else if (state.busy) {
            Box(modifier = Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            EmptyPane("لا توجد صورة صفحة حالية.")
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUndo, enabled = active.canUndo && !state.busy, modifier = Modifier.weight(1f)) { Text("تراجع") }
            OutlinedButton(onClick = onRedo, enabled = active.canRedo && !state.busy, modifier = Modifier.weight(1f)) { Text("إعادة") }
            Button(onClick = { onSave(state.fullSave) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("حفظ") }
        }
        Text(
            "العارض يستخدم محرك Rust الأصلي. اضغط بإصبعين للتكبير والتحريك؛ دقة العرض الحالية 96 DPI ويمكن تغييرها عند إكمال إعدادات العارض.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ZoomablePage(bitmap: Bitmap, documentId: Long, pageIndex: Int) {
    var scale by remember(documentId, pageIndex) { mutableFloatStateOf(1f) }
    var translation by remember(documentId, pageIndex) { mutableStateOf(Offset.Zero) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "صفحة PDF ${pageIndex + 1}",
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .pointerInput(documentId, pageIndex) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    translation += pan
                    if (scale <= 1.01f) translation = Offset.Zero
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = translation.x, translationY = translation.y),
    )
}

@Composable
private fun ToolsPane(
    tools: List<PdfTool>,
    active: PdfDocument?,
    stagedInput: StagedToolInput?,
    result: JSONObject?,
    onRun: (String, JSONObject) -> Unit,
    onPickFile: (String, String, Boolean) -> Unit,
    onExportPath: (String) -> Unit,
    onExportArtifact: (Long, String) -> Unit,
) {
    var selectedTool by remember { mutableStateOf<PdfTool?>(null) }
    if (selectedTool == null) {
        ToolCatalogPane(tools, onSelect = { selectedTool = it })
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
            TextButton(onClick = { selectedTool = null }) { Text("‹ كل الأدوات (${tools.size})") }
            val tool = selectedTool ?: return@Column
            Text(tool.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${tool.category} · ${tool.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(tool.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            if (tool.destructive) Text("هذه الأداة معلّمة كتغيير تدميري/استبدالي في سجل المصدر.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            ToolSchemaForm(tool, active, stagedInput, onRun, onPickFile)
            if (result != null) {
                ToolResultPanel(result, onExportPath, onExportArtifact)
            }
        }
    }
}

@Composable
private fun CommandsPane(
    commands: List<PdfCommand>,
    tools: List<PdfTool>,
    active: PdfDocument?,
    stagedInput: StagedToolInput?,
    result: JSONObject?,
    onRun: (PdfCommand, JSONObject) -> Unit,
    onPickFile: (String, String, Boolean) -> Unit,
    onExportPath: (String) -> Unit,
    onExportArtifact: (Long, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = commands.firstOrNull { it.id == selectedId }
    val nativeRoutes = setOf("file.open", "file.save", "file.save_as", "edit.undo", "edit.redo", "edit.find", "edit.advanced_search", "view.palette", "view.layout.single")
    if (selected != null) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
            TextButton(onClick = { selectedId = null }) { Text("‹ كل الأوامر (${commands.size})") }
            Text(selected.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${selected.menu} · ${selected.id}${selected.shortcut?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(if (selected.enabled) "مفعّل في حالة المستند الحالية." else "غير مفعّل في حالة المستند الحالية.", style = MaterialTheme.typography.bodySmall)
            val tool = selected.toolName?.let { name -> tools.firstOrNull { it.name == name } }
            if (selected.id in nativeRoutes) {
                Text("يستخدم هذا الأمر معالج Android الملائم بدلاً من معاملات واجهة سطح المكتب.", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { onRun(selected, JSONObject()) },
                    enabled = selected.enabled && (active != null || selected.id == "file.open" || selected.id == "view.palette"),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("تنفيذ في Android") }
            } else if (tool != null) {
                Text("الأداة المرتبطة: ${tool.name}. النموذج يستخدم مخطط PdfCraft الأصلي.", style = MaterialTheme.typography.bodySmall)
                ToolSchemaForm(
                    tool.copy(title = selected.label, schema = selected.schema),
                    active,
                    stagedInput,
                    onRun = { _, arguments -> onRun(selected, arguments) },
                    onPickFile = onPickFile,
                )
            } else {
                Text(
                    "لا يوجد ربط تنفيذ لهذا الأمر في هذه الشريحة. يتطلب محول Compose/Android أو أداة Rust مطابقة؛ سيبقى ظاهراً كفجوة ولا يُعد منقولاً.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                selected.toolName?.let { Text("مرجع أداة غير متوفر في الكتالوج: $it", color = MaterialTheme.colorScheme.error) }
            }
            if (result != null) ToolResultPanel(result, onExportPath, onExportArtifact)
        }
    } else {
        val filtered = remember(commands, query) {
            commands.filter { query.isBlank() || "${it.id} ${it.label} ${it.menu}".contains(query, ignoreCase = true) }
        }
        val unresolved = commands.count { it.toolName == null && it.id !in nativeRoutes }
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("ابحث في أوامر PdfCraft") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${filtered.size} من ${commands.size} أمر · $unresolved أمر بلا مسار تنفيذ بعد.", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { command ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedId = command.id }) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(command.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(
                                    if (!command.enabled) "غير مفعّل" else if (command.toolName != null || command.id in nativeRoutes) "مسار متاح" else "غير مربوط",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Text("${command.menu} · ${command.id}${command.toolName?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCatalogPane(tools: List<PdfTool>, onSelect: (PdfTool) -> Unit) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("all") }
    val categories = remember(tools) { listOf("all") + tools.map { it.category }.distinct().sorted() }
    val filtered = remember(tools, query, category) {
        tools.filter { tool ->
            (category == "all" || tool.category == category) &&
                (query.isBlank() || "${tool.name} ${tool.title} ${tool.description}".contains(query, ignoreCase = true))
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("ابحث في الأدوات") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { item ->
                FilterChip(selected = category == item, onClick = { category = item }, label = { Text(if (item == "all") "الكل" else item) })
            }
        }
        Text("${filtered.size} من ${tools.size} أداة", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 4.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.name }) { tool ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(tool) }) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(tool.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(if (tool.readOnly) "قراءة" else if (tool.destructive) "تغيير حساس" else "تحرير", style = MaterialTheme.typography.labelSmall)
                        }
                        Text("${tool.category} · ${tool.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(tool.description, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ToolSchemaForm(
    tool: PdfTool,
    active: PdfDocument?,
    stagedInput: StagedToolInput?,
    onRun: (String, JSONObject) -> Unit,
    onPickFile: (String, String, Boolean) -> Unit,
) {
    val properties = tool.schema.optJSONObject("properties") ?: JSONObject()
    val requiredArray = tool.schema.optJSONArray("required") ?: JSONArray()
    val required = remember(tool.name) { (0 until requiredArray.length()).mapNotNull { requiredArray.optString(it).takeIf(String::isNotBlank) }.toSet() }
    val fields = remember(tool.name) { mutableStateMapOf<String, String>() }
    var advanced by remember(tool.name) { mutableStateOf(false) }
    var rawJson by remember(tool.name) { mutableStateOf("{}") }
    var confirmRun by remember(tool.name) { mutableStateOf(false) }
    var formError by remember(tool.name) { mutableStateOf<String?>(null) }

    LaunchedEffect(stagedInput) {
        if (stagedInput?.toolName == tool.name && properties.has(stagedInput.fieldName)) {
            val schema = properties.optJSONObject(stagedInput.fieldName) ?: JSONObject()
            fields[stagedInput.fieldName] = if (schema.optString("type") == "array") JSONArray(stagedInput.paths).toString() else stagedInput.paths.firstOrNull().orEmpty()
        }
    }

    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (properties.length() == 0) Text("لا تتطلب هذه الأداة مدخلات.", style = MaterialTheme.typography.bodySmall)
        val keys = properties.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "doc") continue // filled from the selected document when the tool needs it
            val schema = properties.optJSONObject(key) ?: JSONObject()
            SchemaField(
                tool = tool,
                name = key,
                schema = schema,
                value = fields[key].orEmpty(),
                required = required.contains(key),
                onChange = { fields[key] = it; formError = null },
                onPickFiles = { multiple -> onPickFile(tool.name, key, multiple) },
            )
        }
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "إخفاء محرر JSON" else "تحرير JSON الخام (للمخططات المركبة)") }
        if (advanced) {
            OutlinedTextField(
                value = rawJson,
                onValueChange = { rawJson = it },
                label = { Text("كائن arguments كامل وفق مخطط الأداة") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        formError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(
            onClick = {
                if (tool.destructive) confirmRun = true
                else submitSchemaForm(tool, fields.toMap(), required, advanced, rawJson, active?.id, onRun) { formError = it }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("تشغيل ${tool.title}") }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmRun) {
        AlertDialog(
            onDismissRequest = { confirmRun = false },
            title = { Text("تأكيد تنفيذ الأداة") },
            text = { Text("الأداة ${tool.name} معلّمة كتغيير تدميري في المصدر. راجع الملف والصفحات قبل المتابعة.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRun = false
                    submitSchemaForm(tool, fields.toMap(), required, advanced, rawJson, active?.id, onRun) { formError = it }
                }) { Text("تنفيذ") }
            },
            dismissButton = { TextButton(onClick = { confirmRun = false }) { Text("إلغاء") } },
        )
    }
}

private fun submitSchemaForm(
    tool: PdfTool,
    fields: Map<String, String>,
    required: Set<String>,
    advanced: Boolean,
    rawJson: String,
    activeDocumentId: Long?,
    onRun: (String, JSONObject) -> Unit,
    onError: (String) -> Unit,
) {
    try {
        val args = if (advanced) JSONObject(rawJson) else JSONObject().apply {
            val properties = tool.schema.optJSONObject("properties") ?: JSONObject()
            val keys = properties.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "doc") continue
                val raw = fields[key]?.trim().orEmpty()
                if (raw.isEmpty()) {
                    if (required.contains(key)) throw IllegalArgumentException("الحقل «${key.replace('_', ' ')}» مطلوب.")
                    continue
                }
                val propertySchema = properties.optJSONObject(key) ?: JSONObject()
                when (propertySchema.optString("type")) {
                    "integer" -> put(key, raw.toLong())
                    "number" -> put(key, raw.toDouble())
                    "boolean" -> put(key, raw.toBooleanStrict())
                    "array", "object" -> put(key, org.json.JSONTokener(raw).nextValue())
                    else -> put(key, raw)
                }
            }
        }
        if (activeDocumentId != null && !args.has("doc") && (tool.schema.optJSONObject("properties")?.has("doc") == true)) {
            args.put("doc", activeDocumentId)
        }
        onRun(tool.name, args)
    } catch (error: Exception) {
        onError(error.message ?: "تعذر تحليل مدخلات الأداة.")
    }
}

@Composable
private fun SchemaField(
    tool: PdfTool,
    name: String,
    schema: JSONObject,
    value: String,
    required: Boolean,
    onChange: (String) -> Unit,
    onPickFiles: (Boolean) -> Unit,
) {
    val label = name.replace('_', ' ') + if (required) " *" else ""
    val type = schema.optString("type", "string")
    val enumValues = schema.optJSONArray("enum")
    val description = schema.optString("description")
    val outputPath = name == "out" || name.startsWith("out_") || (tool.name == "doc_save" && name == "path")
    val pathLike = name.contains("path", true) || name.contains("file", true) || name == "source" || name == "input"
    val canPickFile = pathLike && !outputPath

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (enumValues != null) {
            var expanded by remember(tool.name, name) { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (value.isBlank()) "$label — اختر قيمة" else "$label: $value")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    (0 until enumValues.length()).forEach { index ->
                        val option = enumValues.optString(index)
                        DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false })
                    }
                }
            }
        } else if (type == "boolean") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = value.toBooleanStrictOrNull() ?: false, onCheckedChange = { onChange(it.toString()) })
                Text(label)
            }
        } else {
            val numeric = type == "integer" || type == "number"
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label) },
                supportingText = if (description.isNotBlank()) ({ Text(description) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
                singleLine = type != "array" && type != "object",
                minLines = if (type == "array" || type == "object") 3 else 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (canPickFile) {
            Button(onClick = { onPickFiles(type == "array") }) { Text(if (type == "array") "اختيار ملفات من الجهاز" else "اختيار ملف من الجهاز") }
        }
    }
}

@Composable
private fun ToolResultPanel(
    result: JSONObject,
    onExportPath: (String) -> Unit,
    onExportArtifact: (Long, String) -> Unit,
) {
    val results = result.optJSONArray("results") ?: JSONArray()
    val paths = remember(result) { collectPaths(result) }
    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("نتيجة التنفيذ", style = MaterialTheme.typography.titleMedium)
        Text(result.toString(2).take(16_000), style = MaterialTheme.typography.bodySmall)
        paths.forEach { path ->
            OutlinedButton(onClick = { onExportPath(path) }, modifier = Modifier.fillMaxWidth()) { Text("تصدير الملف: ${path.substringAfterLast('/')}") }
        }
        (0 until results.length()).forEach { index ->
            val item = results.optJSONObject(index) ?: return@forEach
            if (item.optString("kind") == "png") {
                val token = item.optLong("transferToken")
                if (token > 0) Button(onClick = { onExportArtifact(token, "PdfCraft-image-${index + 1}.png") }) { Text("تصدير صورة PNG (${item.optInt("width")}×${item.optInt("height")})") }
            }
        }
    }
}

private fun collectPaths(value: Any?, key: String = ""): List<String> {
    val found = linkedSetOf<String>()
    when (value) {
        is JSONObject -> {
            val keys = value.keys()
            while (keys.hasNext()) {
                val childKey = keys.next()
                val child = value.opt(childKey)
                if (child is String && (childKey.contains("path", true) || childKey == "out" || childKey.contains("file", true)) && child.isNotBlank()) {
                    found.add(child)
                } else {
                    found.addAll(collectPaths(child, childKey))
                }
            }
        }
        is JSONArray -> for (index in 0 until value.length()) found.addAll(collectPaths(value.opt(index), key))
    }
    return found.toList()
}

@Composable
private fun SearchPane(
    state: PdfUiState,
    active: PdfDocument?,
    onSearch: (String) -> Unit,
    onContinue: (String, SearchContinuation) -> Unit,
    onPage: (Int) -> Unit,
) {
    var query by remember(active?.id) { mutableStateOf("") }
    if (active == null) {
        EmptyPane("افتح مستنداً قبل البحث.")
        return
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("بحث داخل المستند", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("عبارة البحث") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = { onSearch(query) }, enabled = query.isNotBlank() && !state.busy) { Text("بحث") }
        }
        if (state.searchMatches.isNotEmpty()) {
            Text("${state.searchMatches.size} نتيجة${if (state.searchComplete) " (مكتمل)" else " (جزئي)"}", style = MaterialTheme.typography.labelMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.searchMatches) { match ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onPage(match.page - 1) }) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("الصفحة ${match.page}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(match.text)
                        }
                    }
                }
            }
        } else if (!state.busy) {
            Text("ستظهر النتائج مع رقم الصفحة ومقتطف النص، ويمكن الضغط على نتيجة للانتقال إليها.", style = MaterialTheme.typography.bodySmall)
        }
        if (!state.searchComplete && state.searchContinuation != null) {
            OutlinedButton(onClick = { onContinue(query, state.searchContinuation) }, enabled = !state.busy) { Text("عرض المزيد من النتائج") }
        }
    }
}

@Composable
private fun HistoryPane(
    state: PdfUiState,
    active: PdfDocument?,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: (Boolean) -> Unit,
    onToggleFullSave: (Boolean) -> Unit,
) {
    if (active == null) {
        EmptyPane("افتح مستنداً لاستخدام التراجع والحفظ.")
        return
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("سجل التعديلات والحفظ", style = MaterialTheme.typography.titleLarge)
        Text(active.name, style = MaterialTheme.typography.titleMedium)
        Text(if (active.dirty) "هناك تغييرات غير محفوظة." else "لا توجد تغييرات معلّقة.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onUndo, enabled = active.canUndo && !state.busy, modifier = Modifier.weight(1f)) { Text("تراجع") }
            OutlinedButton(onClick = onRedo, enabled = active.canRedo && !state.busy, modifier = Modifier.weight(1f)) { Text("إعادة") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.fullSave, onCheckedChange = onToggleFullSave)
            Column {
                Text("إعادة كتابة كاملة عند الحفظ")
                Text("قد تغيّر التوقيعات الرقمية؛ اتركها غير محددة للحفظ التدريجي.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = { onSave(state.fullSave) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("حفظ نسخة إلى الجهاز") }
        Text("تُحدّث حالة المستند المحفوظ فقط بعد اكتمال الكتابة إلى موفر الملفات وتثبيت نسخة داخل مساحة التطبيق.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyPane(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
