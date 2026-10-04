package com.swpp.team5.frameless

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ScreenBackground = Color(0xFFF7F9FC)
private val Navy = Color(0xFF172A46)
private val Blue = Color(0xFF2F6FED)
private val LightBlue = Color(0xFFEAF1FF)
private val Green = Color(0xFF267A55)
private val LightGreen = Color(0xFFE8F5EE)
private val Orange = Color(0xFFAD5A13)
private val LightOrange = Color(0xFFFFF2E5)

private enum class AppScreen {
    INPUT,
    LOADING,
    OVERVIEW,
    CLAIM_DETAIL
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ScreenBackground
                ) {
                    FrameLESSApp()
                }
            }
        }
    }
}

@Composable
private fun FrameLESSApp() {
    val context = LocalContext.current
    val teamCodeStore = remember(context) { TeamCodeStore(context.applicationContext) }
    var screen by remember { mutableStateOf(AppScreen.INPUT) }
    var articleUrl by rememberSaveable { mutableStateOf("") }
    var teamCode by remember { mutableStateOf(teamCodeStore.load()) }
    var teamCodeError by remember { mutableStateOf<String?>(null) }
    var urlError by rememberSaveable { mutableStateOf<String?>(null) }
    var requestError by remember { mutableStateOf<String?>(null) }
    var loadingMessage by remember { mutableStateOf("") }
    var analysis by remember { mutableStateOf<AnalysisResult?>(null) }
    var selectedClaim by remember { mutableStateOf<ClaimPreview?>(null) }

    LaunchedEffect(screen) {
        if (screen == AppScreen.LOADING) {
            loadingMessage = "기사에서 핵심 사건을 파악하고 있어요."
            val progress = launch {
                delay(4_000)
                loadingMessage = "같은 사건을 다룬 보도를 찾고 있어요."
                delay(8_000)
                loadingMessage = "원문 문장과 표현을 비교하고 있어요. 잠시만 기다려줘."
            }
            try {
                analysis = AnalysisApi.analyze(articleUrl, teamCode)
                if (AnalysisApi.requiresTeamCode) teamCodeStore.save(teamCode)
                screen = AppScreen.OVERVIEW
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: TeamCodeException) {
                teamCodeStore.clear()
                teamCode = ""
                teamCodeError = error.message
                screen = AppScreen.INPUT
            } catch (error: Exception) {
                requestError = error.message ?: "기사를 분석하지 못했어. 다시 시도해줘."
                screen = AppScreen.INPUT
            } finally {
                progress.cancel()
            }
        }
    }

    when (screen) {
        AppScreen.INPUT -> {
            InputScreen(
                articleUrl = articleUrl,
                teamCode = teamCode,
                teamCodeError = teamCodeError,
                urlError = urlError,
                requestError = requestError,
                onArticleUrlChange = {
                    articleUrl = it
                    urlError = null
                    requestError = null
                },
                onTeamCodeChange = {
                    teamCode = it
                    if (it.isEmpty()) teamCodeStore.clear()
                    teamCodeError = null
                    requestError = null
                },
                onAnalyze = {
                    val trimmedUrl = articleUrl.trim()
                    requestError = null

                    if (!isValidArticleUrl(trimmedUrl)) {
                        urlError = "http:// 또는 https://로 시작하는 기사 주소를 입력해줘."
                    } else if (AnalysisApi.requiresTeamCode && teamCode.isBlank()) {
                        teamCodeError = "팀 테스트 코드를 입력해줘."
                    } else {
                        articleUrl = trimmedUrl
                        analysis = null
                        selectedClaim = null
                        screen = AppScreen.LOADING
                    }
                }
            )
        }

        AppScreen.LOADING -> LoadingScreen(loadingMessage)

        AppScreen.OVERVIEW -> {
            analysis?.let { result ->
                ComparisonOverviewScreen(
                    analysis = result,
                    onClaimSelected = {
                        selectedClaim = it
                        screen = AppScreen.CLAIM_DETAIL
                    },
                    onStartOver = {
                        articleUrl = ""
                        urlError = null
                        requestError = null
                        analysis = null
                        selectedClaim = null
                        screen = AppScreen.INPUT
                    }
                )
            }
        }

        AppScreen.CLAIM_DETAIL -> {
            selectedClaim?.let { claim ->
                ClaimDetailScreen(
                    claim = claim,
                    onBack = { screen = AppScreen.OVERVIEW }
                )
            }
        }
    }
}

@Composable
private fun InputScreen(
    articleUrl: String,
    teamCode: String,
    teamCodeError: String?,
    urlError: String?,
    requestError: String?,
    onArticleUrlChange: (String) -> Unit,
    onTeamCodeChange: (String) -> Unit,
    onAnalyze: () -> Unit
) {
    Scaffold(containerColor = ScreenBackground) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "FrameLESS",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Blue
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "기사 하나로,\n다른 보도를 함께 보기",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Navy
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "읽고 있는 뉴스 기사를 넣으면, 같은 사건을 다룬 보도에서 공통으로 언급된 내용과 서로 다르게 강조한 부분을 비교해줘.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF516072)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "비교할 기사 입력",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = articleUrl,
                        onValueChange = onArticleUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("뉴스 기사 URL") },
                        placeholder = {
                            Text("https://news.example.com/article")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(onGo = { onAnalyze() }),
                        isError = urlError != null,
                        supportingText = {
                            if (urlError != null) {
                                Text(urlError)
                            }
                        }
                    )

                    if (AnalysisApi.requiresTeamCode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = teamCode,
                            onValueChange = onTeamCodeChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("팀 테스트 코드") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            isError = teamCodeError != null,
                            supportingText = {
                                Text(teamCodeError ?: "인증에 성공한 코드는 이 기기에 저장돼요. 입력칸을 비우면 삭제됩니다.")
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onAnalyze,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Blue
                        )
                    ) {
                        Text("이 기사 비교하기")
                    }

                    if (requestError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = requestError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "FrameLESS는 어느 언론사가 옳은지 판정하지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6D7885)
            )
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Blue)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "비교 결과를 준비하고 있어요",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Navy
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF516072)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComparisonOverviewScreen(
    analysis: AnalysisResult,
    onClaimSelected: (ClaimPreview) -> Unit,
    onStartOver: () -> Unit
) {
    val sharedClaims = analysis.claims.filter { it.type == ClaimType.SHARED }
    val differentClaims = analysis.claims.filter { it.type == ClaimType.DIFFERENT }

    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "비교 결과",
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                },
                actions = {
                    TextButton(onClick = onStartOver) {
                        Text("새 기사")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "동일 사건 보도 비교",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Navy
            )

            Text(
                text = "찾은 보도에서 실제로 대응하는 문장만 보여줍니다. 원문 링크에서 맥락을 확인해 주세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF516072)
            )

            InputArticleCard(analysis)

            MultiSourceBriefCard(analysis)

            SectionTitle("여러 출처에 공통으로 나타난 보도")

            sharedClaims.forEach { claim ->
                ClaimSummaryCard(
                    claim = claim,
                    onClick = { onClaimSelected(claim) }
                )
            }
            if (sharedClaims.isEmpty()) {
                Text("다른 출처에서 같은 주장에 대응하는 문장을 찾지 못했어요.")
            }

            SectionTitle("반박하거나 다르게 해석한 내용")

            differentClaims.forEach { claim ->
                ClaimSummaryCard(
                    claim = claim,
                    onClick = { onClaimSelected(claim) }
                )
            }
            if (differentClaims.isEmpty()) {
                Text("반박이나 해석 차이를 뒷받침하는 문장을 찾지 못했어요.")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClaimDetailScreen(
    claim: ClaimPreview,
    onBack: () -> Unit
) {
    val category = if (claim.type == ClaimType.SHARED) {
        "여러 출처에 공통으로 나타난 보도"
    } else {
        "반박 또는 해석 차이"
    }

    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "문장 비교",
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("뒤로")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = category,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Blue
            )

            Text(
                text = claim.summary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Navy
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "비교 기준",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Navy
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = claim.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF516072)
                    )
                }
            }

            SectionTitle("연결된 원문 문장")

            Text(
                text = "각 출처의 해당 문장을 직접 비교할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF516072)
            )

            claim.passages.forEachIndexed { index, passage ->
                SourcePassageCard(
                    label = if (index == 0) "입력 기사" else "다른 출처",
                    passage = passage,
                    backgroundColor = if (index == 0) LightBlue else Color.White,
                    accentColor = if (index == 0) Blue else Orange
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = LightGreen
                )
            ) {
                Text(
                    text = "이 화면은 어떤 보도가 맞는지 판정하지 않습니다. 사용자가 원문 문장과 출처를 직접 확인할 수 있도록 비교 근거를 보여줍니다.",
                    modifier = Modifier.padding(18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Navy
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InputArticleCard(analysis: AnalysisResult) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "비교 기준 기사",
                style = MaterialTheme.typography.labelLarge,
                color = Blue,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = analysis.sourceTitle,
                style = MaterialTheme.typography.titleMedium,
                color = Navy,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = analysis.sourceUrl,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF667085),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = { uriHandler.openUri(analysis.sourceUrl) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("입력 기사 원문 열기")
            }
        }
    }
}

@Composable
private fun MultiSourceBriefCard(analysis: AnalysisResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = LightGreen
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "분석한 사건",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Green
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = analysis.coreEvent.ifBlank { "핵심 사건을 요약하지 못했어요." },
                style = MaterialTheme.typography.bodyMedium,
                color = Navy
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (analysis.relatedArticleCount == 0) {
                    "같은 사건을 다룬 다른 기사를 찾지 못했어요. 다른 기사로 다시 시도해 주세요."
                } else {
                    "입력 기사 외에 같은 사건으로 분류된 기사 ${analysis.relatedArticleCount}개를 분석했습니다. 아래 항목을 눌러 원문을 확인하세요."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF516072)
            )
        }
    }
}

@Composable
private fun ClaimSummaryCard(
    claim: ClaimPreview,
    onClick: () -> Unit
) {
    val isShared = claim.type == ClaimType.SHARED
    val accentColor = if (isShared) Green else Orange
    val backgroundColor = if (isShared) LightGreen else LightOrange
    val label = if (isShared) {
        "공통 보도"
    } else {
        "반박·해석 차이"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = claim.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = Navy
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "눌러서 원문 문장 비교하기 →",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

@Composable
private fun SourcePassageCard(
    label: String,
    passage: SourcePassage,
    backgroundColor: Color,
    accentColor: Color
) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = passage.outlet,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Navy
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "“${passage.excerpt}”",
                style = MaterialTheme.typography.bodyMedium,
                color = Navy
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (passage.url != null) {
                OutlinedButton(
                    onClick = { uriHandler.openUri(passage.url) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("이 출처의 원문 열기")
                }
            } else {
                Text(
                    text = "이 문장의 원문 링크는 제공되지 않았어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6D7885)
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Navy
    )
}

private fun isValidArticleUrl(url: String): Boolean {
    val uri = Uri.parse(url)

    return (uri.scheme == "http" || uri.scheme == "https") &&
            !uri.host.isNullOrBlank()
}
