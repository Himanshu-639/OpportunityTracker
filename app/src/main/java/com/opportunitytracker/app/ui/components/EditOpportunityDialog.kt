package com.opportunitytracker.app.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.opportunitytracker.app.data.model.AlarmTiming
import com.opportunitytracker.app.data.model.Opportunity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EditOpportunityDialog(
    opportunity: Opportunity,
    onDismiss: () -> Unit,
    onSave: (Opportunity) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(opportunity.title) }
    var organization by remember { mutableStateOf(opportunity.organization) }
    var deadlineMillis by remember { mutableLongStateOf(opportunity.deadlineMillis) }
    var formUrl by remember { mutableStateOf(opportunity.formUrl) }
    var category by remember { mutableStateOf(opportunity.category) }
    var summary by remember { mutableStateOf(opportunity.summary) }
    var notes by remember { mutableStateOf(opportunity.notes) }

    var alarms by remember {
        mutableStateOf(opportunity.getAlarms().toMutableList())
    }

    val formattedDate = remember(deadlineMillis) {
        SimpleDateFormat("EEE, dd MMM yyyy 'at' hh:mm a", Locale.getDefault()).format(Date(deadlineMillis))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (opportunity.id == 0L) "New Opportunity" else "Edit Opportunity",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Opportunity Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Company & Category Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = organization,
                        onValueChange = { organization = it },
                        label = { Text("Organization") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )

                    var catExpanded by remember { mutableStateOf(false) }
                    val categories = listOf("Internship", "Job", "Hackathon", "Fellowship", "Grant", "Event", "Form")

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = {
                                IconButton(onClick = { catExpanded = true }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = catExpanded,
                            onDismissRequest = { catExpanded = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        category = cat
                                        catExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Deadline Picker Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Deadline Date & Time",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formattedDate,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    val cal = Calendar.getInstance().apply { timeInMillis = deadlineMillis }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            cal.set(Calendar.YEAR, y)
                                            cal.set(Calendar.MONTH, m)
                                            cal.set(Calendar.DAY_OF_MONTH, d)
                                            deadlineMillis = cal.timeInMillis
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date", tint = MaterialTheme.colorScheme.primary)
                            }

                            IconButton(
                                onClick = {
                                    val cal = Calendar.getInstance().apply { timeInMillis = deadlineMillis }
                                    TimePickerDialog(
                                        context,
                                        { _, h, min ->
                                            cal.set(Calendar.HOUR_OF_DAY, h)
                                            cal.set(Calendar.MINUTE, min)
                                            cal.set(Calendar.SECOND, 0)
                                            deadlineMillis = cal.timeInMillis
                                        },
                                        cal.get(Calendar.HOUR_OF_DAY),
                                        cal.get(Calendar.MINUTE),
                                        false
                                    ).show()
                                }
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = "Pick Time", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Application Link
                OutlinedTextField(
                    value = formUrl,
                    onValueChange = { formUrl = it },
                    label = { Text("Application / Form URL") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Alarm Reminders Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Alarm,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Alarm Reminders (${alarms.size}/5)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        alarms.forEachIndexed { index, alarmTiming ->
                            AlarmTimingEditorRow(
                                index = index,
                                timing = alarmTiming,
                                onUpdate = { updatedAlarm ->
                                    alarms = alarms.toMutableList().apply {
                                        this[index] = updatedAlarm
                                    }
                                },
                                onDelete = {
                                    alarms = alarms.toMutableList().apply {
                                        removeAt(index)
                                    }
                                }
                            )
                            if (index < alarms.size - 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        if (alarms.size < 5) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val newAlarm = when (alarms.size) {
                                        0 -> AlarmTiming(amount = 2, unit = "DAYS")
                                        1 -> AlarmTiming(amount = 2, unit = "HOURS")
                                        2 -> AlarmTiming(amount = 1, unit = "DAYS")
                                        3 -> AlarmTiming(amount = 1, unit = "HOURS")
                                        else -> AlarmTiming(amount = 30, unit = "MINUTES")
                                    }
                                    alarms = (alarms + newAlarm).toMutableList()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(38.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Alarm (${alarms.size}/5)", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Summary (renamed cleanly from Summary / Highlights)
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("Summary") },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Personal Notes") },
                    maxLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val alarmsToSave = if (alarms.isEmpty()) Opportunity.defaultAlarms() else alarms.take(5)
                            val updated = opportunity.copy(
                                title = title.ifBlank { "Untitled Opportunity" },
                                organization = organization,
                                deadlineMillis = deadlineMillis,
                                formUrl = formUrl,
                                category = category.ifBlank { "General" },
                                summary = summary,
                                notes = notes,
                                tags = "",
                                alarmsJson = Json.encodeToString(alarmsToSave)
                            )
                            onSave(updated)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

@Composable
fun AlarmTimingEditorRow(
    index: Int,
    timing: AlarmTiming,
    onUpdate: (AlarmTiming) -> Unit,
    onDelete: () -> Unit
) {
    var amountText by remember(timing.amount) { mutableStateOf(timing.amount.toString()) }
    var unitExpanded by remember { mutableStateOf(false) }

    val units = listOf("DAYS", "HOURS", "MINUTES")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = amountText,
            onValueChange = { newVal ->
                if (newVal.all { it.isDigit() } && newVal.length <= 3) {
                    amountText = newVal
                    val parsed = newVal.toIntOrNull() ?: 1
                    onUpdate(timing.copy(amount = parsed.coerceAtLeast(1)))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.width(68.dp),
            shape = RoundedCornerShape(8.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = when (timing.unit) {
                    "DAYS" -> if (timing.amount == 1) "day before" else "days before"
                    "HOURS" -> if (timing.amount == 1) "hour before" else "hours before"
                    "MINUTES" -> if (timing.amount == 1) "minute before" else "minutes before"
                    else -> "${timing.unit.lowercase()} before"
                },
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { unitExpanded = true }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            DropdownMenu(
                expanded = unitExpanded,
                onDismissRequest = { unitExpanded = false }
            ) {
                units.forEach { u ->
                    DropdownMenuItem(
                        text = {
                            Text(when (u) {
                                "DAYS" -> "Days before"
                                "HOURS" -> "Hours before"
                                "MINUTES" -> "Minutes before"
                                else -> u
                            })
                        },
                        onClick = {
                            unitExpanded = false
                            onUpdate(timing.copy(unit = u))
                        }
                    )
                }
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = "Remove alarm",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
