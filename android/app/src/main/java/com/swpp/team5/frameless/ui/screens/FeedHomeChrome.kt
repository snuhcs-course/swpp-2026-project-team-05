package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.ui.theme.Fl

/**
 * Home/feed chrome for the Iteration 4 visual refresh: the header menu, the editorial cover, the article rows and
 * the section header. Nothing here reads or invents data beyond what [ArticleSummary] already carries.
 */
internal object HomeTokens {
    val Navy: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
    val OffWhite: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
    val Surface: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
    val Lavender: Color @Composable @ReadOnlyComposable get() = Fl.TintDiff
    val LavenderInk: Color @Composable @ReadOnlyComposable get() = Fl.InkDiff
    val Hairline: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline
    val GuideWash: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
}

/** Actions moved from the app bar into the left menu, in display order. Labels are the existing ones. */
internal enum class FeedMenuItem(val label: String) {
    TUTORIAL("튜토리얼 다시 보기")
}

internal const val FEED_MENU_DESCRIPTION = "메뉴"

/**
 * Metadata line parts for a feed row: category, publisher, time. A blank publisher name or time is dropped
 * rather than shown as an empty slot; nothing is substituted.
 */
internal fun feedRowMetaParts(article: ArticleSummary, time: String): List<String> =
    listOf(article.category.label, article.publisher.name.trim(), time.trim()).filter { it.isNotEmpty() }

internal const val FEATURED_ARTICLE_TAG = "feed-featured-article"

/** The lead article (if any) and the rows listed below it. The lead never appears twice. */
internal data class FeaturedSplit(val featured: ArticleSummary?, val rest: List<ArticleSummary>)

/**
 * Splits the loaded feed for the Home layout. When [enabled] and the feed is non-empty, the first stored article
 * (the newest, as already ordered by the feed) becomes the lead and is removed from the list; otherwise the list is
 * returned untouched. No reordering, ranking or filtering happens here.
 */
internal fun splitFeaturedArticle(items: List<ArticleSummary>, enabled: Boolean): FeaturedSplit =
    if (enabled && items.isNotEmpty()) FeaturedSplit(items.first(), items.drop(1))
    else FeaturedSplit(null, items)

internal const val FEATURED_OPEN_LABEL = "기사 열기"

/** Byline under a title: publisher and time, blank parts dropped, nothing substituted. */
internal fun feedByline(article: ArticleSummary, time: String): String =
    feedRowMetaParts(article, time).drop(1).joinToString("  ·  ")

/** Category kicker: small drawn mark plus the category label, tinted for the surface it sits on. */
@Composable
private fun CategoryKicker(category: NewsCategory, color: Color, fontSize: TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp).clearAndSetSemantics { }) { drawCategoryMark(category, color) }
        Spacer(Modifier.width(7.dp))
        Text(
            category.label,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}

/**
 * Editorial cover for the first article of the normal online feed. The category artwork fills the card; the
 * stored kicker, title, optional excerpt and byline sit on a soft scrim at the bottom. It carries no editorial
 * label (no "top", "recommended" or issue name) and no counts. The whole cover is one tap target.
 */
@Composable
internal fun FeaturedArticleCard(article: ArticleSummary, onClick: () -> Unit) {
    val palette = categoryPalette(article.category)
    val byline = feedByline(article, feedRelativeTime(article.publishedAt))
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.fillMaxWidth().background(HomeTokens.OffWhite)
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .shadow(14.dp, shape, ambientColor = palette.deep, spotColor = palette.deep)
                .clip(shape)
                .background(palette.deep)
                .clickable(onClickLabel = FEATURED_OPEN_LABEL, onClick = onClick)
                .testTag(FEATURED_ARTICLE_TAG)
        ) {
            CategoryCoverArt(article.category, Modifier.matchParentSize())
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.40f to palette.deep.copy(alpha = 0.10f),
                        0.68f to palette.deep.copy(alpha = 0.86f),
                        1f to palette.deep
                    )
                )
            )
            Column(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 156.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CategoryKicker(article.category, palette.glow, 12.sp)
                Text(
                    article.title,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = Fl.Serif,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    color = Color.White
                )
                article.searchExcerpt?.let { excerpt ->
                    Text(excerpt, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        fontSize = 13.5.sp, lineHeight = 20.sp, color = Color.White.copy(alpha = 0.78f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (byline.isNotEmpty()) Text(
                            byline,
                            fontSize = 12.sp,
                            color = palette.glow.copy(alpha = 0.92f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Quiet tap affordance; the whole cover is the button, so this is decorative.
                    Canvas(
                        Modifier.size(34.dp).clearAndSetSemantics { }
                    ) {
                        val c = Offset(size.width / 2, size.height / 2)
                        drawCircle(palette.glow.copy(alpha = 0.55f), radius = size.width / 2 - density,
                            center = c, style = Stroke(1.2f * density))
                        val a = size.width * 0.16f
                        drawLine(Color.White, Offset(c.x - a, c.y), Offset(c.x + a, c.y), 1.6f * density,
                            StrokeCap.Round)
                        drawLine(Color.White, Offset(c.x + a * 0.2f, c.y - a * 0.8f), Offset(c.x + a, c.y),
                            1.6f * density, StrokeCap.Round)
                        drawLine(Color.White, Offset(c.x + a * 0.2f, c.y + a * 0.8f), Offset(c.x + a, c.y),
                            1.6f * density, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

internal const val READING_GUIDE_DESCRIPTION = "표현 비교 읽는 법"

/**
 * Compact editorial rail that explains what FrameLESS does and replays the existing tutorial. It carries only
 * fixed product copy: no issue, outlet, count, quote or score. Shown with the lead article in the normal feed.
 */
@Composable
internal fun ReadingGuideRail(onClick: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier.fillMaxWidth().background(HomeTokens.OffWhite)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(HomeTokens.GuideWash, shape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = READING_GUIDE_DESCRIPTION }
                .padding(start = 18.dp, end = 12.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "FRAMELESS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    color = HomeTokens.LavenderInk
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "같은 사건, 다른 표현",
                    fontFamily = Fl.Serif,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    color = HomeTokens.Navy
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "보라색 표현을 누르면 다른 보도의 표현을 함께 볼 수 있어요.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Fl.Ink2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            ConvergingMarksMotif()
            Spacer(Modifier.width(6.dp))
            val arrowInk = HomeTokens.Navy
            Canvas(Modifier.size(14.dp).clearAndSetSemantics { }) {
                val stroke = 1.6.dp.toPx()
                drawLine(arrowInk, Offset(size.width * 0.35f, size.height * 0.2f),
                    Offset(size.width * 0.7f, size.height * 0.5f), stroke, StrokeCap.Round)
                drawLine(arrowInk, Offset(size.width * 0.7f, size.height * 0.5f),
                    Offset(size.width * 0.35f, size.height * 0.8f), stroke, StrokeCap.Round)
            }
        }
    }
}

/** Two short text-line marks, each with an underline, converging on one shared point. Decorative only. */
@Composable
private fun ConvergingMarksMotif() {
    val ink = HomeTokens.Navy
    val lavender = HomeTokens.Lavender
    val lavenderInk = HomeTokens.LavenderInk
    Canvas(Modifier.size(width = 52.dp, height = 36.dp).clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        val thin = 1.2.dp.toPx()
        val bar = 3.dp.toPx()
        val meet = Offset(w * 0.86f, h * 0.5f)
        // Upper "text line" with its underline mark.
        drawLine(ink.copy(alpha = 0.22f), Offset(w * 0.04f, h * 0.18f), Offset(w * 0.40f, h * 0.18f),
            bar, StrokeCap.Round)
        drawLine(lavenderInk, Offset(w * 0.04f, h * 0.30f), Offset(w * 0.30f, h * 0.30f),
            thin, StrokeCap.Round)
        // Lower "text line" with its underline mark.
        drawLine(ink.copy(alpha = 0.22f), Offset(w * 0.04f, h * 0.70f), Offset(w * 0.34f, h * 0.70f),
            bar, StrokeCap.Round)
        drawLine(lavenderInk, Offset(w * 0.04f, h * 0.82f), Offset(w * 0.26f, h * 0.82f),
            thin, StrokeCap.Round)
        // Both underlines lead to one shared point.
        drawLine(lavenderInk.copy(alpha = 0.7f), Offset(w * 0.30f, h * 0.30f), meet, thin, StrokeCap.Round)
        drawLine(lavenderInk.copy(alpha = 0.7f), Offset(w * 0.26f, h * 0.82f), meet, thin, StrokeCap.Round)
        drawCircle(lavender, radius = 4.5.dp.toPx(), center = meet)
        drawCircle(lavenderInk, radius = 4.5.dp.toPx(), center = meet, style = Stroke(thin))
        drawCircle(lavenderInk, radius = 1.6.dp.toPx(), center = meet)
    }
}

@Composable
internal fun FeedMenuButton(
    onTutorialOpen: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val menuInk = Fl.Ink
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp).semantics { contentDescription = FEED_MENU_DESCRIPTION }
        ) {
            Canvas(Modifier.size(20.dp)) {
                val stroke = 1.8.dp.toPx()
                listOf(0.25f, 0.5f, 0.75f).forEachIndexed { index, y ->
                    drawLine(
                        menuInk,
                        Offset(size.width * 0.12f, size.height * y),
                        Offset(size.width * (if (index == 2) 0.62f else 0.88f), size.height * y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = HomeTokens.Surface
        ) {
            FeedMenuItem.entries.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(item.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Fl.Ink)
                    },
                    leadingIcon = { FeedMenuGlyph(item) },
                    onClick = {
                        expanded = false
                        when (item) {
                            FeedMenuItem.TUTORIAL -> onTutorialOpen()
                        }
                    },
                    // Keeps the old app-bar content description so accessibility users find the same names.
                    modifier = Modifier.semantics { contentDescription = item.label }
                )
            }
        }
    }
}

@Composable
private fun FeedMenuGlyph(item: FeedMenuItem) {
    val iconInk = Fl.Ink2
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val stroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (item) {
            FeedMenuItem.TUTORIAL -> {
                drawCircle(iconInk, radius = w * 0.42f, style = stroke)
                val q = Path().apply {
                    moveTo(w * 0.36f, w * 0.38f)
                    cubicTo(w * 0.36f, w * 0.22f, w * 0.64f, w * 0.22f, w * 0.64f, w * 0.38f)
                    cubicTo(w * 0.64f, w * 0.5f, w * 0.5f, w * 0.5f, w * 0.5f, w * 0.6f)
                }
                drawPath(q, iconInk, style = stroke)
                drawCircle(iconInk, radius = w * 0.05f, center = Offset(w * 0.5f, w * 0.75f))
            }
        }
    }
}

/**
 * Light editorial row on the page surface: category kicker, serif title, optional excerpt, then byline.
 * No image tile; the small kicker mark is the only category visual.
 */
@Composable
internal fun ArticleFeedRow(
    article: ArticleSummary,
    onClick: () -> Unit
) {
    val palette = categoryPalette(article.category)
    val byline = feedByline(article, feedRelativeTime(article.publishedAt))
    Column(
        Modifier.fillMaxWidth().background(HomeTokens.OffWhite).clickable(onClick = onClick)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        CategoryKicker(article.category, Fl.Muted, 11.5.sp)
        Text(
            article.title,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            fontFamily = Fl.Serif,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            color = Fl.Ink
        )
        article.searchExcerpt?.let { excerpt ->
            Text(excerpt, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontSize = 13.5.sp, lineHeight = 20.sp, color = Fl.Ink2)
        }
        if (byline.isNotEmpty()) {
            Text(byline, fontSize = 12.sp, color = Fl.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Section heading over the feed list: serif title and the existing scope/offline caption.
 * Refresh is owned by the surrounding pull gesture. With no title (a one-article
 * feed) only the caption row remains.
 */
@Composable
internal fun FeedSectionHeader(
    title: String?,
    caption: String
) {
    Column(
        Modifier.fillMaxWidth().background(HomeTokens.OffWhite)
            .padding(start = 20.dp, end = 12.dp, top = if (title != null) 26.dp else 10.dp, bottom = 6.dp)
    ) {
        if (title != null) {
            Text(title, fontFamily = Fl.Serif, fontSize = 21.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp, color = Fl.Ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(14.dp).height(2.dp).background(HomeTokens.Navy, RoundedCornerShape(1.dp)))
                Spacer(Modifier.width(8.dp))
                Text(caption, color = Fl.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text(caption, color = Fl.Muted, fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Hairline between rows, inset to the text column. */
@Composable
internal fun FeedRowDivider() {
    Box(Modifier.fillMaxWidth().background(HomeTokens.OffWhite).padding(horizontal = 20.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(HomeTokens.Hairline))
    }
}
