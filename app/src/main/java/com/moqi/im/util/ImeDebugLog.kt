package com.moqi.im.util

import android.content.Context
import android.util.Log
import com.moqi.im.theme.ThemePalette
import mobilebridge.Mobilebridge

object ImeDebugLog {
    const val PREF_KEY = "debug_logging"

    @Volatile
    var enabled: Boolean = false
        private set

    fun refresh(context: Context): Boolean {
        val next = context
            .getSharedPreferences(ThemePalette.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_KEY, false)
        enabled = next
        runCatching {
            Mobilebridge.setDebugLoggingEnabled(next)
        }.onFailure { error ->
            Log.w(TAG, "sync native debug logging failed", error)
        }
        return next
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        runCatching {
            Mobilebridge.setDebugLoggingEnabled(enabled)
        }.onFailure { error ->
            Log.w(TAG, "sync native debug logging failed", error)
        }
    }

    fun d(tag: String, message: () -> String) {
        if (enabled) {
            Log.d(tag, message())
        }
    }

    fun duration(tag: String, operation: String, startNanos: Long, slowThresholdMs: Long, details: () -> String = { "" }) {
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
        if (elapsedMs >= slowThresholdMs) {
            Log.w(tag, "$operation took=${elapsedMs}ms ${details()}")
        } else {
            d(tag) { "$operation took=${elapsedMs}ms ${details()}" }
        }
    }

    private const val TAG = "ImeDebugLog"
}
