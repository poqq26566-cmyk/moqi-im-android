package com.moqi.im.cloudclipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import java.security.MessageDigest

object CloudClipboardGuard {
    private const val MIN_TEXT_LENGTH = 2
    private const val MAX_TEXT_LENGTH = 32_768

    fun extractPlainText(context: Context, clip: ClipData): String? {
        if (clip.itemCount <= 0) return null
        val description = clip.description
        if (isSensitive(description)) return null
        val item = clip.getItemAt(0)
        val text = item.coerceToText(context)?.toString()?.trim() ?: return null
        if (!isUploadableText(text)) return null
        return text
    }

    fun shouldUpload(
        config: CloudClipboardConfig,
        text: String,
        uploadContextActive: Boolean,
        isApplyingRemoteClip: Boolean,
        lastUploadedHash: String?
    ): Boolean {
        if (!CloudClipboardPrefs.isConfigComplete(config)) return false
        if (!uploadContextActive) return false
        if (isApplyingRemoteClip) return false
        if (!isUploadableText(text)) return false
        val md5 = contentMd5(text)
        if (md5 == lastUploadedHash) return false
        return true
    }

    /** 内容 MD5（32 位小写十六进制），用作文件名实现 WebDAV 侧去重。 */
    fun contentMd5(text: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun buildFilename(text: String): String = "${contentMd5(text)}.txt"

    private fun isSensitive(description: ClipDescription): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val extras = description.extras
            if (extras != null && extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)) {
                return true
            }
        }
        return false
    }

    private fun isUploadableText(text: String): Boolean {
        if (text.length < MIN_TEXT_LENGTH || text.length > MAX_TEXT_LENGTH) return false
        if (text.all { it.isWhitespace() }) return false
        if (looksLikeMaskedSecret(text)) return false
        return true
    }

    private fun looksLikeMaskedSecret(text: String): Boolean {
        if (text.length < 4) return false
        val maskChars = setOf('*', '•', '●', '·')
        val maskedCount = text.count { it in maskChars }
        return maskedCount >= text.length * 0.8
    }
}
