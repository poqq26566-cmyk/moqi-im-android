package com.moqi.im.cloudclipboard

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.moqi.im.theme.ThemePalette

object CloudClipboardPrefs {
    const val KEY_ENABLED = "cloud_clipboard_enabled"
    const val KEY_URL = "cloud_clipboard_url"
    const val KEY_USERNAME = "cloud_clipboard_username"
    const val KEY_REMOTE_PATH = "cloud_clipboard_remote_path"
    const val KEY_MIN_INTERVAL_SEC = "cloud_clipboard_min_interval_sec"

    private const val SECURE_PREFS_NAME = "moqi_cloud_clipboard_secure"
    private const val SECURE_KEY_PASSWORD = "cloud_clipboard_password"
    private const val DEFAULT_REMOTE_PATH = "/moqi-input-method/"
    private const val DEFAULT_INTERVAL_SEC = 10

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun loadConfig(context: Context): CloudClipboardConfig {
        val p = prefs(context)
        return CloudClipboardConfig(
            enabled = p.getBoolean(KEY_ENABLED, false),
            baseUrl = WebDavUrlPolicy.normalizeBaseUrl(p.getString(KEY_URL, "").orEmpty()),
            username = p.getString(KEY_USERNAME, "").orEmpty().trim(),
            password = readPassword(context),
            remotePath = normalizeRemotePath(
                p.getString(KEY_REMOTE_PATH, DEFAULT_REMOTE_PATH) ?: DEFAULT_REMOTE_PATH
            ),
            minIntervalMs = (p.getString(KEY_MIN_INTERVAL_SEC, DEFAULT_INTERVAL_SEC.toString())
                ?.toIntOrNull() ?: DEFAULT_INTERVAL_SEC) * 1000L
        )
    }

    fun hasPassword(context: Context): Boolean = readPassword(context).isNotEmpty()

    fun savePassword(context: Context, password: String) {
        securePrefs(context).edit().putString(SECURE_KEY_PASSWORD, password).apply()
    }

    fun clearPassword(context: Context) {
        securePrefs(context).edit().remove(SECURE_KEY_PASSWORD).apply()
    }

    private fun readPassword(context: Context): String =
        securePrefs(context).getString(SECURE_KEY_PASSWORD, "").orEmpty()

    private fun prefs(context: Context) =
        context.getSharedPreferences(ThemePalette.PREFS_NAME, Context.MODE_PRIVATE)

    private fun securePrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        SECURE_PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun normalizeRemotePath(raw: String): String {
        var path = raw.trim()
        if (path.isEmpty()) path = DEFAULT_REMOTE_PATH
        if (!path.startsWith("/")) path = "/$path"
        if (!path.endsWith("/")) path = "$path/"
        return path
    }

    fun isConfigComplete(config: CloudClipboardConfig): Boolean =
        config.enabled &&
            config.baseUrl.isNotBlank() &&
            config.username.isNotBlank() &&
            config.password.isNotBlank()
}
