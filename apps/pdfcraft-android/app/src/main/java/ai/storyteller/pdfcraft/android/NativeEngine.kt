package ai.storyteller.pdfcraft.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thin wrapper over the Rust engine. There is no PDF logic in Kotlin: every operation is one
 * of the engine's JSON tools (`doc_open`, `page_render`, `text_find`, ...), see [tools].
 */
object NativeEngine {
    init { System.loadLibrary("pdfcraft_android_native") }

    private external fun nativeCreate(root: String): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeCall(handle: Long, tool: String, args: String): String
    private external fun nativeTools(): String

    /** The engine's full tool catalogue (name, title, description, inputSchema, ...). */
    fun tools(): JSONArray = JSONArray(nativeTools())

    sealed interface Part {
        data class Data(val json: Any?) : Part
        data class Image(val bitmap: Bitmap) : Part
    }

    class ToolException(message: String) : Exception(message)

    /** One engine thread confined to [root]; the engine reads and writes nothing outside it. */
    class Session(root: String) : AutoCloseable {
        private val handle = nativeCreate(root).also { check(it != 0L) { "The PDF engine could not start in $root" } }

        override fun close() = nativeDestroy(handle)

        /** Runs a tool; throws [ToolException] with the engine's message when it fails. */
        fun call(tool: String, args: JSONObject = JSONObject()): List<Part> {
            val r = JSONObject(nativeCall(handle, tool, args.toString()))
            if (!r.optBoolean("ok")) throw ToolException(r.optString("error", "failed"))
            val content = r.getJSONArray("content")
            return (0 until content.length()).map { i ->
                val c = content.getJSONObject(i)
                if (c.getString("type") == "png") {
                    val bytes = Base64.decode(c.getString("data"), Base64.DEFAULT)
                    Part.Image(
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            ?: throw ToolException("the page image could not be decoded"),
                    )
                } else Part.Data(c.opt("value"))
            }
        }

        fun json(tool: String, args: JSONObject = JSONObject()): JSONObject =
            (call(tool, args).first { it is Part.Data } as Part.Data).json as JSONObject

        fun image(tool: String, args: JSONObject = JSONObject()): Bitmap =
            (call(tool, args).first { it is Part.Image } as Part.Image).bitmap
    }
}
