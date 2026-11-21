package com.swpp.team5.frameless.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.ui.theme.FilledPill
import com.swpp.team5.frameless.ui.theme.Fl

private val CoachScrim = Color(0xA80E111C)

/** 강조 영역 중심이 화면 위쪽 절반에 있으면 말풍선을 그 아래에 둔다. */
internal fun coachTooltipGoesBelow(spotlightCenterY: Float, containerHeight: Float): Boolean =
    containerHeight <= 0f || spotlightCenterY < containerHeight / 2f

/** 루트 좌표가 한 프레임 늦게 갱신돼도 Compose padding에는 음수가 전달되지 않는다. */
internal fun safeCoachPadding(distance: Float): Float = distance.coerceAtLeast(0f)

/**
 * 화면 전체를 어둡게 덮고 [spotlightInRoot] 영역만 밝게 남기는 코치마크.
 *
 * 강조 영역을 누르면 [onSpotlightTap]이 호출되고, 나머지 영역의 터치는 막는다.
 * 좌표는 루트 기준이며 오버레이 자신의 위치를 빼서 그린다.
 */
@Composable
internal fun CoachMarkOverlay(
    spotlightInRoot: Rect?,
    stepLabel: String,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    onSpotlightTap: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val padding = with(density) { 6.dp.toPx() }
    val radius = with(density) { 14.dp.toPx() }
    val spotlight = spotlightInRoot?.translate(-origin.x, -origin.y)?.inflate(padding)
    val currentTap by rememberUpdatedState(onSpotlightTap)

    Box(
        modifier.fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInRoot()
                size = it.size
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                drawRect(CoachScrim)
                spotlight?.let {
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = it.topLeft,
                        size = it.size,
                        cornerRadius = CornerRadius(radius),
                        blendMode = BlendMode.Clear
                    )
                }
            }
            .pointerInput(spotlight) {
                detectTapGestures { position ->
                    if (spotlight?.contains(position) == true) currentTap?.invoke()
                }
            }
    ) {
        val gap = 12.dp
        val tooltipModifier = when {
            spotlight == null -> Modifier.align(Alignment.Center)
            coachTooltipGoesBelow(spotlight.center.y, size.height.toFloat()) ->
                Modifier.align(Alignment.TopCenter)
                    .padding(
                        top = with(density) { safeCoachPadding(spotlight.bottom).toDp() } + gap
                    )
            else -> Modifier.align(Alignment.BottomCenter)
                .padding(
                    bottom = with(density) {
                        safeCoachPadding(size.height - spotlight.top).toDp()
                    } + gap
                )
        }
        CoachTooltip(
            stepLabel = stepLabel,
            title = title,
            body = body,
            primaryLabel = primaryLabel,
            onPrimary = onPrimary,
            secondaryLabel = secondaryLabel,
            onSecondary = onSecondary,
            modifier = tooltipModifier.padding(horizontal = 16.dp)
        )
    }
}

/** 코치마크 말풍선. 비교 시트 안의 인라인 안내에도 같은 모양을 쓴다. */
@Composable
internal fun CoachTooltip(
    stepLabel: String,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {}
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.widthIn(max = 420.dp).fillMaxWidth()
            .shadow(12.dp, shape)
            .background(Fl.Card, shape)
            .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stepLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Fl.InkDiff)
        Text(title, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
        Text(body, fontSize = 14.sp, lineHeight = 21.sp, color = Fl.Ink2)
        extra()
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (secondaryLabel != null) {
                Text(
                    secondaryLabel,
                    fontSize = 14.sp,
                    color = Fl.Muted,
                    modifier = Modifier.heightIn(min = 48.dp)
                        .clickable(onClick = onSecondary)
                        .semantics { role = Role.Button }
                        .padding(horizontal = 4.dp, vertical = 14.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            FilledPill(primaryLabel, onPrimary)
        }
    }
}
