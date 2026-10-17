package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.ui.theme.Caption
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.FlCard

/** 분석 중 화면. step: 0 사건 파악, 1 보도 찾기, 2 문장 비교 */
@Composable
internal fun LoadingScreen(url: String, step: Int) {
    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        FlCard(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BrandRow()
                Text(
                    text = "같은 사건을 다룬\n다른 보도를 찾고 있어요",
                    fontFamily = Fl.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    color = Fl.Ink
                )
                Caption(url)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Fl.Accent,
                    trackColor = Fl.Line
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("입력 기사의 핵심 사건 파악", "같은 사건을 다룬 보도 찾기", "문장끼리 대응 관계 비교").forEachIndexed { i, label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        when {
                                            i < step -> Fl.TintSame
                                            i == step -> Fl.Accent
                                            else -> Fl.Line
                                        },
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = label + if (i < step) " · 완료" else "",
                                fontSize = 14.sp,
                                fontWeight = if (i == step) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (i <= step) Fl.Ink else Fl.Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
