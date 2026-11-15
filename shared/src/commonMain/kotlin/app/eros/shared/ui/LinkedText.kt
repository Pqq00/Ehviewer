// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import app.eros.shared.data.GalleryLink
import app.eros.shared.data.GalleryRef
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 把正文里的链接渲染成可点的蓝字。
 *
 * 点击行为靠替换 [LocalUriHandler] 实现：miuix `Text` 收到 `LinkAnnotation.Url` 后会补齐默认样式
 * （primary + 下划线），点击时调用 `LocalUriHandler.openUri`，这里把它换成「站内画廊 → 应用内页面、
 * 其它 → 平台浏览器」。
 *
 * @param onOpenGallery 站内画廊链接的跳转回调；为 null 时站内链接也交给浏览器。
 */
@Composable
internal fun LinkedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MiuixTheme.textStyles.body2,
    color: Color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.88f),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onOpenGallery: ((GalleryRef) -> Unit)? = null,
) {
    val platformHandler = LocalUriHandler.current
    val spans = remember(text) { GalleryLink.findAll(text) }
    val annotated = remember(text, spans) {
        if (spans.isEmpty()) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                append(text)
                spans.forEach { span ->
                    // 样式留空，由 miuix 的 createTextWithLinkStyles 统一补 primary + 下划线。
                    addLink(LinkAnnotation.Url(span.url), span.start, span.end)
                }
            }
        }
    }
    val handler = remember(platformHandler, onOpenGallery) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val ref = GalleryLink.parse(uri)
                if (ref != null && onOpenGallery != null) {
                    onOpenGallery(ref)
                } else {
                    runCatching { platformHandler.openUri(GalleryLink.externalUrl(uri)) }
                }
            }
        }
    }
    CompositionLocalProvider(LocalUriHandler provides handler) {
        Text(
            text = annotated,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = onTextLayout,
        )
    }
}
