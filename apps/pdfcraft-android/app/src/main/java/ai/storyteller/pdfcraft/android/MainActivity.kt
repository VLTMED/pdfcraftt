package ai.storyteller.pdfcraft.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private data class PdfScreenState(
    val fileName: String = "",
    val message: String = "افتح ملف PDF لفحص اتصال Kotlin بمحرك Rust.",
    val loading: Boolean = false,
    val passwordRequired: Boolean = false,
    val error: Boolean = false,
)

class MainActivity : ComponentActivity() {
    private val screenState = mutableStateOf(PdfScreenState())
    private var pendingUri: Uri? = null
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
                    )
                }
            }
        }
    }

    private fun inspect(uri: Uri, password: String) {
        val fileName = displayName(uri)
        screenState.value = PdfScreenState(fileName = fileName, message = "جارٍ فحص الملف…", loading = true)

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("تعذر قراءة الملف المحدد.")
                    try {
                        NativePdf.inspectPdf(bytes, password)
                    } finally {
                        bytes.fill(0)
                    }
                }
                when {
                    response == "ERR|NEEDS_PASSWORD" -> screenState.value = PdfScreenState(
                        fileName = fileName,
                        message = "هذا المستند محمي بكلمة مرور. أدخلها لفحص الملف.",
                        passwordRequired = true,
                    )
                    response == "ERR|WRONG_PASSWORD" -> screenState.value = PdfScreenState(
                        fileName = fileName,
                        message = "كلمة المرور غير صحيحة. حاول مرة أخرى.",
                        passwordRequired = true,
                        error = true,
                    )
                    response.startsWith("OK|") -> {
                        val fields = response.split('|')
                        val version = fields.getOrNull(1) ?: "?"
                        val pages = fields.getOrNull(2) ?: "?"
                        screenState.value = PdfScreenState(
                            fileName = fileName,
                            message = "نجح فحص Rust: PDF $version، عدد الصفحات $pages.",
                        )
                    }
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
}

@Composable
private fun PdfCraftPrototype(
    state: PdfScreenState,
    onOpen: () -> Unit,
    onPassword: (String) -> Unit,
) {
    var password by remember(state.fileName) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("PdfCraft for Android", style = MaterialTheme.typography.headlineMedium)
        Text(
            "نموذج تكامل أولي — لا يعرض الصفحات ولا يتضمن بعد أدوات PdfCraft المكتبي.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        Button(onClick = onOpen, enabled = !state.loading) {
            Text("فتح ملف PDF")
        }

        if (state.loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.width(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(state.message)
            }
        } else {
            if (state.fileName.isNotBlank()) {
                Text(state.fileName, style = MaterialTheme.typography.titleMedium)
            }
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
            ) {
                Text("فك الحماية وفحص الملف")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "هذه الشريحة تتحقق من فتح المستند وعدّ صفحاته عبر Rust/JNI فقط. لا تعتبر إصداراً مكتملاً أو متكافئاً وظيفياً.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}
