package com.swpp.team5.frameless.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.ui.theme.Fl

/** One product signature across Home, authentication, search, and the tutorial. */
@Composable
internal fun BrandRow(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(if (compact) 26.dp else 30.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF7194E4),
                    Color(0xFF9B79CC), Color(0xFFD77DA7))), RoundedCornerShape(9.dp))
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center
        ) {
            Text("F", color = Color.White, fontFamily = Fl.Serif,
                fontSize = if (compact) 15.sp else 18.sp, fontWeight = FontWeight.Bold)
        }
        Text("FrameLESS", color = Fl.Ink, fontFamily = Fl.Serif,
            fontSize = if (compact) 19.sp else 22.sp,
            fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    }
}
