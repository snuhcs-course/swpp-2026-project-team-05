package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.swpp.team5.frameless.R
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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
 * Metadata line parts for a feed row: category and publisher. A blank publisher name is dropped
 * rather than shown as an empty slot; nothing is substituted.
 */
internal fun feedRowMetaParts(article: ArticleSummary): List<String> =
    listOf(article.category.label, article.publisher.name.trim()).filter { it.isNotEmpty() }

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

/** List byline: the original publisher, without a timestamp. */
internal fun feedByline(article: ArticleSummary): String = feedRowMetaParts(article).drop(1).joinToString("  ·  ")

/** Category kicker: small drawn mark plus the category label, tinted for the surface it sits on. */
@Composable
private fun CategoryKicker(category: NewsCategory, color: Color, fontSize: TextUnit) {
    Text(category.label, color = color, fontSize = fontSize,
        fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
}

/**
 * Editorial cover for the first article of the normal online feed. The category artwork fills the card; the
 * stored kicker, title, optional excerpt and byline sit on a soft scrim at the bottom. It carries no editorial
 * label (no "top", "recommended" or issue name) and no counts. The whole cover is one tap target.
 */
@Composable
internal fun FeaturedArticleCard(article: ArticleSummary, onClick: () -> Unit) {
    val palette = categoryPalette(article.category)
    val byline = feedByline(article)
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
                    Box(Modifier.size(34.dp).border(1.dp, palette.glow.copy(alpha = 0.55f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_portal_arrow_forward), contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(18.dp))
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
            ComparisonColumnsMotif()
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(R.drawable.ic_portal_chevron_right), contentDescription = null,
                tint = HomeTokens.Navy, modifier = Modifier.size(18.dp))
        }
    }
}

/** Quiet comparison motif; the surrounding guide owns the action. */
@Composable
private fun ComparisonColumnsMotif() {
    Icon(painterResource(R.drawable.ic_portal_compare), contentDescription = null,
        tint = HomeTokens.LavenderInk, modifier = Modifier.size(34.dp))
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
            Icon(painterResource(R.drawable.ic_portal_menu), contentDescription = null,
                tint = menuInk, modifier = Modifier.size(21.dp))
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
    when (item) {
        FeedMenuItem.TUTORIAL -> Icon(painterResource(R.drawable.ic_portal_book_open),
            contentDescription = null, tint = Fl.Ink2, modifier = Modifier.size(18.dp))
    }
}

/**
 * Light editorial row on the page surface: category kicker, serif title, optional excerpt, then byline.
 * No image tile; category is conveyed by its text label.
 */
@Composable
internal fun ArticleFeedRow(
    article: ArticleSummary,
    onClick: () -> Unit
) {
    val palette = categoryPalette(article.category)
    val byline = feedByline(article)
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
