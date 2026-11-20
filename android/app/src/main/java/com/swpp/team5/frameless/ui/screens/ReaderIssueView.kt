package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.data.FindingUi
import com.swpp.team5.frameless.data.FrameFront
import com.swpp.team5.frameless.data.FrameLensUi
import com.swpp.team5.frameless.data.IssueOutlet
import com.swpp.team5.frameless.data.IssueViewUi
import com.swpp.team5.frameless.ui.theme.Fl

/*
 * Event issue view on Android (specs/features/event-issue-view.md): a frame card above the title with the event, what
 * this article puts in front of it, and the comparison points; the issues across outlets below the article. Everything
 * shown is stored data; the card and the list make no judgment of an outlet.
 */

private val FrameInk = Color(0xFF0F5E57)
private val FrameTint = Color(0xFFEEF5F4)

internal const val FrameCardLabel = "사건과 이 기사"
internal const val IssueListHeading = "쟁점별로 본 다른 언론사"
internal const val OwnPositionTag = "이 기사"

/** What the article puts in front, as one line of text: its position, «the fact», or speaker «words» · speaker «words». */
internal fun frontText(front: FrameFront): String = when (front.kind) {
    "position" -> front.label.orEmpty()
    "fact" -> "«${front.phrase.orEmpty()}»"
    else -> front.voices.joinToString("  ·  ") { v -> if (v.speaker.isBlank()) "«${v.quote}»" else "${v.speaker} «${v.quote}»" }
}

/** An outlet as listed in the issue view: its name, and the speaker in parentheses when it quotes one. */
internal fun outletText(outlet: IssueOutlet): String =
    if (outlet.voice.isNullOrBlank()) outlet.publisherName else "${outlet.publisherName} (${outlet.voice})"

@Composable
internal fun FrameCard(
    frame: FrameLensUi,
    points: List<ComparisonPoint>,
    onOpenPoint: (FindingUi) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth()
            .background(FrameTint, RoundedCornerShape(8.dp))
            .border(1.dp, FrameInk.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = FrameCardLabel }
    ) {
        Text("사건", fontSize = 11.sp, color = Fl.Muted)
        Text(frame.event, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
        frame.front?.let { front ->
            Spacer(Modifier.height(8.dp))
            Text("이 기사", fontSize = 11.sp, color = Fl.Muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    frontText(front),
                    fontFamily = if (front.kind == "position") null else Fl.Serif,
                    fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Fl.Ink,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (frame.frame.isNotBlank()) {
                    Text(
                        frame.frame, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FrameInk,
                        modifier = Modifier.border(1.dp, FrameInk.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
        if (points.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            ComparisonPointsSection(points = points, onOpen = onOpenPoint)
        }
    }
}

/** The issues on which some outlet takes a position; every outlet opens its own article. */
@Composable
internal fun IssueListSection(frame: FrameLensUi, onOpenArticle: (String) -> Unit) {
    if (frame.issues.isEmpty()) return
    Column(Modifier.fillMaxWidth().semantics { contentDescription = IssueListHeading }) {
        HorizontalDivider(color = FrameInk, thickness = 2.dp)
        Spacer(Modifier.height(12.dp))
        Text(IssueListHeading, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FrameInk,
            modifier = Modifier.semantics { heading() })
        Text(frame.event, fontSize = 12.sp, color = Fl.Muted)
        frame.issues.forEachIndexed { index, issue ->
            if (index > 0) HorizontalDivider(color = Fl.Line, modifier = Modifier.padding(top = 4.dp))
            IssueBlock(issue, onOpenArticle)
        }
    }
}

@Composable
private fun IssueBlock(issue: IssueViewUi, onOpenArticle: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(issue.question, fontFamily = Fl.Serif, fontSize = 15.sp, lineHeight = 22.sp,
            fontWeight = FontWeight.Bold, color = Fl.Ink)
        issue.positions.forEach { position ->
            Row(Modifier.fillMaxWidth()) {
                Spacer(
                    Modifier.width(2.dp).height(18.dp)
                        .background(if (position.isOwn) FrameInk else Fl.Line, RoundedCornerShape(50))
                )
                Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(position.label, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
                            color = Fl.Ink, modifier = Modifier.weight(1f, fill = false))
                        if (position.isOwn) {
                            Text(
                                OwnPositionTag, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                                modifier = Modifier.background(FrameInk, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    OutletLinks(position.outlets, onOpenArticle)
                }
            }
        }
        if (issue.factOnly.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Top) {
                Text("사실만 전함  ", fontSize = 12.sp, color = Fl.Muted)
                OutletLinks(issue.factOnly, onOpenArticle)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutletLinks(outlets: List<IssueOutlet>, onOpenArticle: (String) -> Unit) {
    if (outlets.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        outlets.forEach { outlet ->
            Text(
                outletText(outlet), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Fl.Ink2,
                modifier = Modifier
                    .clickable { onOpenArticle(outlet.articleId) }
                    .semantics { role = Role.Button; contentDescription = "${outletText(outlet)} 기사 열기" }
                    .padding(vertical = 2.dp)
            )
        }
    }
}
