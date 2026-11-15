// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.download

/**
 * 下载文件存储。
 *
 * 布局：`<filesDir>/downloads/<gid>/manifest.json` + `<gid>/001.jpg`。
 * 放在 app 私有目录：无需存储权限、卸载即删。
 */
expect object DownloadStore {

    /** 读取全部画廊清单（启动时重建队列用）。 */
    fun listManifests(): List<DownloadManifest>

    fun readManifest(gid: Long): DownloadManifest?

    /** 原子写 manifest（tmp + rename）。 */
    fun writeManifest(manifest: DownloadManifest)

    /** 删除整本画廊目录。 */
    fun deleteGallery(gid: Long)

    /** 写一页图片文件，返回字节数；失败返回 -1。 */
    fun writePage(gid: Long, index: Int, extension: String, bytes: ByteArray): Long

    /** 页文件是否已存在且大小合理。 */
    fun pageFileSize(gid: Long, index: Int): Long

    /** 一次列出整本画廊的页文件大小（index → 字节数），避免逐页遍历目录。 */
    fun pageFileSizes(gid: Long): Map<Int, Long>

    /** 清单内容未变化时不写盘，返回是否写入。 */
    fun writeManifestIfChanged(manifest: DownloadManifest): Boolean

    /** 读取一页（离线阅读用）；没有返回 null。 */
    fun readPage(gid: Long, index: Int): ByteArray?

    /** 已下载总占用（字节）。 */
    fun totalSize(): Long
}

/** 从图片 URL 推断扩展名，默认 jpg。 */
internal fun extensionOf(url: String): String {
    val path = url.substringBefore('?').substringAfterLast('/')
    val ext = path.substringAfterLast('.', "")
    return when (ext.lowercase()) {
        "jpg", "jpeg" -> "jpg"
        "png" -> "png"
        "webp" -> "webp"
        "gif" -> "gif"
        else -> "jpg"
    }
}

/** 图片魔数校验：防止把错误页当图片存下来。 */
internal fun looksLikeImage(bytes: ByteArray): Boolean {
    if (bytes.size < 16) return false
    fun at(i: Int) = bytes[i].toInt() and 0xFF
    return when {
        at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> true                    // JPEG
        at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> true   // PNG
        at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x38 -> true   // GIF
        at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 &&
            at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50 -> true // WEBP
        else -> false
    }
}

/** 是否是明显的 HTML 错误页。 */
internal fun looksLikeHtmlError(bytes: ByteArray): Boolean {
    val head = bytes.take(64).toByteArray().toString(Charsets.UTF_8).trimStart()
    return head.startsWith("<") || head.contains("pining for the fjords", ignoreCase = true)
}
