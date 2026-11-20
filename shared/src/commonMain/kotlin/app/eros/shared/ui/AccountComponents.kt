// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import app.eros.shared.ui.blend.ColorBlendToken

/** 站点选择行：名字 + 说明 + 选中打勾。 */
@Composable
internal fun SiteChoiceRow(
    name: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = "当前站点",
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 站点 Profile 行：名字 + 选中打勾 + 编号（编号就是 cookie `sp` 的值）。 */
@Composable
internal fun SiteProfileRow(
    name: String,
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = name,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = "当前 Profile",
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = number.toString(),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

/** 官方 About 页同款玻璃卡片：内容区域对背景做模糊 + 染色混合。 */
@Composable
internal fun GlassCard(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val blend = remember(dark) {
        if (dark) ColorBlendToken.Overlay_Thin_Light else ColorBlendToken.Pured_Regular_Light
    }
    Card(
        modifier = modifier.then(
            if (backdrop != null) {
                Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = RoundedCornerShape(16.dp),
                    blurRadius = 60f,
                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                    colors = BlurDefaults.blurColors(blendColors = blend),
                )
            } else {
                Modifier
            },
        ),
        colors = CardDefaults.defaultColors(
            if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            // 不能给 Transparent：Card 会把它作为 LocalContentColor，TextField 的输入文字
            // 默认取该颜色，结果只剩光标可见、文字全透明。
            MiuixTheme.colorScheme.onSurface,
        ),
    ) {
        content()
    }
}

/** 应用名称：字号与效果都对齐 miuix 官方示例「关于」页（35sp 加粗 + 玻璃字）。 */
@Composable
internal fun LogoTitle(text: String, backdrop: LayerBackdrop?) {
    // 官方做法：文字自身当遮罩（DstIn），把背景模糊透进字形里，再叠三层染色，
    // 所以看起来不是纯色字而是一块玻璃。
    val dark = isSystemInDarkTheme()
    val logoBlend = remember(dark) {
        if (dark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }
    Text(
        text = text,
        modifier = if (backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RoundedCornerShape(16.dp),
                blurRadius = 150f,
                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                colors = BlurDefaults.blurColors(blendColors = logoBlend),
                contentBlendMode = BlendMode.DstIn,
            )
        } else {
            Modifier
        },
        color = MiuixTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        fontSize = 35.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

/** Cookie 行：名称 + 值（默认打码，点一下才显示原文）。 */
@Composable
internal fun CookieRow(name: String, value: String?) {
    var revealed by remember(name) { mutableStateOf(false) }
    val text = value?.takeIf { it.isNotBlank() }
    val shown = when {
        text == null -> "（未设置）"
        revealed -> text
        // 三行统一用同样数量的点：长度一致才好对齐，也不泄露真实长度。
        else -> COOKIE_MASK
    }
    BasicComponent(
        title = name,
        summary = shown,
        onClick = if (text == null) null else ({ revealed = !revealed }),
    )
}

/** 打码占位：三行 Cookie 都用它，保证数量一致、左对齐。 */
private const val COOKIE_MASK = "••••••••"
