package com.swpp.team5.frameless.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.ui.theme.FilledPill
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.OutlinedPill
import com.swpp.team5.frameless.ui.theme.PillShape
import kotlinx.coroutines.launch
import com.swpp.team5.frameless.ui.components.BrandRow

private const val OnboardingPages = 3

/**
 * 첫 실행 온보딩 3장. 피드의 ? 버튼으로 언제든 다시 볼 수 있다.
 * 1장: 앱의 관점(판정하지 않고 차이를 보여준다). 2장: 사용 순서 세 단계. 3장: 라벨 A~G 한 줄 설명.
 * 끝내기·건너뛰기·로그인 모두 [onFinish] 계열로 끝난다. 로그인한 상태면 로그인 버튼은 숨긴다.
 */
@Composable
internal fun OnboardingScreen(
    onFinish: () -> Unit,
    onBack: () -> Unit = onFinish,
    onLogin: () -> Unit,
    showLogin: Boolean = true
) {
    val pager = rememberPagerState(pageCount = { OnboardingPages })
    val scope = rememberCoroutineScope()
    BackHandler {
        if (pager.currentPage > 0) {
            scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
        } else {
            onBack()
        }
    }

    Column(Modifier.fillMaxSize().background(Fl.Card)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandRow(modifier = Modifier.weight(1f), compact = true)
            Text(
                "건너뛰기",
                fontSize = 14.sp,
                color = Fl.Muted,
                modifier = Modifier.heightIn(min = 48.dp)
                    .clickable(onClick = onFinish)
                    .semantics { role = Role.Button }
                    .padding(horizontal = 10.dp, vertical = 14.dp)
            )
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (page) {
                    0 -> PerspectivePage()
                    1 -> HowToPage()
                    else -> LabelsPage()
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PageDots(current = pager.currentPage)
            if (pager.currentPage < OnboardingPages - 1) {
                FilledPill("다음", {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }, Modifier.fillMaxWidth())
            } else {
                FilledPill("시작하기", onFinish, Modifier.fillMaxWidth())
                if (showLogin) OutlinedPill("로그인하러 가기", onLogin, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PerspectivePage() {
    Spacer(Modifier.height(8.dp))
    ExampleCard("조세일보 · 정치", "대통령은 개혁 과제를 둘러싼 ", "여권 내부의 반발과 이견", "을 비판했다.")
    Text("같은 대상, 다른 이름", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = Fl.InkDiff, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        textAlign = TextAlign.Center)
    ExampleCard("경기일보 · 정치", "대통령은 개혁에 따른 ", "반발에 편승하는 공인들", "을 향해 경고했다.")
    Spacer(Modifier.height(10.dp))
    Text("같은 사건,\n다른 표현", fontSize = 28.sp, lineHeight = 36.sp,
        fontWeight = FontWeight.Bold, color = Fl.Ink)
    Text(
        "FrameLESS는 기사가 편향됐다고 판정하지 않아요. 같은 사건을 다르게 표현한 부분을 보여줄 뿐이에요.",
        fontSize = 16.sp, lineHeight = 25.sp, color = Fl.Ink2
    )
}

@Composable
private fun ExampleCard(caption: String, before: String, marked: String, after: String) {
    val marker = Fl.TintDiff
    Column(
        Modifier.fillMaxWidth()
            .background(Fl.Bg, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(caption, fontSize = 12.sp, color = Fl.Muted)
        Text(
            buildAnnotatedString {
                append(before)
                withStyle(SpanStyle(background = marker, fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline)) { append(marked) }
                append(after)
            },
            fontFamily = Fl.Serif, fontSize = 16.sp, lineHeight = 27.sp, color = Fl.Ink
        )
    }
}

@Composable
private fun HowToPage() {
    Spacer(Modifier.height(8.dp))
    Text("이렇게 읽어요", fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
    Text("세 단계면 같은 사건을 다른 언론사가 어떻게 썼는지 볼 수 있어요.",
        fontSize = 16.sp, lineHeight = 25.sp, color = Fl.Ink2)
    Spacer(Modifier.height(4.dp))
    StepCard(1, "기사 고르기", "홈에서 정치·사회 기사를 골라 읽어요. 돋보기로 검색도 할 수 있어요.")
    StepCard(2, "표시된 표현 누르기", "보라색으로 표시된 표현을 누르면 어떤 차이가 있는지 알려줘요.")
    StepCard(3, "다른 기사와 비교하기", "같은 내용을 다른 언론사는 어떻게 썼는지 보고, 원문까지 확인해요.")
    Text("로그인하면 기사를 저장해 두고 나중에 다시 볼 수 있어요.",
        fontSize = 13.sp, lineHeight = 20.sp, color = Fl.Muted, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun LabelsPage() {
    Spacer(Modifier.height(8.dp))
    Text("7가지 표현 차이", fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
    Text("표시된 표현에는 아래 라벨이 붙어요. 차이의 종류일 뿐, 옳고 그름을 판정하지 않아요.",
        fontSize = 15.sp, lineHeight = 23.sp, color = Fl.Ink2)
    ExpressionLabelExamples.forEach { example ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(28.dp).background(Fl.TintDiff, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(example.code, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Fl.InkDiff)
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(categoryLabel(example.code), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
                Text(ExpressionLabelSummaries.getValue(example.code), fontSize = 13.sp, lineHeight = 18.sp,
                    color = Fl.Muted)
            }
        }
    }
}

@Composable
private fun StepCard(number: Int, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth()
            .border(1.dp, Fl.Line, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(36.dp).background(if (number == 2) Fl.TintDiff else Fl.Inset, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                color = if (number == 2) Fl.InkDiff else Fl.Accent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
            Text(body, fontSize = 14.sp, lineHeight = 21.sp, color = Fl.Ink2)
        }
    }
}

@Composable
private fun PageDots(current: Int) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp)
            .semantics { contentDescription = "${OnboardingPages}장 중 ${current + 1}번째" },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        repeat(OnboardingPages) { index ->
            Box(
                Modifier.height(6.dp).width(if (index == current) 20.dp else 6.dp)
                    .background(if (index == current) Fl.Accent else Fl.Line, PillShape)
            )
        }
    }
}
