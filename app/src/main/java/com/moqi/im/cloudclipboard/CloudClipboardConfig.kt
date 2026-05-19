package com.moqi.im.cloudclipboard

data class CloudClipboardConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val username: String,
    val password: String,
    val remotePath: String,
    val minIntervalMs: Long
) {
    fun remoteDirectoryUrl(): String {
        val base = baseUrl.trimEnd('/')
        val path = remotePath.trimStart('/')
        return "$base/$path"
    }
}
