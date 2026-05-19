package com.moqi.im.cloudclipboard

import android.util.Log
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
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
        val response = propfind(config.clipDirectoryUrl(), depth = 1)
        if (!response.isSuccessful) {
            throw IOException("PROPFIND failed: HTTP ${response.code}")
        }
        val body = response.body?.string().orEmpty()
        response.close()
        return parsePropfind(body)
            .filter { isClipFileName(it.name) }
            .distinctBy { it.name }
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
    fun deleteClip(name: String) {
        val request = Request.Builder()
            .url(fileUrl(name))
            .header("Authorization", authHeader)
            .delete()
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code !in 200..299 && response.code != 404) {
                throw IOException("DELETE failed: HTTP ${response.code}")
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

    @Throws(IOException::class)
    fun listUserDictSnapshots(schemeSet: String): List<UserDictSnapshotEntry> {
        requireValidSchemeSet(schemeSet)
        ensureRemoteSchemeSetDictDirectory(schemeSet)
        val response = propfind(dictSchemeSetDirectoryUrl(schemeSet), depth = 1)
        if (!response.isSuccessful) {
            throw IOException("PROPFIND dict failed: HTTP ${response.code}")
        }
        val body = response.body?.string().orEmpty()
        response.close()
        return parsePropfind(body)
            .filter { isSyncDeviceDirName(it.name) }
            .distinctBy { it.name }
            .flatMap { device ->
                val deviceUrl = dictDeviceDirectoryUrl(schemeSet, device.name)
                runCatching {
                    propfind(deviceUrl, depth = 1).use { deviceResp ->
                        if (!deviceResp.isSuccessful) return@runCatching emptyList()
                        parsePropfind(deviceResp.body?.string().orEmpty())
                            .filter { isUserDictSnapshotFileName(it.name) }
                            .distinctBy { it.name }
                            .map { file ->
                                UserDictSnapshotEntry(
                                    schemeSet = schemeSet,
                                    deviceId = device.name,
                                    name = file.name,
                                    lastModified = file.lastModified
                                )
                            }
                    }
                }.getOrDefault(emptyList())
            }
    }

    @Throws(IOException::class)
    fun downloadUserDictSnapshot(entry: UserDictSnapshotEntry): ByteArray {
        requireValidSchemeSet(entry.schemeSet)
        requireValidDeviceId(entry.deviceId)
        requireValidSnapshotName(entry.name)
        val request = Request.Builder()
            .url(dictSnapshotUrl(entry.schemeSet, entry.deviceId, entry.name))
            .header("Authorization", authHeader)
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GET user dict snapshot failed: HTTP ${response.code}")
            }
            return response.body?.bytes() ?: ByteArray(0)
        }
    }

    @Throws(IOException::class)
    fun uploadUserDictSnapshot(schemeSet: String, deviceId: String, name: String, data: ByteArray) {
        requireValidSchemeSet(schemeSet)
        requireValidDeviceId(deviceId)
        requireValidSnapshotName(name)
        if (data.isEmpty()) {
            throw IOException("本机词库快照为空，已取消上传: $name")
        }
        ensureRemoteSchemeSetDictDirectory(schemeSet)
        val deviceUrl = dictDeviceDirectoryUrl(schemeSet, deviceId)
        if (!directoryExists(deviceUrl) && !tryMkcol(deviceUrl)) {
            throw IOException("词库同步设备目录无法创建")
        }
        val snapshotUrl = dictSnapshotUrl(schemeSet, deviceId, name)
        val request = Request.Builder()
            .url(snapshotUrl)
            .header("Authorization", authHeader)
            .put(fixedLengthUserDictBody(data))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("PUT user dict snapshot failed: HTTP ${response.code}")
            }
        }
        verifyUploadedUserDictSnapshot(snapshotUrl, name, data.size)
    }

    /**
     * 先验证 WebDAV 根路径，再确保远程子目录存在（兼容飞牛等需先访问 / 的 NAS）。
     */
    @Throws(IOException::class)
    private fun ensureRemoteDirectory() {
        ensureSettingsRoot()

        val clipUrl = config.clipDirectoryUrl().trimEnd('/') + "/"
        if (directoryExists(clipUrl)) return

        if (tryMkcol(clipUrl) && directoryExists(clipUrl)) return

        throw IOException(
            "剪贴板目录 ${MoqiWebDavPaths.CLIP_DIR}/ 无法创建。" +
                "请在 ${config.settingsRootPath} 下手动新建 clip 文件夹"
        )
    }

    @Throws(IOException::class)
    private fun ensureRemoteDictDirectory() {
        ensureSettingsRoot()
        val dictUrl = config.dictDirectoryUrl().trimEnd('/') + "/"
        if (directoryExists(dictUrl)) return
        if (tryMkcol(dictUrl) && directoryExists(dictUrl)) return
        throw IOException(
            "词库同步目录 ${MoqiWebDavPaths.DICT_DIR}/ 无法创建。" +
                "请在 ${config.settingsRootPath} 下手动新建 dict 文件夹"
        )
    }

    @Throws(IOException::class)
    private fun ensureRemoteSchemeSetDictDirectory(schemeSet: String) {
        requireValidSchemeSet(schemeSet)
        ensureRemoteDictDirectory()
        val schemeSetUrl = dictSchemeSetDirectoryUrl(schemeSet)
        if (directoryExists(schemeSetUrl)) return
        if (tryMkcol(schemeSetUrl) && directoryExists(schemeSetUrl)) return
        throw IOException("词库同步方案集目录 $schemeSet 无法创建")
    }

    @Throws(IOException::class)
    private fun ensureSettingsRoot() {
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

        val settingsRoot = config.settingsRootUrl()
        if (!directoryExists(settingsRoot)) {
            if (!tryMkcol(settingsRoot) || !directoryExists(settingsRoot)) {
                throw IOException(
                    "设置目录 ${config.settingsRootPath} 不存在且无法自动创建。" +
                        "请在 NAS 文件管理中手动新建，或把设置目录改为 /"
                )
            }
        }
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
        val dir = config.clipDirectoryUrl().trimEnd('/')
        val safeName = filename.trimStart('/')
        return "$dir/$safeName"
    }

    private fun dictSchemeSetDirectoryUrl(schemeSet: String): String {
        val dir = config.dictDirectoryUrl().trimEnd('/')
        return "$dir/${encodePathSegment(schemeSet)}/"
    }

    private fun dictDeviceDirectoryUrl(schemeSet: String, deviceId: String): String {
        val dir = dictSchemeSetDirectoryUrl(schemeSet).trimEnd('/')
        return "$dir/${encodePathSegment(deviceId)}/"
    }

    private fun dictSnapshotUrl(schemeSet: String, deviceId: String, name: String): String {
        val dir = dictDeviceDirectoryUrl(schemeSet, deviceId).trimEnd('/')
        return "$dir/${encodePathSegment(name)}"
    }

    private fun fixedLengthUserDictBody(data: ByteArray): RequestBody =
        object : RequestBody() {
            override fun contentType() = "text/plain; charset=utf-8".toMediaType()

            override fun contentLength(): Long = data.size.toLong()

            override fun writeTo(sink: BufferedSink) {
                sink.write(data)
            }
        }

    @Throws(IOException::class)
    private fun verifyUploadedUserDictSnapshot(url: String, name: String, expectedSize: Int) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GET uploaded user dict snapshot failed: HTTP ${response.code}")
            }
            val actualSize = response.body?.bytes()?.size ?: 0
            if (actualSize != expectedSize) {
                throw IOException("词库快照上传后大小不一致: $name，本地 $expectedSize，远端 $actualSize")
            }
        }
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
        val decoded = runCatching {
            URLDecoder.decode(href.trim(), StandardCharsets.UTF_8.name())
        }.getOrDefault(href.trim())
        if (decoded.isEmpty()) return null
        val segment = decoded.trimEnd('/').substringAfterLast('/')
        return segment.takeIf { it.isNotBlank() }
    }

    private fun isClipFileName(name: String): Boolean {
        if (!name.endsWith(".txt", ignoreCase = true)) return false
        if (name.contains('/') || name.contains('\\')) return false
        val base = name.dropLast(4)
        if (base.length == 32 && base.all { it in '0'..'9' || it in 'a'..'f' }) return true
        return name.startsWith("clip_", ignoreCase = true)
    }

    private fun isUserDictSnapshotFileName(name: String): Boolean {
        if (!name.endsWith(".userdb.txt", ignoreCase = true)) return false
        if (name.any { it == '/' || it == '\\' || it == ':' }) return false
        val base = name.removeSuffix(".userdb.txt")
        return base.isNotBlank() && base != "." && base != ".."
    }

    private fun isSyncDeviceDirName(name: String): Boolean {
        if (name.isBlank() || name == "." || name == ".." || name.length > 128) return false
        return name.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
    }

    private fun isSchemeSetDirName(name: String): Boolean {
        if (name.isBlank() || name == "." || name == ".." || name.length > 128) return false
        return name.none { it.code < 0x20 || it == '/' || it == '\\' || it == ':' }
    }

    private fun requireValidSnapshotName(name: String) {
        require(isUserDictSnapshotFileName(name)) { "invalid user dict snapshot filename" }
    }

    private fun requireValidDeviceId(deviceId: String) {
        require(isSyncDeviceDirName(deviceId)) { "invalid sync device id" }
    }

    private fun requireValidSchemeSet(schemeSet: String) {
        require(isSchemeSetDirName(schemeSet)) { "invalid scheme set name" }
    }

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

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
            if (!CloudClipboardPrefs.isWebDavConfigComplete(config)) return null
            if (!WebDavUrlPolicy.isAllowed(config.baseUrl)) {
                Log.w(TAG, "WebDAV URL not allowed: ${config.baseUrl}")
                return null
            }
            return WebDavClient(config)
        }
    }
}
