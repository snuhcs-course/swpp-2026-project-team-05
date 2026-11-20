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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.ui.components.dismissKeyboardOnTap
import com.swpp.team5.frameless.ui.theme.Caption
import com.swpp.team5.frameless.ui.theme.FilledPill
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.OutlinedPill
import com.swpp.team5.frameless.ui.viewmodel.LinkAnalysisState

/**
 * The feed `+` flow: paste an article link, the server finds other outlets' articles on the same event and compares
 * their wording, then the reader opens the article. The view model checks the field; the server checks the link.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun LinkAnalysisSheet(
    state: LinkAnalysisState,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    fun submit() {
        if (!state.isRunning) onSubmit()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fl.Card) {
        Column(
            Modifier.fillMaxWidth().dismissKeyboardOnTap().padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = if (state.isRunning) Alignment.CenterHorizontally else Alignment.Start
        ) {
            if (state.isRunning) {
                CircularProgressIndicator(color = Fl.Accent, strokeWidth = 2.dp)
                Spacer(Modifier.height(20.dp))
                Text("기사를 분석하고 있어요", fontFamily = Fl.Serif, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, color = Fl.Ink)
                Spacer(Modifier.height(10.dp))
                Text(
                    "같은 사건을 다룬 다른 언론사 기사를 찾아 표현을 비교하고 있어요. 1~2분 정도 걸려요.",
                    fontSize = 13.sp, lineHeight = 20.sp, color = Fl.Muted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                Spacer(Modifier.height(24.dp))
                OutlinedPill("그만 기다리기", onDismiss)
            } else {
                Text("기사 링크로 분석하기", fontFamily = Fl.Serif, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, color = Fl.Ink)
                Spacer(Modifier.height(8.dp))
                Caption("뉴스 기사 주소를 넣으면 같은 사건을 다룬 다른 언론사 기사와 표현을 비교해 보여드려요.")
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onInput,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "기사 URL 입력" },
                    singleLine = true,
                    label = { Text("기사 URL") },
                    placeholder = { Text("https://n.news.naver.com/...") },
                    isError = state.error != null,
                    supportingText = { Text(state.error ?: "HTTP와 HTTPS 링크만 입력할 수 있어요.") },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Fl.Accent, unfocusedBorderColor = Fl.Line, cursorColor = Fl.Accent,
                        focusedContainerColor = Fl.Card, unfocusedContainerColor = Fl.Card
                    )
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedPill("취소", onDismiss)
                    FilledPill("분석하기", ::submit)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
