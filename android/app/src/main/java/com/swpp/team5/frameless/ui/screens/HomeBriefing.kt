package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.R
import com.swpp.team5.frameless.ui.theme.Fl
import java.util.Locale

internal enum class WeatherCondition(val label: String, val icon: Int) {
    CLEAR("맑음", R.drawable.ic_portal_sun),
    CLOUDY("흐림", R.drawable.ic_portal_cloud),
    RAIN("비", R.drawable.ic_portal_cloud_rain),
    SNOW("눈", R.drawable.ic_portal_snowflake)
}

internal data class WeatherBriefing(
    val city: String = "서울",
    val temperature: String = "8°",
    val condition: WeatherCondition = WeatherCondition.CLEAR,
    val highLow: String = "12° / 2°"
)

internal enum class MarketDirection { UP, DOWN, FLAT }

internal fun marketDirection(change: Double): MarketDirection = when {
    change > 0 -> MarketDirection.UP
    change < 0 -> MarketDirection.DOWN
    else -> MarketDirection.FLAT
}

internal fun marketChangeLabel(change: Double): String =
    if (change == 0.0) "0.00%" else String.format(Locale.US, "%+.2f%%", change)

internal data class MarketBriefing(
    val label: String,
    val value: String,
    val changePercent: Double,
    val currency: Boolean = false
)

private val previewMarkets = listOf(
    MarketBriefing("KOSPI", "2,591.05", 0.32),
    MarketBriefing("원·달러", "1,397.40", 0.12, currency = true),
    MarketBriefing("NASDAQ", "19,003.65", -0.60),
    MarketBriefing("S&P 500", "5,948.71", -0.29)
)

/** Presentation fixtures until weather/market providers are connected. */
@Composable
internal fun HomeBriefingStrip(
    weather: WeatherBriefing = WeatherBriefing(),
    markets: List<MarketBriefing> = previewMarkets
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 18.dp)) {
        item { WeatherBriefingCard(weather) }
        items(markets, key = MarketBriefing::label) { MarketBriefingCard(it) }
    }
}

@Composable
private fun briefingTile() = Modifier.width(132.dp).height(88.dp)
    .background(Fl.Card, RoundedCornerShape(12.dp))
    .border(1.dp, Fl.Line, RoundedCornerShape(12.dp))
    .padding(horizontal = 12.dp, vertical = 10.dp)

@Composable
internal fun WeatherBriefingCard(weather: WeatherBriefing) {
    val accent = when (weather.condition) {
        WeatherCondition.CLEAR -> if (Fl.IsDark) Color(0xFFF4C663) else Color(0xFFBE8424)
        WeatherCondition.CLOUDY -> Fl.Muted
        WeatherCondition.RAIN, WeatherCondition.SNOW -> if (Fl.IsDark) Color(0xFF95B8F2) else Color(0xFF4A77B8)
    }
    Column(briefingTile().testTag("weather-briefing"), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(weather.city, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Fl.Ink2)
            Spacer(Modifier.weight(1f))
            Icon(painterResource(weather.condition.icon), contentDescription = null,
                tint = accent, modifier = Modifier.size(23.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(weather.temperature, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Fl.Ink)
            Text(weather.condition.label, fontSize = 11.sp, color = Fl.Ink2,
                modifier = Modifier.padding(start = 8.dp))
        }
        Text(weather.highLow, fontSize = 10.sp, color = Fl.Muted)
    }
}

@Composable
internal fun MarketBriefingCard(market: MarketBriefing) {
    val direction = marketDirection(market.changePercent)
    val changeColor = when (direction) {
        MarketDirection.UP -> if (Fl.IsDark) Color(0xFFFF8176) else Color(0xFFCD4641)
        MarketDirection.DOWN -> if (Fl.IsDark) Color(0xFF8DB4FF) else Color(0xFF386FBC)
        MarketDirection.FLAT -> Fl.Muted
    }
    Column(briefingTile(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(if (market.currency) R.drawable.ic_portal_exchange else R.drawable.ic_portal_chart_line),
                contentDescription = null, tint = Fl.Muted, modifier = Modifier.size(16.dp))
            Text(market.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Fl.Ink2,
                modifier = Modifier.padding(start = 6.dp))
        }
        Text(market.value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Fl.Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (direction != MarketDirection.FLAT) {
                Icon(painterResource(if (direction == MarketDirection.UP) R.drawable.ic_portal_triangle_up
                    else R.drawable.ic_portal_triangle_down),
                    contentDescription = if (direction == MarketDirection.UP) "상승" else "하락",
                    tint = changeColor, modifier = Modifier.size(9.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(marketChangeLabel(market.changePercent), color = changeColor,
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
