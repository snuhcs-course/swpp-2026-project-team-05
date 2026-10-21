package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.ui.theme.FilledPill
import com.swpp.team5.frameless.ui.theme.Fl

@Composable
internal fun LoginScreen(username: String, password: String, loading: Boolean, error: String?, onUsername: (String)->Unit, onPassword: (String)->Unit, onLogin: ()->Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        BrandRow(); Spacer(Modifier.height(32.dp))
        Text("분석 기록을\n이어 보세요", fontFamily = Fl.Serif, fontSize = 34.sp, color = Fl.Ink)
        Spacer(Modifier.height(12.dp)); Text("로그인하면 기기와 관계없이 이전 분석을 다시 열 수 있어요.", color = Fl.Muted)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(username, onUsername, Modifier.fillMaxWidth(), label = { Text("아이디") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, onPassword, Modifier.fillMaxWidth(), label = { Text("비밀번호") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        if (error != null) Text(error, color = Fl.Danger, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(18.dp)); FilledPill(if (loading) "로그인 중…" else "로그인", onLogin, Modifier.fillMaxWidth())
    }
}
