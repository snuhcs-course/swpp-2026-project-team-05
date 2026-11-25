package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.R
import com.swpp.team5.frameless.data.ReactionKind
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.viewmodel.ArticleReactionState
import java.text.NumberFormat
import java.util.Locale

internal fun reactionCountLabel(count: Long): String {
    if (count < 10_000) return NumberFormat.getIntegerInstance(Locale.KOREAN).format(count)
    val unit = if (count >= 100_000_000) 100_000_000L else 10_000L
    val tenths = count / (unit / 10)
    return "${tenths / 10}${if (tenths % 10 == 0L) "" else ".${tenths % 10}"}${if (unit == 10_000L) "만" else "억"}"
}

private fun ReactionKind.icon() = when (this) {
    ReactionKind.RECOMMEND -> R.drawable.ic_reaction_recommend
    ReactionKind.LIKE -> R.drawable.ic_reaction_like
    ReactionKind.MOVED -> R.drawable.ic_reaction_moved
    ReactionKind.ANGRY -> R.drawable.ic_reaction_angry
    ReactionKind.SAD -> R.drawable.ic_reaction_sad
}

@Composable
internal fun ArticleReactionSection(
    state: ArticleReactionState,
    onSelect: (ReactionKind) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().testTag("article-reactions")) {
        HorizontalDivider(color = Fl.Line)
        Text("이 기사를 어떻게 보셨나요?", color = Fl.Ink2, fontSize = 13.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 16.dp))
        val page = state.page
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ReactionKind.entries.forEach { kind ->
                val selected = page?.selected == kind
                val actionKind = state.intent?.kind ?: page?.selected
                val spinning = state.pending && actionKind == kind
                val shape = RoundedCornerShape(12.dp)
                Column(
                    Modifier.weight(1f).fillMaxHeight().heightIn(min = 100.dp).clip(shape)
                        .background(if (selected) Fl.Inset else Color.Transparent, shape)
                        .border(1.dp, if (selected) Fl.Line else Color.Transparent, shape)
                        .selectable(selected = selected, enabled = page != null && !state.pending,
                            role = Role.RadioButton, onClick = { onSelect(kind) })
                        .testTag("reaction-${kind.name}")
                        .semantics {
                            contentDescription = "${kind.label}, ${page?.counts?.get(kind)?.let { "$it 명" } ?: "불러오는 중"}"
                        }
                        .padding(horizontal = 1.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        if (spinning) CircularProgressIndicator(Modifier.size(23.dp), strokeWidth = 1.8.dp, color = Fl.Muted)
                        else Image(painterResource(kind.icon()), contentDescription = null, modifier = Modifier.size(36.dp))
                    }
                    Text(kind.label, color = Fl.Ink2, fontSize = 10.sp, lineHeight = 15.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.weight(1f))
                    if (page == null) Box(Modifier.padding(top = 5.dp).size(18.dp, 10.dp)
                        .background(Fl.Inset, RoundedCornerShape(4.dp)))
                    else Text(reactionCountLabel(page.counts.getValue(kind)), color = Fl.Ink,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 3.dp).testTag("reaction-count-${kind.name}"))
                }
            }
        }
        state.error?.let { error ->
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Text(error, color = Fl.Muted, fontSize = 11.sp)
                TextButton(onClick = onRetry, enabled = !state.pending && !state.loading) {
                    Text("다시 시도", color = Fl.Ink, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
