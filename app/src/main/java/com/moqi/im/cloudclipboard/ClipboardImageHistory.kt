package com.moqi.im.cloudclipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * 本地剪贴板图片历史。
 *
 * 监听系统剪贴板（[ClipboardManager.OnPrimaryClipChangedListener]），当检测到复制的内容
 * 是图片（image/* MIME 或带图片 [Uri] 的 [ClipData.Item]）时，把图片落盘到应用私有缓存目录
 * （通过 [FileProvider] 暴露为可被目标输入框读取的 content:// Uri），并维护一份历史列表
 * （SharedPreferences 持久化，最多保留 [MAX_ITEMS] 张，超出自动清理最旧的文件）。
 *
 * 键盘面板（[com.moqi.im.keyboard.CloudClipboardPanelView]）展示这份历史的缩略图，
 * 点击后由 [com.moqi.im.core.MoqiInputMethodService] 通过 InputConnection#commitContent
 * 把图片提交给当前输入框（需要目标 App 支持接收富媒体输入，否则会静默失败并提示用户）。
 */
class ClipboardImageHistory(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val clipboardManager =
        appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    data class Entry(
        val fileName: String,
        val timestamp: Long,
        val mimeType: String
    ) {
        fun file(context: Context): File = File(imageDir(context), fileName)
        fun contentUri(context: Context): Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.imagefileprovider",
            file(context)
        )
    }

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        runCatching { onPrimaryClipChanged() }
            .onFailure { Log.w(TAG, "handle clipboard change failed", it) }
    }

    fun start() {
        clipboardManager?.addPrimaryClipChangedListener(listener)
    }

    fun stop() {
        clipboardManager?.removePrimaryClipChangedListener(listener)
    }

    fun listEntries(): List<Entry> = loadEntries()

    fun delete(entry: Entry) {
        runCatching { entry.file(appContext).delete() }
        val remaining = loadEntries().filterNot { it.fileName == entry.fileName }
        saveEntries(remaining)
    }

    fun clearAll() {
        loadEntries().forEach { runCatching { it.file(appContext).delete() } }
        saveEntries(emptyList())
    }

    private fun onPrimaryClipChanged() {
        val clip = clipboardManager?.primaryClip ?: return
        if (clip.itemCount <= 0) return
        val item = clip.getItemAt(0)
        val uri = item.uri ?: return
        val description = clip.description
        val mimeType = (0 until description.mimeTypeCount)
            .map { description.getMimeType(it) }
            .firstOrNull { it.startsWith("image/") }
            ?: return
        saveImageFromUri(uri, mimeType)
    }

    private fun saveImageFromUri(sourceUri: Uri, mimeType: String) {
        val resolver: ContentResolver = appContext.contentResolver
        val bitmap = runCatching {
            resolver.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return
        val scaled = downscale(bitmap, MAX_DIMENSION)
        val dir = imageDir(appContext).apply { mkdirs() }
        val fileName = "clip_${System.currentTimeMillis()}.png"
        val outFile = File(dir, fileName)
        runCatching {
            FileOutputStream(outFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }.onFailure {
            Log.w(TAG, "save clipboard image failed", it)
            return
        }
        val entry = Entry(fileName, System.currentTimeMillis(), "image/png")
        val updated = (listOf(entry) + loadEntries()).distinctBy { it.fileName }
        val kept = updated.take(MAX_ITEMS)
        val dropped = updated.drop(MAX_ITEMS)
        dropped.forEach { runCatching { it.file(appContext).delete() } }
        saveEntries(kept)
    }

    private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxOf(width, height)
        val newWidth = (width * scale).toInt().coerceAtLeast(1)
        val newHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun loadEntries(): List<Entry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                Entry(
                    fileName = obj.optString("fileName"),
                    timestamp = obj.optLong("timestamp"),
                    mimeType = obj.optString("mimeType", "image/png")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveEntries(entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("fileName", entry.fileName)
                    .put("timestamp", entry.timestamp)
                    .put("mimeType", entry.mimeType)
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    companion object {
        private const val TAG = "ClipboardImageHistory"
        private const val PREFS_NAME = "clipboard_image_history"
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ITEMS = 20
        private const val MAX_DIMENSION = 1280

        fun imageDir(context: Context): File =
            File(context.cacheDir, "clipboard_images")
    }
}
