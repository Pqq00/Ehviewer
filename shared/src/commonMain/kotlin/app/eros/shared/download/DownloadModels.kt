// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.download

import kotlinx.serialization.Serializable

/** 整本画廊的下载状态。 */
@Serializable
enum class DownloadStatus {
    QUEUED,
    RUNNING,

    /** 全部页抓取完成，正在校验落盘文件。 */
    VERIFYING,

    /** 校验通过，正在原子提交清单。 */
    COMMITTING,

    PAUSED,

    /** 失败后等待退避重试。 */
    RETRY_WAIT,
    DONE,
    FAILED,
}

/** 单页状态。 */
@Serializable
enum class PageStatus {
    PENDING,
    DONE,
    FAILED,
}

/** 单页记录。 */
@Serializable
data class DownloadPage(
    val index: Int,
    val status: PageStatus = PageStatus.PENDING,
    val file: String? = null,
    val bytes: Long = 0,
    val attempts: Int = 0,
    val error: String? = null,
)

/** 页面文件小于该值视为无效（错误页/空文件），需要重下。 */
const val MIN_VALID_PAGE_BYTES = 1024L

/** 画廊是否被调度器视为"占用中"（含校验/提交/退避等待）。 */
val DownloadStatus.isBusy: Boolean
    get() = this == DownloadStatus.QUEUED || this == DownloadStatus.RUNNING ||
        this == DownloadStatus.VERIFYING || this == DownloadStatus.COMMITTING ||
        this == DownloadStatus.RETRY_WAIT

/** 是否需要前台服务保活。 */
val DownloadStatus.needsForeground: Boolean
    get() = isBusy || this == DownloadStatus.PAUSED

/**
 * 一本画廊的下载清单，落盘为 `downloads/<gid>/manifest.json`。
 *
 * 用 JSON 而不是数据库：本期只需要"一本书一个清单"，且便于人工排查；
 * [schemaVersion] 已留好后续迁移到 Room 的余地。
 */
@Serializable
data class DownloadManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val gid: Long,
    val token: String,
    val title: String,
    val totalPages: Int,
    val thumb: String? = null,
    val savedAt: Long = System.currentTimeMillis(),
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val pages: List<DownloadPage> = emptyList(),
    val error: String? = null,
    /** 整本自动重试次数（网络类失败时递增，成功后归零）。 */
    val retryCount: Int = 0,
    /** 完成时间（epoch millis），0 表示未完成。 */
    val finishedAt: Long = 0L,
    /** 上传者（详情页抓到的元信息，旧清单没有则为空）。 */
    val uploader: String? = null,
    /** 作者（artist 标签），优先展示。 */
    val artist: String? = null,
    /** 画廊分类位（EhConfig 常量），-1 表示未知。 */
    val category: Int = -1,
    /** 日文标题（可能为空；旧清单没有该字段）。 */
    val titleJpn: String? = null,
) {
    val donePages: Int get() = pages.count { it.status == PageStatus.DONE }
    val failedPages: Int get() = pages.count { it.status == PageStatus.FAILED }

    /** 0f..1f */
    val progress: Float
        get() = if (totalPages <= 0) 0f else donePages.toFloat() / totalPages

    val downloadedBytes: Long get() = pages.sumOf { it.bytes }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val DIR_NAME = "downloads"
        const val MANIFEST_NAME = "manifest.json"

        fun create(
            gid: Long,
            token: String,
            title: String,
            totalPages: Int,
            thumb: String? = null,
            uploader: String? = null,
            artist: String? = null,
            category: Int = -1,
            titleJpn: String? = null,
        ): DownloadManifest = DownloadManifest(
            gid = gid,
            token = token,
            title = title,
            totalPages = totalPages,
            thumb = thumb,
            uploader = uploader,
            artist = artist,
            category = category,
            pages = (0 until totalPages).map { DownloadPage(index = it) },
            titleJpn = titleJpn,
        )
    }
}

/**
 * 启动恢复 / 提交前校验。
 *
 * 规则：
 *  - 页索引越界或重复的条目丢弃，缺失页补成 PENDING；
 *  - 标记 DONE 但文件不存在或小于 [MIN_VALID_PAGE_BYTES] → 回退 PENDING 重下；
 *  - 上次进程被杀时留下的 RUNNING/VERIFYING/COMMITTING/RETRY_WAIT → QUEUED 续传；
 *  - 状态 DONE 但仍有未完成页 → 按剩余页降级为 QUEUED/FAILED。
 *
 * 纯函数：文件系统大小由 [sizeOf] 注入，便于单测。
 */
fun DownloadManifest.reconciled(sizeOf: (Int) -> Long): DownloadManifest {
    if (totalPages <= 0) {
        return copy(status = DownloadStatus.FAILED, error = "清单损坏：页数非法", pages = emptyList())
    }
    val byIndex = pages
        .filter { it.index in 0 until totalPages }
        .groupBy { it.index }
        .mapValues { (_, group) -> group.firstOrNull { it.status == PageStatus.DONE } ?: group.first() }
    val normalized = (0 until totalPages).map { index ->
        val page = byIndex[index] ?: DownloadPage(index = index)
        if (page.status != PageStatus.DONE) {
            page
        } else {
            val size = sizeOf(index)
            when {
                size <= 0L -> page.copy(
                    status = PageStatus.PENDING,
                    file = null,
                    bytes = 0,
                    error = "文件缺失，待重新下载",
                )
                size < MIN_VALID_PAGE_BYTES -> page.copy(
                    status = PageStatus.PENDING,
                    file = null,
                    bytes = 0,
                    error = "文件过小，待重新下载",
                )
                else -> page
            }
        }
    }
    val allDone = normalized.all { it.status == PageStatus.DONE }
    val interrupted = status == DownloadStatus.RUNNING || status == DownloadStatus.VERIFYING ||
        status == DownloadStatus.COMMITTING || status == DownloadStatus.RETRY_WAIT
    val nextStatus = when {
        interrupted -> DownloadStatus.QUEUED
        status == DownloadStatus.DONE && !allDone ->
            if (normalized.any { it.status == PageStatus.FAILED }) DownloadStatus.FAILED else DownloadStatus.QUEUED
        else -> status
    }
    return copy(pages = normalized, status = nextStatus, error = if (nextStatus == DownloadStatus.QUEUED) null else error)
}
/** 人类可读的字节数（B/KB/MB/GB）。 */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format("%.1f GB", bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024)
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
