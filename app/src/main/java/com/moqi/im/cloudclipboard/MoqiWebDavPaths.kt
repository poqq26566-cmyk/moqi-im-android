package com.moqi.im.cloudclipboard

/**
 * 墨奇 WebDAV 远程目录约定（与设置里「墨奇设置目录」对应）：
 *
 * ```
 * {设置目录}/
 *   clip/     # 云剪贴板 .txt
 *   dict/     # 用户词库（预留）
 * ```
 */
object MoqiWebDavPaths {
    const val DEFAULT_SETTINGS_ROOT = "/moqi-input-method/"
    const val CLIP_DIR = "clip"
    const val DICT_DIR = "dict"

    fun clipPathUnder(settingsRoot: String): String {
        val root = normalizeDir(settingsRoot)
        return "$root$CLIP_DIR/"
    }

    fun dictPathUnder(settingsRoot: String): String =
        "${normalizeDir(settingsRoot)}$DICT_DIR/"

    fun normalizeDir(raw: String): String {
        var path = raw.trim()
        if (path.isEmpty()) path = DEFAULT_SETTINGS_ROOT
        if (!path.startsWith("/")) path = "/$path"
        if (!path.endsWith("/")) path = "$path/"
        return path
    }
}
