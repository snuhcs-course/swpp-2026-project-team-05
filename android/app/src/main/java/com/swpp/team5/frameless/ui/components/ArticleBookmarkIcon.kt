package com.swpp.team5.frameless.ui.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.swpp.team5.frameless.R

@Composable
internal fun ArticleBookmarkIcon(saved: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(if (saved) R.drawable.ic_portal_bookmark_filled else R.drawable.ic_portal_bookmark),
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}
