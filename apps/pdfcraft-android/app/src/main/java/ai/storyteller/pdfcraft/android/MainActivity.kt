package ai.storyteller.pdfcraft.android

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

private data class PdfScreenState(
    val fileName: String = "",
    val message: String = "افتح ملف PDF لعرض صفحة باستخدام محرك Rust.",
    val loading: Boolean = false,
    val passwordRequired: Boolean = false,
    val error: Boolean = false,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val bitmap: Bitmap? = null,
)

class MainActivity : ComponentActivity() {
    private val screenState = mutableStateOf(PdfScreenState())
    private var pendingUri: Uri? = null
    private var documentPassword = ""
    private lateinit var openDocument: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // Some document providers grant only a transient read permission.
                }
                pendingUri = uri
                documentPassword = ""
                inspect(uri, "")
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PdfCraftPrototype(
                        state = screenState.value,
                        onOpen = { openDocument.launch(arrayOf("application/pdf")) },
                        onPassword = { password -> pendingUri?.let { inspect(it, password) } },
                        onPageChange = ::renderPage,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        documentPassword = ""
        super.onDestroy()
    }

    private fun inspect(uri: Uri, password: String) {
        documentPassword = password
        val fileName = displayName(uri)
        screenState.value = PdfScreenState(fileName = fileName, message = "جارٍ تحليل الملف ورسم الصفحة الأولى…", loading = true)

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("تعذر قراءة الملف المحدد.")
                    try {
                        val response = NativePdf.inspectPdf(bytes, password)
                        val pageCount = if (response.startsWith("OK|")) response.split('|').getOrNull(2)?.toIntOrNull() ?: 0 else 0
                        val bitmap = if (pageCount > 0) decodeNativeBitmap(NativePdf.renderPage(bytes, password, 0, 1.0f)) else null
                        Triple(response, pageCount, bitmap)
                    } finally {
                        bytes.fill(0)
                    }
                }

                val (response, pageCount, bitmap) = result
                when {
                    response == "ERR|NEEDS_PASSWORD" -> screenState.value = PdfScreenState(
                        fileName = fileName,
                        message = "هذا المستند محمي بكلمة مرور. أدخلها لفتحه.",
                        passwordRequired = true,
                    )
                    response == "ERR|WRONG_PASSWORD" -> screenState.value = PdfScreenState(
                        fileName = fileName,
                        message = "كلمة المرور غير صحيحة. حاول مرة أخرى.",
                        passwordRequired = true,
                        error = true,
                    )
                    response.startsWith("OK|") && bitmap != null -> {
                        val version = response.split('|').getOrNull(1) ?: "?"
                        screenState.value = PdfScreenState(
                            fileName = fileName,
                            message = "PDF $version — الصفحة 1 من $pageCount",
                            pageCount = pageCount,
                            bitmap = bitmap,
                        )
                    }
                    response.startsWith("OK|") -> screenState.value = PdfScreenState(
                        fileName = fileName,
                        message = "تم تحليل الملف، لكن تعذر عرض الصفحة الأولى.",
                        pageCount = pageCount,
                        error = true,
                    )
                    else -> screenState.value = PdfScreenState(
                        fileName = fileName,
                        message = response.removePrefix("ERR|"),
                        error = true,
                    )
                }
            } catch (error: Exception) {
                screenState.value = PdfScreenState(
                    fileName = fileName,
                    message = error.message ?: "تعذر فحص المستند.",
                    error = true,
                )
            } catch (error: LinkageError) {
                screenState.value = PdfScreenState(
                    fileName = fileName,
                    message = "تعذر تحميل مكتبة Rust الأصلية: ${error.message ?: "native library error"}",
                    error = true,
                )
            }
        }
    }

    private fun renderPage(pageIndex: Int) {
        val uri = pendingUri ?: return
        val current = screenState.value
        if (pageIndex !in 0 until current.pageCount || current.loading) return
        val password = documentPassword
        val fileName = current.fileName
        screenState.value = current.copy(pageIndex = pageIndex, bitmap = null, loading = true, error = false, message = "جارٍ رسم الصفحة ${pageIndex + 1}…")

        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("تعذر إعادة قراءة ملف PDF.")
                    try {
                        decodeNativeBitmap(NativePdf.renderPage(bytes, password, pageIndex, 1.0f))
                    } finally {
                        bytes.fill(0)
                    }
                }
                screenState.value = screenState.value.copy(
                    fileName = fileName,
                    pageIndex = pageIndex,
                    bitmap = bitmap,
                    loading = false,
                    error = false,
                    message = "الصفحة ${pageIndex + 1} من ${screenState.value.pageCount}",
                )
            } catch (error: Exception) {
                screenState.value = screenState.value.copy(
                    loading = false,
                    bitmap = null,
                    error = true,
                    message = error.message ?: "تعذر رسم الصفحة ${pageIndex + 1}.",
                )
            }
        }
    }

    private fun displayName(uri: Uri): String {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else null
            } ?: "PDF document"
    }
}

private object NativePdf {
    init {
        System.loadLibrary("pdfcraft_android_native")
    }

    external fun inspectPdf(bytes: ByteArray, password: String): String
    external fun renderPage(bytes: ByteArray, password: String, pageIndex: Int, scale: Float): ByteArray
}

private fun decodeNativeBitmap(packet: ByteArray): Bitmap {
    if (packet.size < 8) throw IOException("ردّ محرك العرض غير مكتمل.")
    val header = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
    val width = header.int
    val height = header.int
    if (width <= 0 || height <= 0) {
        val message = String(packet, 8, packet.size - 8, Charsets.UTF_8)
        throw IOException(message.ifBlank { "تعذر رسم صفحة PDF." })
    }
    val expected = width.toLong() * height.toLong() * 4L
    if (expected != packet.size.toLong() - 8L || expected > Int.MAX_VALUE) {
        throw IOException("أبعاد الصورة الناتجة غير صالحة.")
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(packet, 8, expected.toInt()).order(ByteOrder.LITTLE_ENDIAN))
    return bitmap
}

@Composable
private fun PdfCraftPrototype(
    state: PdfScreenState,
    onOpen: () -> Unit,
    onPassword: (String) -> Unit,
    onPageChange: (Int) -> Unit,
) {
    var password by remember(state.fileName) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("PdfCraft for Android", style = MaterialTheme.typography.headlineMedium)
        Text(
            "نموذج نقل أولي: قراءة PDF وعرض الصفحات عبر محرك Rust؛ بقية أدوات التحرير والميزات المتقدمة لم تُنقل بعد.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Button(onClick = onOpen, enabled = !state.loading) { Text("فتح ملف PDF") }

        if (state.fileName.isNotBlank()) Text(state.fileName, style = MaterialTheme.typography.titleMedium)
        if (state.loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.width(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(state.message)
            }
        } else {
            Text(
                state.message,
                color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }

        if (state.passwordRequired && !state.loading) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("كلمة مرور PDF") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val submitted = password
                    password = ""
                    onPassword(submitted)
                },
                enabled = password.isNotEmpty(),
            ) { Text("فتح المستند") }
        }

        if (state.pageCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { onPageChange(state.pageIndex - 1) }, enabled = state.pageIndex > 0 && !state.loading) {
                    Text("السابقة")
                }
                Text("${state.pageIndex + 1} / ${state.pageCount}")
                Button(onClick = { onPageChange(state.pageIndex + 1) }, enabled = state.pageIndex + 1 < state.pageCount && !state.loading) {
                    Text("التالية")
                }
            }
        }

        state.bitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "صفحة PDF ${state.pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "هذا بناء تجريبي: عرض صفحة واحدة في كل مرة، دون تحرير أو حفظ أو OCR أو تعليقات أو أدوات النماذج. لا يثبت التكافؤ الوظيفي.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}
