package app.eros.shared.ui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import app.eros.shared.data.GalleryTitles
import app.eros.shared.download.DownloadManager
import app.eros.shared.download.DownloadStatus
import app.eros.shared.system.showSystemToast
import app.eros.shared.ui.DetailState
import app.eros.shared.ui.ReadStore

/**
 * 详情页的三个动作（收藏 / 下载 / 阅读），底栏与两种 rail 共用同一份。
 *
 * Phase 1 从 `App()` 的重复参数块抽出：之前 rail(native/glass) 与底栏各写一遍
 * `activeDetail?.detail != null`、`readProgress?.first?.plus(1)`、下载文案等，
 * 改一处漏一处的风险高。
 */
@Immutable
internal data class DetailActions(
    val enabled: Boolean,
    val favorited: Boolean,
    val favoriteCount: Int,
    val readPage: Int?,
    val readStarted: Boolean,
    val downloadLabel: String,
    val downloadActive: Boolean,
    val onFavorite: () -> Unit,
    val onDownload: () -> Unit,
    val onRead: () -> Unit,
)

/**
 * 按当前详情页状态算出 [DetailActions]；传 null 时返回全部禁用的一组。
 *
 * 每次组合都重新构造（回调闭包捕获最新状态），不做 remember —— 收藏数、下载进度
 * 都是 Compose 状态，读取时自然触发重组。
 */
@Composable
internal fun rememberDetailActions(
    state: DetailState?,
    onRead: (DetailState) -> Unit,
): DetailActions {
    val tasks by DownloadManager.tasks.collectAsState()
    val task = state?.let { st -> tasks.firstOrNull { it.gid == st.gid } }
    val progress = state?.let { st -> ReadStore.progressOf(st.gid) }
    val currentOnRead by rememberUpdatedState(onRead)

    val downloadLabel = when {
        task == null -> "下载"
        task.status == DownloadStatus.DONE -> "已下载"
        else -> "${(task.progress * 100).toInt()}%"
    }

    val onDownload: () -> Unit = {
        val st = state
        val detail = st?.detail
        if (st != null && detail != null && task == null) {
            DownloadManager.enqueue(
                gid = detail.gid,
                token = st.token,
                title = GalleryTitles.primary(detail.title, detail.titleJpn),
                titleJpn = detail.titleJpn,
                totalPages = detail.pages,
                thumb = detail.thumb,
                uploader = detail.uploader,
                artist = detail.tags.firstOrNull { it.namespace == "artist" }?.tags?.firstOrNull(),
                category = detail.category,
            )
            showSystemToast("已加入下载队列")
        }
    }

    return DetailActions(
        enabled = state?.detail != null,
        favorited = state?.favorited ?: false,
        favoriteCount = state?.favoriteCount ?: 0,
        readPage = progress?.first?.plus(1),
        readStarted = progress != null,
        downloadLabel = downloadLabel,
        downloadActive = task != null,
        onFavorite = { state?.openFavSheet() },
        onDownload = onDownload,
        onRead = { state?.let(currentOnRead) },
    )
}
