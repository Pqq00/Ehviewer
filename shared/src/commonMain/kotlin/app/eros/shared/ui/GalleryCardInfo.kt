// Copyright 2026, eros-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package app.eros.shared.ui

import androidx.compose.ui.graphics.Color

/** E-Hentai 分类位/索引 → 显示名。 */
internal fun categoryName(category: Int): String? = when (category) {
    0x1, 1 -> "Misc"
    0x2, 2 -> "Doujinshi"
    0x4, 3 -> "Manga"
    0x8, 4 -> "Artist CG"
    0x10, 5 -> "Game CG"
    0x20, 6 -> "Image Set"
    0x40, 7 -> "Cosplay"
    0x80, 8 -> "Asian Porn"
    0x100, 9 -> "Non-H"
    0x200, 10 -> "Western"
    else -> null
}

/**
 * 分类角标底色：照 EhViewer 的分类色，稍微压暗一点保证白字可读。
 * 未知分类沿用原来的半透明黑。
 */
internal fun categoryColor(category: Int): Color = when (category) {
    0x1, 1 -> Color(0xE05D5D5D)
    0x2, 2 -> Color(0xE0D32F2F)
    0x4, 3 -> Color(0xE0EF6C00)
    0x8, 4 -> Color(0xE02E7D32)
    0x10, 5 -> Color(0xE01565C0)
    0x20, 6 -> Color(0xE06A1B9A)
    0x40, 7 -> Color(0xE0AD1457)
    0x80, 8 -> Color(0xE0BF360C)
    0x100, 9 -> Color(0xE04E342E)
    0x200, 10 -> Color(0xE037474F)
    else -> Color.Black.copy(alpha = 0.45f)
}

/** 语言标签（`language:chinese`）→ 两字母码，照 EhViewer 的 `S_LANG_TAGS`。 */
private val LANG_TAGS: List<Pair<String, String>> = listOf(
    "language:english" to "EN",
    "language:chinese" to "ZH",
    "language:spanish" to "ES",
    "language:korean" to "KO",
    "language:russian" to "RU",
    "language:french" to "FR",
    "language:portuguese" to "PT",
    "language:thai" to "TH",
    "language:german" to "DE",
    "language:italian" to "IT",
    "language:vietnamese" to "VI",
    "language:polish" to "PL",
    "language:hungarian" to "HU",
    "language:dutch" to "NL",
)

/**
 * 列表页常常没有标签，用标题猜语言。
 * 标题里的语言标记：覆盖站点上常见的「(English)」「中文/漢化」「日本語」等写法。
 */
private val LANG_TITLE_PATTERNS: List<Pair<Regex, String>> = listOf(
    Regex("""[(\[]eng(?:lish)?[)\]]|英訳""", RegexOption.IGNORE_CASE) to "EN",
    Regex("""[(\[（]ch(?:inese)?[)\]）]|[汉漢]化|中[国國][语語]|中文|中国翻訳""", RegexOption.IGNORE_CASE) to "ZH",
    Regex("""[(\[]spanish[)\]]|[(\[]Español[)\]]|スペイン翻訳""", RegexOption.IGNORE_CASE) to "ES",
    Regex("""[(\[]korean?[)\]]|韓国翻訳""", RegexOption.IGNORE_CASE) to "KO",
    Regex("""[(\[]rus(?:sian)?[)\]]|ロシア翻訳""", RegexOption.IGNORE_CASE) to "RU",
    Regex("""[(\[]fr(?:ench)?[)\]]|フランス翻訳""", RegexOption.IGNORE_CASE) to "FR",
    Regex("""[(\[]portuguese|ポルトガル翻訳""", RegexOption.IGNORE_CASE) to "PT",
    Regex("""[(\[]thai(?: ภาษาไทย)?[)\]]|แปลไทย|タイ翻訳""", RegexOption.IGNORE_CASE) to "TH",
    Regex("""[(\[]german[)\]]|ドイツ翻訳""", RegexOption.IGNORE_CASE) to "DE",
    Regex("""[(\[]italiano?[)\]]|イタリア翻訳""", RegexOption.IGNORE_CASE) to "IT",
    Regex("""[(\[]vietnamese(?: Tiếng Việt)?[)\]]|ベトナム翻訳""", RegexOption.IGNORE_CASE) to "VI",
    Regex("""[(\[]polish[)\]]|ポーランド翻訳""", RegexOption.IGNORE_CASE) to "PL",
    Regex("""[(\[]hun(?:garian)?[)\]]|ハンガリー翻訳""", RegexOption.IGNORE_CASE) to "HU",
    Regex("""[(\[]dutch[)\]]|オランダ翻訳""", RegexOption.IGNORE_CASE) to "NL",
)

/** ① 先看标签，② 再按标题猜（与 EhViewer `generateSLang()` 同顺序）。 */
internal fun languageOf(tags: List<String>, title: String): String? {
    tags.firstOrNull { it.startsWith("language:") }?.let { tag ->
        LANG_TAGS.firstOrNull { it.first == tag }?.let { return it.second }
    }
    LANG_TITLE_PATTERNS.firstOrNull { it.first.containsMatchIn(title) }?.let { return it.second }
    return null
}
