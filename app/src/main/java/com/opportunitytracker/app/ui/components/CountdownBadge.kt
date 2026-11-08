package com.opportunitytracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.TimeUnit

@Composable
fun CountdownBadge(
    deadlineMillis: Long,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val diffMillis = deadlineMillis - now

    val (badgeText, badgeBgColor, badgeTextColor, icon) = when {
        isCompleted -> Quad(
            "Completed",
            Color(0xFFDCFCE7),
            Color(0xFF166534),
            Icons.Default.CheckCircle
        )
        diffMillis <= 0 -> Quad(
            "Expired",
            Color(0xFFF1F5F9),
            Color(0xFF64748B),
            Icons.Default.AccessTime
        )
        diffMillis < TimeUnit.HOURS.toMillis(6) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis) % 60
            val text = if (hours > 0) "Due in ${hours}h ${minutes}m" else "Due in ${minutes}m"
            Quad(text, Color(0xFFFEE2E2), Color(0xFFDC2626), Icons.Default.Warning)
        }
        diffMillis < TimeUnit.HOURS.toMillis(24) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
            Quad("Due today (${hours}h left)", Color(0xFFFFEDD5), Color(0xFFEA580C), Icons.Default.AccessTime)
        }
        diffMillis < TimeUnit.HOURS.toMillis(48) -> {
            Quad("Due tomorrow", Color(0xFFFEF3C7), Color(0xFFD97706), Icons.Default.AccessTime)
        }
        else -> {
            val days = TimeUnit.MILLISECONDS.toDays(diffMillis)
            Quad("In $days days", Color(0xFFE0F2FE), Color(0xFF0369A1), Icons.Default.AccessTime)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(badgeBgColor, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = badgeTextColor,
            modifier = Modifier.width(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = badgeText,
            color = badgeTextColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
