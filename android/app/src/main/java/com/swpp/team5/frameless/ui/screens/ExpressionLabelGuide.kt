package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.swpp.team5.frameless.ui.theme.FilledPill
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.TagPill

/** 판정 기준표 v0.2의 표현 차이 범주 A~G. 이름은 하단 주석 라벨과 같은 [categoryLabel]을 쓴다. */
internal data class ExpressionLabelExample(val code: String, val baseline: String, val marked: String)

internal val ExpressionLabelExamples = listOf(
    ExpressionLabelExample("A", "말했다", "강변했다, 우겼다"),
    ExpressionLabelExample("B", "발언", "망언, 궤변"),
    ExpressionLabelExample("C", "처리했다", "강행 처리했다"),
    ExpressionLabelExample("D", "늘었다", "급증했다"),
    ExpressionLabelExample("E", "대응", "졸속 대응"),
    ExpressionLabelExample("F", "개정안", "개악"),
    ExpressionLabelExample("G", "~할 전망이다", "확실시된다")
)

/** 소개 화면의 라벨 한 줄 설명. */
internal val ExpressionLabelSummaries = mapOf(
    "A" to "발언을 전하는 동사에 태도가 실린 경우",
    "B" to "발언 자체를 규정하는 이름을 붙인 경우",
    "C" to "같은 행위를 더 강하게 규정한 경우",
    "D" to "수치나 변화의 정도를 강조한 경우",
    "E" to "평가나 감정이 담긴 말을 덧붙인 경우",
    "F" to "한쪽 입장의 이름으로 부른 경우",
    "G" to "전망이나 추정을 확신·단정으로 쓴 경우"
)

/** 하단 주석의 ? 버튼. */
@Composable
internal fun LabelGuideButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).semantics { contentDescription = "표현 분석 라벨 기준 보기" }
    ) {
        Text(
            "?",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Fl.InkDiff,
            modifier = Modifier.size(26.dp)
                .border(1.5.dp, Fl.InkDiff, CircleShape)
                .padding(top = 2.dp),
            textAlign = TextAlign.Center
        )
    }
}

/** ? 버튼으로 여는 라벨 기준 팝업. [highlightCodes]는 방금 본 카드의 범주다. */
@Composable
internal fun ExpressionLabelGuideDialog(highlightCodes: Set<String>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier.fillMaxWidth().heightIn(max = 640.dp)
                .background(Fl.Card, shape)
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("표현 분석 라벨 기준", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
            Text(
                "같은 사건을 다룬 다른 기사와 비교해, 단어 선택이 다른 부분을 7가지로 나눠 표시해요. " +
                    "라벨은 차이의 종류일 뿐, 옳고 그름을 판정하지 않아요.",
                fontSize = 13.sp, lineHeight = 20.sp, color = Fl.Ink2
            )
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                HorizontalDivider(color = Fl.Line)
                ExpressionLabelExamples.forEach { example ->
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(categoryLabel(example.code), fontSize = 14.sp,
                                fontWeight = FontWeight.Bold, color = Fl.InkDiff)
                            if (example.code in highlightCodes) {
                                TagPill("현재 기준", Fl.InkDiff, Fl.OnAccent)
                            }
                        }
                        Text(
                            buildAnnotatedString {
                                append("${example.baseline} → ")
                                withStyle(SpanStyle(color = Fl.Ink, fontWeight = FontWeight.SemiBold)) {
                                    append(example.marked)
                                }
                            },
                            fontSize = 13.sp, color = Fl.Muted
                        )
                    }
                    HorizontalDivider(color = Fl.Line)
                }
            }
            FilledPill("확인", onDismiss, Modifier.fillMaxWidth())
        }
    }
}
