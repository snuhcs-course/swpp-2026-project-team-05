package com.swpp.team5.frameless.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.swpp.team5.frameless.data.NewsCategory

/**
 * Art direction for each news category. All artwork is drawn in Compose from these colours; nothing is
 * downloaded, bundled from a third party, or derived from a news photo. Each palette stays inside the
 * FrameLESS navy / lavender family but has its own temperature so the three sections are recognisable.
 */
internal data class CategoryPalette(
    /** Top and bottom of the artwork background. */
    val deep: Color,
    val dusk: Color,
    /** Light accent used for linework and highlights on the dark artwork. */
    val glow: Color,
    /** Accent used for text and small marks on the off-white page (AA contrast on [HomeTokens.OffWhite]). */
    val ink: Color
)

internal fun categoryPalette(category: NewsCategory): CategoryPalette = when (category) {
    NewsCategory.POLITICS -> CategoryPalette(
        deep = Color(0xFF161C2C), dusk = Color(0xFF2C2F55),
        glow = Color(0xFFCFC5F0), ink = Color(0xFF5B4A93)
    )
    NewsCategory.SOCIETY -> CategoryPalette(
        deep = Color(0xFF15202F), dusk = Color(0xFF29405E),
        glow = Color(0xFFBCD0EE), ink = Color(0xFF34548A)
    )
    NewsCategory.ECONOMY -> CategoryPalette(
        deep = Color(0xFF1C1828), dusk = Color(0xFF41304F),
        glow = Color(0xFFE6CBE6), ink = Color(0xFF7A4878)
    )
}

/**
 * Full-bleed cover illustration for a category. Purely decorative: it is removed from the accessibility tree,
 * and it never encodes data (no values, counts or direction of any real trend).
 */
@Composable
internal fun CategoryCoverArt(category: NewsCategory, modifier: Modifier = Modifier) {
    val palette = categoryPalette(category)
    Canvas(modifier.clearAndSetSemantics { }) {
        drawRect(Brush.verticalGradient(listOf(palette.dusk, palette.deep)))
        when (category) {
            NewsCategory.POLITICS -> drawChamber(palette)
            NewsCategory.SOCIETY -> drawCommons(palette)
            NewsCategory.ECONOMY -> drawMarket(palette)
        }
    }
}

/**
 * "Chamber": concentric seating rows of an assembly hall arc behind a domed building on a stepped plinth.
 */
private fun DrawScope.drawChamber(p: CategoryPalette) {
    val w = size.width
    val h = size.height
    val cx = w * 0.70f
    val baseY = h * 0.60f

    // Faint moon / spotlight behind the dome.
    drawCircle(
        Brush.radialGradient(listOf(p.glow.copy(alpha = 0.30f), Color.Transparent),
            center = Offset(cx, h * 0.26f), radius = h * 0.55f),
        radius = h * 0.55f, center = Offset(cx, h * 0.26f)
    )

    // Seating rows: dashed half rings, nearer rows brighter.
    val rows = 6
    for (i in 0 until rows) {
        val r = w * (0.20f + i * 0.085f)
        val dash = (5f + i * 2.2f) * density
        drawArc(
            color = p.glow.copy(alpha = 0.42f - i * 0.055f),
            startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(cx - r, baseY - r), size = Size(r * 2, r * 2),
            style = Stroke(
                width = (2.4f - i * 0.2f) * density,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f), i * 3f)
            )
        )
    }

    // Plinth steps.
    listOf(0.30f, 0.25f, 0.20f).forEachIndexed { i, half ->
        val top = baseY + i * h * 0.035f
        drawRect(p.glow.copy(alpha = 0.55f + i * 0.12f), Offset(cx - w * half, top), Size(w * half * 2, h * 0.03f))
    }
    // Colonnade.
    val colTop = baseY - h * 0.17f
    drawRect(p.deep.copy(alpha = 0.55f), Offset(cx - w * 0.17f, colTop), Size(w * 0.34f, h * 0.17f))
    for (i in 0..6) {
        val x = cx - w * 0.15f + i * w * 0.05f
        drawRoundRect(p.glow.copy(alpha = 0.88f), Offset(x - w * 0.009f, colTop + h * 0.012f),
            Size(w * 0.018f, h * 0.15f), CornerRadius(w * 0.005f))
    }
    // Entablature and drum.
    drawRect(p.glow, Offset(cx - w * 0.19f, colTop - h * 0.03f), Size(w * 0.38f, h * 0.03f))
    drawRect(p.glow.copy(alpha = 0.92f), Offset(cx - w * 0.10f, colTop - h * 0.085f), Size(w * 0.20f, h * 0.055f))
    // Dome with ribs.
    val domeR = w * 0.10f
    val domeBase = colTop - h * 0.085f
    drawArc(p.glow, 180f, 180f, true, Offset(cx - domeR, domeBase - domeR), Size(domeR * 2, domeR * 2))
    listOf(-0.55f, 0f, 0.55f).forEach { k ->
        val x = cx + domeR * k
        drawLine(p.dusk.copy(alpha = 0.55f), Offset(x, domeBase),
            Offset(cx + domeR * k * 0.35f, domeBase - domeR * 0.92f), strokeWidth = 1.2f * density)
    }
    drawLine(p.glow, Offset(cx, domeBase - domeR), Offset(cx, domeBase - domeR - h * 0.06f),
        strokeWidth = 1.6f * density, cap = StrokeCap.Round)
    drawCircle(p.glow, radius = 2.4f * density, center = Offset(cx, domeBase - domeR - h * 0.065f))

    // Horizon.
    drawLine(p.glow.copy(alpha = 0.25f), Offset(0f, baseY + h * 0.105f), Offset(w, baseY + h * 0.105f),
        strokeWidth = 1f * density)
}

/**
 * "Commons": a layered city skyline with lit windows over a crowd of abstract figures and a crosswalk.
 */
private fun DrawScope.drawCommons(p: CategoryPalette) {
    val w = size.width
    val h = size.height
    val ground = h * 0.66f

    drawCircle(p.glow.copy(alpha = 0.18f), radius = h * 0.30f, center = Offset(w * 0.80f, h * 0.24f))
    drawCircle(p.glow.copy(alpha = 0.28f), radius = h * 0.30f, center = Offset(w * 0.80f, h * 0.24f),
        style = Stroke(1f * density))

    // Back and front skyline layers: (left, width, height) as fractions.
    val back = listOf(
        Triple(0.30f, 0.08f, 0.36f), Triple(0.39f, 0.06f, 0.28f), Triple(0.46f, 0.09f, 0.46f),
        Triple(0.56f, 0.07f, 0.32f), Triple(0.64f, 0.10f, 0.52f), Triple(0.75f, 0.07f, 0.38f),
        Triple(0.83f, 0.09f, 0.44f), Triple(0.93f, 0.07f, 0.30f)
    )
    back.forEach { (x, bw, bh) ->
        drawRect(p.glow.copy(alpha = 0.12f), Offset(w * x, ground - h * bh), Size(w * bw, h * bh))
    }
    val front = listOf(
        Triple(0.36f, 0.10f, 0.26f), Triple(0.50f, 0.08f, 0.34f), Triple(0.60f, 0.12f, 0.22f),
        Triple(0.74f, 0.09f, 0.30f), Triple(0.86f, 0.12f, 0.20f)
    )
    front.forEachIndexed { i, (x, bw, bh) ->
        val left = w * x
        val top = ground - h * bh
        drawRect(p.deep.copy(alpha = 0.85f), Offset(left, top), Size(w * bw, h * bh))
        drawRect(p.glow.copy(alpha = 0.30f), Offset(left, top), Size(w * bw, h * bh), style = Stroke(1f * density))
        // Window grid; a deterministic few are lit.
        val cols = 3
        val rowsN = ((h * bh) / (h * 0.055f)).toInt().coerceAtLeast(1)
        for (r in 0 until rowsN) for (c in 0 until cols) {
            val lit = (r * 7 + c * 3 + i) % 5 == 0
            drawRect(
                p.glow.copy(alpha = if (lit) 0.85f else 0.16f),
                Offset(left + w * bw * (0.18f + c * 0.25f), top + h * 0.03f + r * h * 0.055f),
                Size(w * bw * 0.14f, h * 0.022f)
            )
        }
    }

    // Ground and crosswalk.
    drawRect(p.deep, Offset(0f, ground), Size(w, h - ground))
    for (i in 0 until 9) {
        val x = w * (0.34f + i * 0.072f)
        drawRect(p.glow.copy(alpha = 0.14f), Offset(x, ground + h * 0.05f), Size(w * 0.04f, h * 0.035f))
    }

    // Crowd: three rows of figures, back rows smaller and dimmer.
    listOf(
        Triple(0.62f, 0.032f, 0.34f), Triple(0.70f, 0.040f, 0.55f), Triple(0.80f, 0.050f, 0.85f)
    ).forEachIndexed { row, (y, scale, alpha) ->
        val count = 8 - row * 2
        for (k in 0 until count) {
            val x = w * (0.36f + (k + (row % 2) * 0.5f) * (0.62f / count))
            val headR = h * scale * 0.42f
            val cy = h * y
            drawCircle(p.glow.copy(alpha = alpha), radius = headR, center = Offset(x, cy))
            drawRoundRect(
                p.glow.copy(alpha = alpha * 0.8f),
                Offset(x - headR * 1.5f, cy + headR * 1.25f),
                Size(headR * 3f, headR * 2.6f),
                CornerRadius(headR * 1.4f)
            )
        }
    }
}

/**
 * "Treasury": a quiet ledger grid, a lattice of points, a row of overlapping square-holed coins (엽전) and a
 * level scalloped band. Every element is symmetric or evenly spaced: nothing rises, falls or reads as a chart.
 */
private fun DrawScope.drawMarket(p: CategoryPalette) {
    val w = size.width
    val h = size.height

    // Ledger grid over the right two thirds.
    for (i in 1..5) {
        val y = h * i / 6f
        drawLine(p.glow.copy(alpha = 0.08f), Offset(w * 0.30f, y), Offset(w, y), strokeWidth = 1f * density)
    }
    for (i in 0..7) {
        val x = w * (0.32f + i * 0.095f)
        drawLine(p.glow.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, h), strokeWidth = 1f * density)
    }

    // Even lattice of points, upper left of the artwork.
    for (r in 0 until 4) for (c in 0 until 6) {
        drawCircle(p.glow.copy(alpha = 0.22f), radius = 1.3f * density,
            center = Offset(w * (0.36f + c * 0.04f), h * (0.12f + r * 0.07f)))
    }

    // Large faint halo behind the coins.
    drawCircle(
        Brush.radialGradient(listOf(p.glow.copy(alpha = 0.22f), Color.Transparent),
            center = Offset(w * 0.72f, h * 0.40f), radius = h * 0.50f),
        radius = h * 0.50f, center = Offset(w * 0.72f, h * 0.40f)
    )

    // Three overlapping coins of equal size on one level line, each with a square hole.
    val coinR = h * 0.17f
    listOf(0.56f, 0.72f, 0.88f).forEachIndexed { i, x ->
        val c = Offset(w * x, h * 0.42f)
        drawCircle(p.dusk, radius = coinR, center = c)
        drawCircle(p.glow.copy(alpha = 0.20f + i * 0.04f), radius = coinR, center = c)
        drawCircle(p.glow.copy(alpha = 0.75f), radius = coinR, center = c, style = Stroke(1.6f * density))
        drawCircle(p.glow.copy(alpha = 0.40f), radius = coinR * 0.74f, center = c, style = Stroke(1f * density))
        val hole = coinR * 0.30f
        drawRect(p.deep, Offset(c.x - hole / 2, c.y - hole / 2), Size(hole, hole))
        drawRect(p.glow.copy(alpha = 0.75f), Offset(c.x - hole / 2, c.y - hole / 2), Size(hole, hole),
            style = Stroke(1.2f * density))
    }

    // Level scalloped band: identical arcs along one baseline.
    val baseY = h * 0.80f
    val arcR = w * 0.035f
    var x = w * 0.30f
    while (x < w) {
        drawArc(p.glow.copy(alpha = 0.30f), 180f, 180f, false, Offset(x, baseY - arcR), Size(arcR * 2, arcR * 2),
            style = Stroke(1.2f * density))
        x += arcR * 2
    }
    drawLine(p.glow.copy(alpha = 0.20f), Offset(w * 0.30f, baseY + h * 0.05f), Offset(w, baseY + h * 0.05f),
        strokeWidth = 1f * density)
}

/** Small category mark for list rows and the cover kicker: an arch, two figures, or a square-holed coin. */
internal fun DrawScope.drawCategoryMark(category: NewsCategory, color: Color) {
    val w = size.width
    val stroke = Stroke(1.6f * density, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (category) {
        NewsCategory.POLITICS -> {
            drawArc(color, 180f, 180f, false, Offset(w * 0.12f, w * 0.18f), Size(w * 0.76f, w * 0.76f), style = stroke)
            drawLine(color, Offset(w * 0.06f, w * 0.86f), Offset(w * 0.94f, w * 0.86f), stroke.width, StrokeCap.Round)
        }
        NewsCategory.SOCIETY -> {
            drawCircle(color, radius = w * 0.17f, center = Offset(w * 0.32f, w * 0.40f))
            drawCircle(color, radius = w * 0.17f, center = Offset(w * 0.70f, w * 0.40f), style = stroke)
            drawLine(color, Offset(w * 0.08f, w * 0.86f), Offset(w * 0.92f, w * 0.86f), stroke.width, StrokeCap.Round)
        }
        NewsCategory.ECONOMY -> {
            // A coin with a square hole: no direction, no trend.
            drawCircle(color, radius = w * 0.40f, center = Offset(w * 0.5f, w * 0.5f), style = stroke)
            val hole = w * 0.24f
            drawRect(color, Offset(w * 0.5f - hole / 2, w * 0.5f - hole / 2), Size(hole, hole), style = stroke)
        }
    }
}
