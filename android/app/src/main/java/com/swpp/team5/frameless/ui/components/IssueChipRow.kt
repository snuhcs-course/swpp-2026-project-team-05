package com.swpp.team5.frameless.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swpp.team5.frameless.ui.state.IssueView
import com.swpp.team5.frameless.ui.state.OTHERS_ISSUE
import com.swpp.team5.frameless.ui.state.two
import com.swpp.team5.frameless.ui.theme.Caption
import com.swpp.team5.frameless.ui.theme.Fl
import com.swpp.team5.frameless.ui.theme.PillShape

/** 쟁점 선택 칩 줄: 01 02 03 … 그 밖 */
@Composable
internal fun IssueChipRow(
    issues: List<IssueView>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Caption("쟁점", modifier = Modifier.padding(end = 4.dp))
        issues.forEach { issue ->
            IssueChip(label = two(issue.index + 1), selected = issue.index == selected) { onSelect(issue.index) }
        }
        IssueChip(label = "그 밖의 문장", selected = selected == OTHERS_ISSUE) { onSelect(OTHERS_ISSUE) }
    }
}

@Composable
private fun IssueChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) Fl.OnAccent else Fl.Ink,
        modifier = Modifier
            .background(if (selected) Fl.Accent else Color.Transparent, PillShape)
            .border(1.dp, if (selected) Fl.Accent else Fl.Line, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}
