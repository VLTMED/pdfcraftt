package ai.storyteller.pdfcraft.android

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Engine-backed viewer (phase 1). Pages come from the Rust engine's `page_render` tool.
 * Opening goes through the system picker; the content is copied into the engine's confined
 * work folder because the engine works with paths, not content:// URIs.
 */
class ViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = File(filesDir, "work").apply { mkdirs() }
        val initial: Uri? = intent?.data
        setContent { MaterialTheme { ViewerScreen(root, initial) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerScreen(root: File, initial: Uri?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { NativeEngine.Session(root.absolutePath) }
    DisposableEffect(Unit) { onDispose { session.close() } }

    var doc by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var pages by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    fun render(d: Int, p: Int) {
        scope.launch {
            busy = true
            error = null
            runCatching {
                withContext(Dispatchers.Default) {
                    session.image("page_render", JSONObject().put("doc", d).put("page", p).put("dpi", 144))
                }
            }.onSuccess { image = it; page = p; zoom = 1f; panX = 0f; panY = 0f }
                .onFailure { error = it.message }
            busy = false
        }
    }

    fun open(uri: Uri) {
        scope.launch {
            busy = true
            error = null
            runCatching {
                withContext(Dispatchers.Default) {
                    val file = File(root, "doc_${System.nanoTime()}.pdf")
                    val input = context.contentResolver.openInputStream(uri) ?: error("cannot read the selected file")
                    input.use { i -> file.outputStream().use { o -> i.copyTo(o) } }
                    session.json("doc_open", JSONObject().put("path", file.name))
                }
            }.onSuccess {
                doc = it.getInt("doc")
                name = it.optString("name")
                pages = it.getInt("pages")
                render(doc, 1)
            }.onFailure { error = it.message }
            busy = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::open) }
    DisposableEffect(initial) { initial?.let(::open); onDispose { } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (doc == 0) "PdfCraft" else name) },
                actions = {
                    TextButton(onClick = { picker.launch(arrayOf("application/pdf")) }) { Text("Open") }
                },
            )
        },
        bottomBar = {
            if (doc != 0) {
                BottomAppBar {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { render(doc, page - 1) }, enabled = page > 1 && !busy) { Text("Previous") }
                        Text("$page / $pages")
                        TextButton(onClick = { render(doc, page + 1) }, enabled = page < pages && !busy) { Text("Next") }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            image?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page $page",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, z, _ ->
                                zoom = (zoom * z).coerceIn(1f, 6f)
                                panX += pan.x
                                panY += pan.y
                            }
                        }
                        .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = panX, translationY = panY),
                )
            }
            if (busy) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            if (doc == 0 && !busy && error == null) Text("Open a PDF to begin")
        }
    }
}
