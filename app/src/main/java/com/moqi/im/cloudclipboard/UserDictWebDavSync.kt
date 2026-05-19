package com.moqi.im.cloudclipboard

import android.content.Context
import com.moqi.im.engine.MoqiImeSession
import com.moqi.im.moqiAndroidDataDir
import mobilebridge.Mobilebridge
import java.io.File

class UserDictWebDavSync(context: Context) {
    private val appContext = context.applicationContext

    fun sync(): UserDictSnapshotSyncResult {
        val config = CloudClipboardPrefs.loadConfig(appContext)
        if (!CloudClipboardPrefs.isWebDavConfigComplete(config)) {
            error("WebDAV 未配置完整")
        }
        val client = WebDavClient.createOrNull(config)
            ?: error(WebDavUrlPolicy.rejectionMessage())

        val androidDataDir = appContext.moqiAndroidDataDir()
        val runtimeRoot = File(androidDataDir, RIME_APP_DIR).apply { mkdirs() }
        val session = MoqiImeSession(
            guid = Mobilebridge.GUIDRime,
            androidDataDir = androidDataDir.absolutePath
        )
        try {
            val schemeSet = session.currentSchemeSet().ifBlank { DEFAULT_SCHEME_SET }
            require(isSchemeSetDirName(schemeSet)) { "方案集名称无效" }
            val userDir = File(runtimeRoot, schemeSet).apply { mkdirs() }
            val syncRoot = File(userDir, "sync")

            val downloaded = downloadSnapshots(client, syncRoot, schemeSet)
            val syncResult = session.command(RIME_SYNC_COMMAND_ID)
            if (!syncResult.success || !syncResult.handled) {
                error(syncResult.error.ifBlank { "librime 合并用户词库失败" })
            }
            val deviceId = readInstallationId(userDir)
                ?: error("无法读取本机 Rime installation_id")
            val uploaded = uploadSnapshots(client, syncRoot, schemeSet, deviceId)
            return UserDictSnapshotSyncResult(downloaded = downloaded, uploaded = uploaded)
        } finally {
            session.close()
        }
    }

    private fun downloadSnapshots(client: WebDavClient, syncRoot: File, schemeSet: String): Int {
        var downloaded = 0
        client.listUserDictSnapshots(schemeSet).forEach { entry ->
            if (!isSyncDeviceDirName(entry.deviceId) || !isUserDictSnapshotFileName(entry.name)) {
                return@forEach
            }
            val target = File(File(syncRoot, entry.deviceId), entry.name)
            atomicWrite(target, client.downloadUserDictSnapshot(entry))
            downloaded++
        }
        return downloaded
    }

    private fun uploadSnapshots(
        client: WebDavClient,
        syncRoot: File,
        schemeSet: String,
        deviceId: String
    ): Int {
        val deviceDir = File(syncRoot, deviceId)
        val files = waitForLocalSnapshotFiles(deviceDir)
        var uploaded = 0
        files.forEach { file ->
            val data = file.readBytes()
            if (data.isEmpty()) {
                error("本机词库快照为空，已取消上传: ${file.name}")
            }
            client.uploadUserDictSnapshot(schemeSet, deviceId, file.name, data)
            uploaded++
        }
        return uploaded
    }

    private fun waitForLocalSnapshotFiles(deviceDir: File): List<File> {
        repeat(SNAPSHOT_READY_ATTEMPTS) {
            val files = localSnapshotFiles(deviceDir)
            if (files.isEmpty()) {
                Thread.sleep(SNAPSHOT_READY_DELAY_MS)
                return@repeat
            }
            val sizes = files.map { it.length() }
            if (sizes.all { it > 0L }) {
                Thread.sleep(SNAPSHOT_READY_DELAY_MS)
                val refreshed = localSnapshotFiles(deviceDir)
                val refreshedSizes = refreshed.map { it.length() }
                if (refreshed.map { it.name } == files.map { it.name } && refreshedSizes == sizes) {
                    return refreshed
                }
            }
            Thread.sleep(SNAPSHOT_READY_DELAY_MS)
        }
        val files = localSnapshotFiles(deviceDir)
        val empty = files.firstOrNull { it.length() == 0L }
        if (empty != null) {
            error("本机词库快照为空，已取消上传: ${empty.name}")
        }
        return files
    }

    private fun localSnapshotFiles(deviceDir: File): List<File> =
        deviceDir.listFiles { file ->
            file.isFile && isUserDictSnapshotFileName(file.name)
        }.orEmpty().sortedBy { it.name }

    private fun readInstallationId(userDir: File): String? {
        val installation = File(userDir, "installation.yaml")
        if (!installation.isFile) return null
        return installation.readLines()
            .asSequence()
            .map { it.trim() }
            .filter { it.startsWith("installation_id:") }
            .map {
                it.substringAfter("installation_id:")
                    .trim()
                    .trim('"', '\'')
            }
            .firstOrNull { isSyncDeviceDirName(it) }
    }

    private fun atomicWrite(target: File, data: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeBytes(data)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun isUserDictSnapshotFileName(name: String): Boolean {
        if (!name.endsWith(".userdb.txt", ignoreCase = true)) return false
        if (name.any { it == '/' || it == '\\' || it == ':' }) return false
        val lower = name.lowercase()
        val base = lower.removeSuffix(".userdb.txt")
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

    companion object {
        private const val RIME_APP_DIR = "Moqi"
        private const val DEFAULT_SCHEME_SET = "Rime"
        private const val RIME_SYNC_COMMAND_ID = 11
        private const val SNAPSHOT_READY_ATTEMPTS = 10
        private const val SNAPSHOT_READY_DELAY_MS = 200L
    }
}
