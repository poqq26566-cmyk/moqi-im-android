package com.moqi.im.cloudclipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

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
        val hash = contentHash(text)
        if (hash == lastUploadedHash) return false
        return true
    }

    fun contentHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun buildFilename(text: String): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
        val hash = contentHash(text).take(8)
        return "clip_${stamp}_$hash.txt"
    }

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
