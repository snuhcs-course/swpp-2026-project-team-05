package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.components.dismissKeyboardOnTap
import com.swpp.team5.frameless.ui.components.edgeSwipeBack

@Composable
internal fun LoginScreen(
    identifier: String,
    password: String,
    loading: Boolean,
    error: String?,
    onIdentifier: (String) -> Unit,
    onPassword: (String) -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    AuthFrame(onBack = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandRow()
            Spacer(Modifier.weight(1f))
            Text(
                "닫기",
                fontSize = 13.sp,
                color = Fl.Muted,
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.height(42.dp))
        Text("다른 보도를\n함께 읽는 뉴스", fontFamily = Fl.Serif, fontWeight = FontWeight.Bold,
            fontSize = 30.sp, lineHeight = 38.sp, color = Fl.Ink)
        Spacer(Modifier.height(10.dp))
        Text("좋아하는 언론사의 기사를 읽고, 표현의 차이가 궁금할 때만 비교해 보세요.",
            fontSize = 13.5.sp, lineHeight = 21.sp, color = Fl.Muted)
        Spacer(Modifier.height(30.dp))
        OutlinedTextField(
            value = identifier,
            onValueChange = onIdentifier,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("이메일 또는 사용자 이름") },
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
            colors = authFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(password, onPassword, "비밀번호", ImeAction.Done, onLogin)
        if (error != null) Text(error, color = Fl.Danger, fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(20.dp))
        AuthPrimaryButton(if (loading) "로그인 중…" else "로그인", loading, onLogin)
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("계정이 없나요? ", fontSize = 13.sp, color = Fl.Muted)
            Text("회원가입", fontSize = 13.sp, color = Fl.Accent, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onRegister))
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                "로그인 없이 둘러보기",
                fontSize = 13.sp,
                color = Fl.Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp)
            )
        }
    }
}

@Composable
internal fun RegistrationScreen(
    email: String,
    username: String,
    password: String,
    confirmation: String,
    loading: Boolean,
    error: String?,
    fieldErrors: Map<String, String>,
    onEmail: (String) -> Unit,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirmation: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    AuthFrame(onBack = onBack) {
        Text("←  로그인", color = Fl.Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
        Spacer(Modifier.height(24.dp))
        Text("회원가입", fontFamily = Fl.Serif, fontWeight = FontWeight.Bold,
            fontSize = 32.sp, color = Fl.Ink)
        Spacer(Modifier.height(8.dp))
        Text("FrameLESS의 기사와 저장된 분석을 한 계정에서 이어 보세요.",
            fontSize = 14.sp, lineHeight = 21.sp, color = Fl.Muted)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            email, onEmail, Modifier.fillMaxWidth(), label = { Text("이메일") }, singleLine = true,
            isError = fieldErrors.containsKey("email"),
            shape = RoundedCornerShape(13.dp),
            colors = authFieldColors(),
            supportingText = if (fieldErrors["email"] != null) {
                { Text(fieldErrors.getValue("email")) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            username, onUsername, Modifier.fillMaxWidth(), label = { Text("사용자 이름") }, singleLine = true,
            isError = fieldErrors.containsKey("username"),
            shape = RoundedCornerShape(13.dp),
            colors = authFieldColors(),
            supportingText = if (fieldErrors["username"] != null) {
                { Text(fieldErrors.getValue("username")) }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(8.dp))
        PasswordField(password, onPassword, "비밀번호", ImeAction.Next, onSubmit,
            fieldErrors["password"])
        Spacer(Modifier.height(8.dp))
        PasswordField(confirmation, onConfirmation, "비밀번호 확인", ImeAction.Done, onSubmit,
            fieldErrors["password_confirmation"])
        Text("10자 이상 · 너무 흔한 비밀번호와 숫자로만 된 비밀번호는 사용할 수 없어요.",
            fontSize = 11.sp, lineHeight = 17.sp, color = Fl.Muted, modifier = Modifier.padding(top = 8.dp))
        if (error != null && fieldErrors.isEmpty()) Text(error, color = Fl.Danger, fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(20.dp))
        AuthPrimaryButton(if (loading) "계정 만드는 중…" else "계정 만들기", loading, onSubmit)
    }
}

@Composable
private fun AuthFrame(onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Fl.Bg).dismissKeyboardOnTap()
            .edgeSwipeBack(onBack = onBack)
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) { content() }
    }
}

@Composable
private fun PasswordField(value: String, onValue: (String) -> Unit, label: String,
                          imeAction: ImeAction, onDone: () -> Unit, error: String? = null) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(13.dp),
        colors = authFieldColors(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            Text(if (visible) "숨김" else "보기", fontSize = 12.sp, color = Fl.Accent,
                modifier = Modifier.clickable { visible = !visible }.padding(8.dp))
        },
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() })
    )
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Fl.Accent,
    unfocusedBorderColor = Fl.Line,
    focusedLabelColor = Fl.Accent,
    unfocusedLabelColor = Fl.Muted,
    cursorColor = Fl.Accent,
    focusedContainerColor = Fl.Card,
    unfocusedContainerColor = Fl.Card
)

@Composable
private fun AuthPrimaryButton(label: String, loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(50.dp)
            .background(if (loading) Color(0xFF6D7483) else Fl.Accent, RoundedCornerShape(14.dp))
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Fl.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
