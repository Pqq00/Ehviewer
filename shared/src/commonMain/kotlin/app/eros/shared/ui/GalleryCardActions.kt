// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.eros.shared.data.GalleryTitles
import app.eros.shared.data.GalleryUi
import app.eros.shared.data.createEhApi
import app.eros.shared.system.showSystemToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 信息流卡片的长按菜单：加入收藏夹 / 复制标题 / 复制链接。
 *
 * 网格卡与下载卡样式共用这一个入口，动作语义保持一致。
 *
 * @param show 是否展开；由卡片持有并负责在点击卡片时置 true
 * @param gallery 当前卡片对应的画廊
 * @param onDismiss 关闭菜单（点击遮罩、返回键或任一动作后回调）
 */
@Composable
internal fun GalleryCardActions(
    show: Boolean,
    gallery: GalleryUi,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    ErosBottomSheet(
        backdrop = LocalPageBackdrop.current,
        show = show,
        title = GalleryTitles.primary(gallery.title, gallery.titleJpn),
        onDismissRequest = onDismiss,
        onDismissFinished = { },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            SheetAction("加入收藏夹 0") {
                onDismiss()
                scope.launch {
                    val ok = runCatching {
                        withContext(Dispatchers.IO) {
                            createEhApi().setFavourite(gallery.gid, gallery.token, 0)
                        }
                    }.getOrDefault(false)
                    showSystemToast(if (ok) "已加入收藏夹 0" else "收藏失败，请确认已登录")
                }
            }
            SheetAction("复制标题") {
                clipboard.setText(
                    AnnotatedString(GalleryTitles.primary(gallery.title, gallery.titleJpn)),
                )
                onDismiss()
                showSystemToast("标题已复制")
            }
            SheetAction("复制链接") {
                clipboard.setText(AnnotatedString("https://e-hentai.org/g/${gallery.gid}/"))
                onDismiss()
                showSystemToast("链接已复制")
            }
        }
    }
}

/** 底部菜单里的一行动作。 */
@Composable
private fun SheetAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body1,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = null)
            .padding(vertical = 14.dp),
    )
}
