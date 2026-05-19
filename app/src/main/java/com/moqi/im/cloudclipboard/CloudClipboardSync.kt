package com.moqi.im.cloudclipboard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CloudClipboardSync(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile
    private var config: CloudClipboardConfig = CloudClipboardPrefs.loadConfig(appContext)

    @Volatile
    private var lastUploadAt: Long = 0L

    @Volatile
    private var lastUploadedHash: String? = null

    @Volatile
    private var isUploadInFlight = false

    private var pendingText: String? = null
    private var debounceJob: Job? = null

    fun reloadConfig() {
        config = CloudClipboardPrefs.loadConfig(appContext)
    }

    fun lastUploadedContentHash(): String? = lastUploadedHash

    fun scheduleUpload(text: String) {
        if (!CloudClipboardPrefs.isConfigComplete(config)) return
        pendingText = text
        launchDebouncedUpload(DEBOUNCE_MS)
    }

    private fun launchDebouncedUpload(delayMs: Long) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(delayMs)
            val toUpload = pendingText ?: return@launch
            pendingText = null
            performUpload(toUpload)
        }
    }

    suspend fun listClips(): List<WebDavClipEntry> = withContext(Dispatchers.IO) {
        val client = clientOrThrow()
        client.listClips()
    }

    suspend fun listClipsForDisplay(
        maxItems: Int = MAX_DISPLAY_ITEMS,
        previewLength: Int = PREVIEW_MAX_LENGTH
    ): List<CloudClipboardDisplayItem> = withContext(Dispatchers.IO) {
        val client = clientOrThrow()
        val entries = client.listClips().take(maxItems)
        coroutineScope {
            entries.map { entry ->
                async {
                    runCatching {
                        val body = client.downloadClip(entry.name).trim()
                        CloudClipboardDisplayItem(
                            name = entry.name,
                            lastModified = entry.lastModified,
                            preview = formatPreview(body, previewLength)
                        )
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
    }

    suspend fun downloadClip(name: String): String = withContext(Dispatchers.IO) {
        val client = clientOrThrow()
        client.downloadClip(name)
    }

    suspend fun deleteClip(name: String) = withContext(Dispatchers.IO) {
        val client = clientOrThrow()
        client.deleteClip(name)
    }

    suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            clientOrThrow().testConnection()
        }
    }

    fun shutdown() {
        debounceJob?.cancel()
        scope.cancel()
    }

    private suspend fun performUpload(text: String) {
        mutex.withLock {
            if (isUploadInFlight) {
                pendingText = text
                return
            }
            val now = System.currentTimeMillis()
            val elapsed = now - lastUploadAt
            if (elapsed < config.minIntervalMs) {
                pendingText = text
                launchDebouncedUpload(config.minIntervalMs - elapsed + 50L)
                return
            }
            val md5 = CloudClipboardGuard.contentMd5(text)
            if (md5 == lastUploadedHash) return
            isUploadInFlight = true
            try {
                val client = WebDavClient.createOrNull(config)
                if (client == null) {
                    Log.w(TAG, "Upload skipped: invalid WebDAV config ${config.baseUrl}")
                    return
                }
                val filename = CloudClipboardGuard.buildFilename(text)
                client.uploadClip(filename, text)
                lastUploadedHash = md5
                lastUploadAt = System.currentTimeMillis()
                Log.d(TAG, "Uploaded cloud clip: $filename")
            } catch (e: Exception) {
                Log.w(TAG, "Cloud clip upload failed", e)
                lastUploadAt = System.currentTimeMillis()
            } finally {
                isUploadInFlight = false
                val retry = pendingText
                if (retry != null && retry != text) {
                    pendingText = null
                    scheduleUpload(retry)
                }
            }
        }
    }

    private fun clientOrThrow(): WebDavClient {
        reloadConfig()
        if (!CloudClipboardPrefs.isConfigComplete(config)) {
            throw IllegalStateException("云剪贴板未配置完整")
        }
        return WebDavClient.createOrNull(config)
            ?: throw IllegalStateException(WebDavUrlPolicy.rejectionMessage())
    }

    companion object {
        private const val TAG = "CloudClipboardSync"
        private const val DEBOUNCE_MS = 500L
        private const val MAX_DISPLAY_ITEMS = 40
        private const val PREVIEW_MAX_LENGTH = 240

        private fun formatPreview(text: String, maxLength: Int): String {
            if (text.isEmpty()) return "（空）"
            val singleLine = text.replace(Regex("\\s+"), " ").trim()
            return if (singleLine.length <= maxLength) {
                singleLine
            } else {
                singleLine.take(maxLength) + "…"
            }
        }
    }
}
