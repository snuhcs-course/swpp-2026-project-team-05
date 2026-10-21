package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.ui.theme.Caption
import com.swpp.team5.frameless.ui.theme.FilledPill
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.FlCard
import com.swpp.team5.frameless.data.AnalysisHistoryItem
import com.swpp.team5.frameless.ui.viewmodel.AnalysisHistoryState
import java.net.URI
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 기사 URL 입력 화면 */
@Composable
internal fun ImportScreen(
    url: String,
    urlError: String?,
    requestError: String?,
    history: AnalysisHistoryState,
    onUrlChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onOpenHistory: (AnalysisHistoryItem) -> Unit,
    onRefreshHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            BrandRow()
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "같은 소식,\n다른 문장",
                fontFamily = Fl.Serif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp,
                lineHeight = 44.sp,
                color = Fl.Ink
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "읽던 기사의 주소를 넣으면 같은 사건을 다룬 다른 보도에서 대응하는 문장을 찾아 나란히 보여드려요.",
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = Fl.Muted
            )
            Spacer(modifier = Modifier.height(28.dp))

            FlCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("비교할 기사", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Fl.Ink)
                    OutlinedTextField(
                        value = url,
                        onValueChange = onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        placeholder = { Text("https://news.example.com/article", color = Fl.Muted) },
                        isError = urlError != null,
                        supportingText = { if (urlError != null) Text(urlError) },
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(onGo = { onAnalyze() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Fl.Accent,
                            unfocusedBorderColor = Fl.Line,
                            cursorColor = Fl.Accent,
                            focusedContainerColor = Fl.Card,
                            unfocusedContainerColor = Fl.Card
                        )
                    )
                    FilledPill(text = "비교하기", onClick = onAnalyze, modifier = Modifier.fillMaxWidth())

                    if (requestError != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Fl.Danger.copy(alpha = 0.07f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("분석하지 못했어요", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Fl.Danger)
                            Text(requestError, fontSize = 13.sp, lineHeight = 19.sp, color = Fl.Ink2)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            AnalysisHistorySection(
                history = history,
                onOpenHistory = onOpenHistory,
                onRefreshHistory = onRefreshHistory
            )

            Spacer(modifier = Modifier.height(20.dp))
            listOf(
                "어느 언론사가 옳은지 판정하지 않아요",
                "점수나 정치 성향 라벨을 붙이지 않아요",
                "모든 문장은 기사 제목과 원문 링크로 확인할 수 있어요"
            ).forEach {
                Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(4.dp).background(Fl.Muted, RoundedCornerShape(50)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Caption(it)
                }
            }
        }
    }
}

@Composable
private fun AnalysisHistorySection(
    history: AnalysisHistoryState,
    onOpenHistory: (AnalysisHistoryItem) -> Unit,
    onRefreshHistory: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("최근 분석", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Fl.Ink)
        Text(
            "새로고침",
            modifier = Modifier.clickable(onClick = onRefreshHistory).padding(4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Fl.Accent
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    when {
        history.isLoading && history.items.isEmpty() -> {
            Caption("이전 분석을 불러오는 중이에요.")
        }
        history.error != null -> {
            Text(
                text = "이전 분석을 불러오지 못했어요. 다시 시도해 주세요.",
                modifier = Modifier.clickable(onClick = onRefreshHistory),
                fontSize = 13.sp,
                color = Fl.Muted
            )
        }
        history.items.isEmpty() -> {
            Caption("아직 저장된 분석이 없어요.")
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                history.items.forEach { item ->
                    AnalysisHistoryItemRow(item = item, onClick = { onOpenHistory(item) })
                }
            }
        }
    }
}

@Composable
private fun AnalysisHistoryItemRow(item: AnalysisHistoryItem, onClick: () -> Unit) {
    FlCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = item.sourceTitle.ifBlank { displayHistoryHost(item.sourceUrl) },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Fl.Ink
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = "${displayHistoryHost(item.sourceUrl)} · ${displayHistoryTime(item.updatedAt)} · ${historyStatusLabel(item.executionStatus)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                color = Fl.Muted
            )
        }
    }
}

private fun displayHistoryHost(url: String): String = try {
    URI(url).host?.removePrefix("www.") ?: url
} catch (_: Exception) {
    url
}

private fun displayHistoryTime(raw: String): String = try {
    OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("M월 d일 · HH:mm", Locale.KOREAN))
} catch (_: Exception) {
    "이전 분석"
}

private fun historyStatusLabel(status: String): String = when (status) {
    "partial" -> "일부 결과"
    else -> "분석 완료"
}

/** 로고 줄: 형광 띠가 들어간 작은 렌즈 표시 + FrameLESS */
@Composable
internal fun BrandRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(Fl.Accent, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(modifier = Modifier.width(13.dp).height(2.5.dp).background(Fl.TintNone, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.width(13.dp).height(2.5.dp).background(Fl.TintSame, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier.width(9.dp).height(2.5.dp).background(Fl.TintDiff, RoundedCornerShape(2.dp)))
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text("FrameLESS", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
    }
}
