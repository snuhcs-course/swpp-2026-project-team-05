package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.ui.theme.Caption
import com.swpp.team5.frameless.ui.theme.FilledPill
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.OutlinedPill
import java.net.URI

internal fun isValidMockArticleUrl(value: String): Boolean = try {
    val uri = URI(value.trim())
    (uri.scheme.equals("http", ignoreCase = true) ||
        uri.scheme.equals("https", ignoreCase = true)) && !uri.host.isNullOrBlank()
} catch (_: Exception) {
    false
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun UrlAnalysisMockSheet(onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var previewing by remember { mutableStateOf(false) }

    fun continueToPreview() {
        if (isValidMockArticleUrl(url)) {
            error = null
            previewing = true
        } else {
            error = "http:// 또는 https://로 시작하는 전체 기사 주소를 입력해 주세요."
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fl.Card) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = if (previewing) Alignment.CenterHorizontally else Alignment.Start
        ) {
            if (previewing) {
                CircularProgressIndicator(color = Fl.Accent, strokeWidth = 2.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    "기사 분석 기능을 준비하고 있어요",
                    fontFamily = Fl.Serif,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Fl.Ink
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "지금은 화면 흐름을 미리 보여주는 단계예요. 입력한 링크는 서버로 전송하거나 저장하지 않았어요.",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Fl.Muted
                )
                Spacer(Modifier.height(24.dp))
                OutlinedPill("검색으로 돌아가기", onDismiss)
            } else {
                Text(
                    "기사 링크로 분석하기",
                    fontFamily = Fl.Serif,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Fl.Ink
                )
                Spacer(Modifier.height(8.dp))
                Caption("공개된 뉴스 기사 주소를 입력해 앞으로 제공될 분석 흐름을 미리 확인해 보세요. 지금은 링크를 전송하거나 분석하지 않아요.")
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "기사 URL 입력"
                    },
                    singleLine = true,
                    label = { Text("기사 URL") },
                    placeholder = { Text("https://news.example.com/article") },
                    isError = error != null,
                    supportingText = {
                        Text(error ?: "HTTP와 HTTPS 링크만 입력할 수 있어요.")
                    },
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { continueToPreview() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Fl.Accent,
                        unfocusedBorderColor = Fl.Line,
                        cursorColor = Fl.Accent,
                        focusedContainerColor = Fl.Card,
                        unfocusedContainerColor = Fl.Card
                    )
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedPill("취소", onDismiss)
                    FilledPill("계속", ::continueToPreview)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
