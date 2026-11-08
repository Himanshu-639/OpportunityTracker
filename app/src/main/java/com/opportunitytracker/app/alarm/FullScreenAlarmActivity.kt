package com.opportunitytracker.app.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opportunitytracker.app.data.local.AppDatabase
import com.opportunitytracker.app.data.model.Opportunity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FullScreenAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnScreenOnAndShowOverLockScreen()

        val oppId = intent.getLongExtra(AlarmScheduler.EXTRA_OPPORTUNITY_ID, -1L)

        setContent {
            var opportunity by remember { mutableStateOf<Opportunity?>(null) }
            var showSnoozePicker by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(oppId) {
                if (oppId != -1L) {
                    val db = AppDatabase.getDatabase(this@FullScreenAlarmActivity)
                    opportunity = db.opportunityDao().getOpportunityById(oppId)
                }
            }

            AlarmScreenContent(
                opportunity = opportunity,
                onOpenForm = { formUrl ->
                    stopAlarmSound()
                    if (formUrl.isNotBlank()) {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(formUrl))
                        startActivity(browserIntent)
                    }
                    finish()
                },
                onSnoozeClick = {
                    showSnoozePicker = true
                },
                onDismiss = {
                    stopAlarmSound()
                    finish()
                },
                onMarkDone = {
                    stopAlarmSound()
                    scope.launch {
                        if (oppId != -1L) {
                            val db = AppDatabase.getDatabase(this@FullScreenAlarmActivity)
                            db.opportunityDao().updateCompletionStatus(oppId, true)
                        }
                        finish()
                    }
                }
            )

            if (showSnoozePicker) {
                SnoozeDurationDialog(
                    onDismiss = { showSnoozePicker = false },
                    onSelectSnooze = { triggerMillis, _ ->
                        stopAlarmSound()
                        if (oppId != -1L) {
                            AlarmScheduler(this@FullScreenAlarmActivity).snoozeAlarmAt(oppId, triggerMillis)
                        }
                        showSnoozePicker = false
                        finish()
                    }
                )
            }
        }
    }

    private fun turnScreenOnAndShowOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun stopAlarmSound() {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
    }

    override fun onDestroy() {
        stopAlarmSound()
        super.onDestroy()
    }
}

@Composable
fun AlarmScreenContent(
    opportunity: Opportunity?,
    onOpenForm: (String) -> Unit,
    onSnoozeClick: () -> Unit,
    onDismiss: () -> Unit,
    onMarkDone: () -> Unit
) {
    val dateStr = remember(opportunity?.deadlineMillis) {
        opportunity?.deadlineMillis?.let {
            SimpleDateFormat("EEEE, dd MMM yyyy 'at' hh:mm a", Locale.getDefault()).format(Date(it))
        } ?: "Deadline approaching!"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = "Alarm",
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "OPPORTUNITY DEADLINE",
                color = Color(0xFFEF4444),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = opportunity?.title ?: "Time to fill form!",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            if (!opportunity?.organization.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = opportunity?.organization.orEmpty(),
                    color = Color(0xFF94A3B8),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Deadline Time",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dateStr,
                        color = Color(0xFF38BDF8),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    if (!opportunity?.summary.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = opportunity?.summary.orEmpty(),
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Primary Action: Open Form
            if (!opportunity?.formUrl.isNullOrBlank()) {
                Button(
                    onClick = { onOpenForm(opportunity?.formUrl.orEmpty()) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Application Form Now",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Snooze & Mark Done Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSnoozeClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Snooze, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Snooze...")
                }

                Button(
                    onClick = onMarkDone,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Done")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Dismiss Alarm", color = Color(0xFF94A3B8), fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun SnoozeDurationDialog(
    onDismiss: () -> Unit,
    onSelectSnooze: (Long, String) -> Unit
) {
    val now = System.currentTimeMillis()

    val tonightCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 20)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= now) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    val tomorrowMorningCal = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val snoozeOptions = listOf(
        Pair("15 minutes", now + 15 * 60 * 1000L),
        Pair("1 hour", now + 60 * 60 * 1000L),
        Pair("Tonight at 8:00 PM", tonightCal.timeInMillis),
        Pair("Tomorrow at 9:00 AM", tomorrowMorningCal.timeInMillis)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Snooze Alarm", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Choose when you want to be reminded next:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                snoozeOptions.forEach { (label, millis) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectSnooze(millis, label)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                            Icon(
                                Icons.Default.Alarm,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
