package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.swpp.team5.frameless.ui.components.BrandRow
import com.swpp.team5.frameless.R

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
    onDismiss: () -> Unit = {},
    coolingDown: Boolean = false
) {
    AuthFrame(onBack = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandRow()
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("닫기", fontSize = 13.sp, color = Fl.Muted) }
        }
        Spacer(Modifier.height(34.dp))
        Text("다른 보도를\n함께 읽는 뉴스", fontFamily = Fl.Serif, fontWeight = FontWeight.Bold,
            fontSize = 28.sp, lineHeight = 37.sp, color = Fl.Ink)
        Spacer(Modifier.height(10.dp))
        Text("관심 언론사의 뉴스와 저장한 기사를 한 계정에서 이어 보세요.",
            fontSize = 13.5.sp, lineHeight = 21.sp, color = Fl.Muted)
        Spacer(Modifier.height(30.dp))
        OutlinedTextField(
            value = identifier,
            onValueChange = onIdentifier,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("이메일 또는 사용자 이름") },
            singleLine = true,
            enabled = !loading,
            shape = RoundedCornerShape(13.dp),
            colors = authFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(password, onPassword, "비밀번호", ImeAction.Done, onLogin, enabled = !loading)
        if (error != null) Text(error, color = Fl.Danger, fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(20.dp))
        AuthPrimaryButton(if (loading) "로그인 중…" else "로그인", loading, onLogin, coolingDown)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Text("계정이 없나요? ", fontSize = 13.sp, color = Fl.Muted)
            TextButton(onClick = onRegister, enabled = !loading) {
                Text("회원가입", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onDismiss) {
                Text("로그인 없이 둘러보기", fontSize = 13.sp, color = Fl.Muted)
            }
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
    onSubmit: () -> Unit,
    coolingDown: Boolean = false
) {
    AuthFrame(onBack = onBack) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_portal_arrow_back),
                    contentDescription = "로그인으로 돌아가기", tint = Fl.Ink)
            }
            Spacer(Modifier.weight(1f))
            BrandRow(compact = true)
        }
        Spacer(Modifier.height(24.dp))
        Text("회원가입", fontFamily = Fl.Serif, fontWeight = FontWeight.Bold,
            fontSize = 28.sp, color = Fl.Ink)
        Spacer(Modifier.height(8.dp))
        Text("FrameLESS의 기사와 저장된 분석을 한 계정에서 이어 보세요.",
            fontSize = 14.sp, lineHeight = 21.sp, color = Fl.Muted)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            email, onEmail, Modifier.fillMaxWidth(), label = { Text("이메일") }, singleLine = true,
            enabled = !loading,
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
            enabled = !loading,
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
            fieldErrors["password"], enabled = !loading)
        Spacer(Modifier.height(8.dp))
        PasswordField(confirmation, onConfirmation, "비밀번호 확인", ImeAction.Done, onSubmit,
            fieldErrors["password_confirmation"], enabled = !loading)
        Text("10자 이상 · 너무 흔한 비밀번호와 숫자로만 된 비밀번호는 사용할 수 없어요.",
            fontSize = 11.sp, lineHeight = 17.sp, color = Fl.Muted, modifier = Modifier.padding(top = 8.dp))
        if (error != null && fieldErrors.isEmpty()) Text(error, color = Fl.Danger, fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(20.dp))
        AuthPrimaryButton(if (loading) "계정 만드는 중…" else "계정 만들기", loading, onSubmit, coolingDown)
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
                          imeAction: ImeAction, onDone: () -> Unit, error: String? = null,
                          enabled: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        colors = authFieldColors(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }, enabled = enabled,
                modifier = Modifier.semantics {
                    contentDescription = if (visible) "비밀번호 숨기기" else "비밀번호 보기"
                }) {
                Text(if (visible) "숨김" else "보기", fontSize = 12.sp)
            }
        },
        isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { if (enabled) onDone() })
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
private fun AuthPrimaryButton(label: String, loading: Boolean, onClick: () -> Unit, coolingDown: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = !loading && !coolingDown,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("auth-primary-action"),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Fl.Accent,
            contentColor = Fl.OnAccent,
            disabledContainerColor = Fl.Inset,
            disabledContentColor = Fl.Muted
        )
    ) {
        if (loading) CircularProgressIndicator(Modifier.padding(end = 10.dp).size(16.dp),
            strokeWidth = 1.5.dp, color = Fl.Muted)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
