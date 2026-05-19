package com.moqi.im.cloudclipboard

import android.util.Log
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class WebDavClient(private val config: CloudClipboardConfig) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val authHeader = Credentials.basic(config.username, config.password)

    @Throws(IOException::class)
    fun testConnection() {
        ensureRemoteDirectory()
    }

    @Throws(IOException::class)
    fun listClips(): List<WebDavClipEntry> {
        ensureRemoteDirectory()
        val response = propfind(config.remoteDirectoryUrl(), depth = 1)
        if (!response.isSuccessful) {
            throw IOException("PROPFIND failed: HTTP ${response.code}")
        }
        val body = response.body?.string().orEmpty()
        response.close()
        return parsePropfind(body)
            .filter { it.name.endsWith(".txt", ignoreCase = true) }
            .sortedByDescending { it.lastModified }
    }

    @Throws(IOException::class)
    fun uploadClip(filename: String, text: String) {
        ensureRemoteDirectory()
        val url = fileUrl(filename)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .put(text.toRequestBody("text/plain; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("PUT failed: HTTP ${response.code}")
            }
        }
    }

    @Throws(IOException::class)
    fun downloadClip(name: String): String {
        val request = Request.Builder()
            .url(fileUrl(name))
            .header("Authorization", authHeader)
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GET failed: HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

  /**
     * 先验证 WebDAV 根路径，再确保远程子目录存在（兼容飞牛等需先访问 / 的 NAS）。
     */
    @Throws(IOException::class)
    private fun ensureRemoteDirectory() {
        val rootUrl = rootUrl()
        propfind(rootUrl, depth = 0).use { rootResp ->
            when (rootResp.code) {
                401 -> throw IOException(
                    "认证失败：请检查用户名和密码。飞牛须使用「可见文件夹范围」所属账号登录"
                )
                in 200..299 -> Unit
                else -> throw IOException(
                    "无法访问 WebDAV 根路径 HTTP ${rootResp.code}。" +
                        "飞牛地址请填 http://192.168.x.x:5005/（注意末尾 /）"
                )
            }
        }

        val dirUrl = config.remoteDirectoryUrl().trimEnd('/') + "/"
        if (dirUrl.equals(rootUrl, ignoreCase = true)) return

        if (directoryExists(dirUrl)) return

        if (tryMkcol(dirUrl) && directoryExists(dirUrl)) return

        throw IOException(
            "远程目录 ${config.remotePath} 不存在且无法自动创建。" +
                "请在飞牛「文件管理」中手动新建该文件夹，或把远程目录改为 /"
        )
    }

    private fun rootUrl(): String = config.baseUrl.trimEnd('/') + "/"

    private fun directoryExists(url: String): Boolean =
        propfind(url, depth = 0).use { it.code in 200..299 }

    private fun tryMkcol(url: String): Boolean =
        runCatching {
            mkcol(url)
            true
        }.getOrDefault(false)

    private fun fileUrl(filename: String): String {
        val dir = config.remoteDirectoryUrl().trimEnd('/')
        val safeName = filename.trimStart('/')
        return "$dir/$safeName"
    }

    private fun propfind(url: String, depth: Int) =
        http.newCall(
            Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .header("Depth", depth.toString())
                .method(
                    "PROPFIND",
                    PROPFIND_BODY.toRequestBody("text/xml; charset=utf-8".toMediaType())
                )
                .build()
        ).execute()

    private fun mkcol(url: String) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .method("MKCOL", ByteArray(0).toRequestBody(null))
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code !in 200..299 && response.code != 405) {
                throw IOException("MKCOL failed: HTTP ${response.code}")
            }
        }
    }

    private fun parsePropfind(xml: String): List<WebDavClipEntry> {
        if (xml.isBlank()) return emptyList()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())

        val results = mutableListOf<WebDavClipEntry>()
        var inResponse = false
        var href: String? = null
        var lastModified: Long = 0L

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    when {
                        name.equals("response", ignoreCase = true) -> {
                            inResponse = true
                            href = null
                            lastModified = 0L
                        }
                        inResponse && name.equals("href", ignoreCase = true) -> {
                            href = parser.nextText().trim()
                        }
                        inResponse && name.equals("getlastmodified", ignoreCase = true) -> {
                            lastModified = parseHttpDate(parser.nextText())
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("response", ignoreCase = true) && inResponse) {
                        val entryName = href?.let(::nameFromHref)
                        if (!entryName.isNullOrBlank() && entryName != "." && entryName != "..") {
                            results += WebDavClipEntry(entryName, lastModified)
                        }
                        inResponse = false
                    }
                }
            }
            event = parser.next()
        }
        return results
    }

    private fun nameFromHref(href: String): String? {
        val decoded = href.trim()
        if (decoded.isEmpty()) return null
        val segment = decoded.trimEnd('/').substringAfterLast('/')
        return segment.takeIf { it.isNotBlank() }
    }

    private fun parseHttpDate(raw: String): Long {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss 'GMT'"
        )
        for (pattern in formats) {
            runCatching {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("GMT")
                return sdf.parse(raw.trim())?.time ?: 0L
            }
        }
        return 0L
    }

    companion object {
        private const val TAG = "WebDavClient"
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:getlastmodified/>
                <d:displayname/>
              </d:prop>
            </d:propfind>"""

        fun createOrNull(config: CloudClipboardConfig): WebDavClient? {
            if (!CloudClipboardPrefs.isConfigComplete(config)) return null
            if (!WebDavUrlPolicy.isAllowed(config.baseUrl)) {
                Log.w(TAG, "WebDAV URL not allowed: ${config.baseUrl}")
                return null
            }
            return WebDavClient(config)
        }
    }
}
