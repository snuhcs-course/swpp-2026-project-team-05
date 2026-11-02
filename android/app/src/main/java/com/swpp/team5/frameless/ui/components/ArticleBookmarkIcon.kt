package com.swpp.team5.frameless.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun ArticleBookmarkIcon(saved: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val inset = 2.dp.toPx()
        val path = Path().apply {
            moveTo(inset, inset)
            lineTo(size.width - inset, inset)
            lineTo(size.width - inset, size.height - inset)
            lineTo(size.width / 2f, size.height * 0.70f)
            lineTo(inset, size.height - inset)
            close()
        }
        if (saved) drawPath(path, tint)
        else drawPath(path, tint, style = Stroke(width = 1.8.dp.toPx()))
    }
}
