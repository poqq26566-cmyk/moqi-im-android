package com.moqi.im.cloudclipboard

/** WebDAV 地址：不修改用户填写的 http/https，仅做 trim。 */
object WebDavUrlPolicy {

    fun normalizeBaseUrl(raw: String): String =
        raw.trim().trimEnd('/')

    fun isAllowed(baseUrl: String): Boolean =
        baseUrl.startsWith("https://", ignoreCase = true) ||
            baseUrl.startsWith("http://", ignoreCase = true)

    fun rejectionMessage(): String =
        "WebDAV 地址须以 http:// 或 https:// 开头"
}
