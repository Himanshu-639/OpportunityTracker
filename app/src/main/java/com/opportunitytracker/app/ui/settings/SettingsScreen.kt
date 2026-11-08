package com.opportunitytracker.app.ui.settings

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opportunitytracker.app.alarm.AlarmScheduler
import com.opportunitytracker.app.data.calendar.CalendarContractManager
import com.opportunitytracker.app.data.model.Opportunity
import com.opportunitytracker.app.data.repository.OpportunityRepository
import com.opportunitytracker.app.ui.theme.AppThemes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val repository = remember { OpportunityRepository(context) }

    var apiKey by remember {
        mutableStateOf(prefs.getString("groq_api_key", "") ?: "")
    }
    var apiKeyVisible by remember { mutableStateOf(false) }

    var selectedTheme by remember {
        mutableStateOf(prefs.getString("app_theme", AppThemes.DYNAMIC) ?: AppThemes.DYNAMIC)
    }

    var alarmSoundUri by remember {
        mutableStateOf(prefs.getString("alarm_sound_uri", null))
    }
    var alarmSoundTitle by remember {
        mutableStateOf(prefs.getString("alarm_sound_title", "Default System Alarm") ?: "Default System Alarm")
    }

    var isPreviewPlaying by remember { mutableStateOf(false) }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val calendarManager = remember { CalendarContractManager(context) }
    var hasCalendarPerm by remember { mutableStateOf(calendarManager.hasCalendarPermission()) }
    var availableCalendars by remember { mutableStateOf(if (hasCalendarPerm) calendarManager.getGoogleCalendars() else emptyList()) }

    var connectedCalendarId by remember {
        mutableStateOf(prefs.getLong("selected_calendar_id", -1L))
    }
    var connectedAccountName by remember {
        mutableStateOf(prefs.getString("selected_calendar_account", null))
    }

    var isSyncing by remember { mutableStateOf(false) }
    var showAccountChooserDialog by remember { mutableStateOf(false) }

    val calendarPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.all { it }
        hasCalendarPerm = granted
        if (granted) {
            availableCalendars = calendarManager.getGoogleCalendars()
            showAccountChooserDialog = true
        } else {
            Toast.makeText(context, "Calendar permissions are required to sync tasks", Toast.LENGTH_SHORT).show()
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                alarmSoundUri = uri.toString()
                val ringtone = RingtoneManager.getRingtone(context, uri)
                val title = ringtone?.getTitle(context) ?: "Custom Alarm"
                alarmSoundTitle = title
            } else {
                alarmSoundUri = null
                alarmSoundTitle = "Default System Alarm"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                previewPlayer?.stop()
                previewPlayer?.release()
            } catch (_: Exception) {}
        }
    }

    fun saveSettings() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {}

        prefs.edit()
            .putString("groq_api_key", apiKey.trim())
            .putString("alarm_sound_uri", alarmSoundUri)
            .putString("alarm_sound_title", alarmSoundTitle)
            .putString("app_theme", selectedTheme)
            .apply()

        onThemeChanged(selectedTheme)
        Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { saveSettings() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ==========================================
            // 1. APP THEME & PALETTE SECTION
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Theme & Appearance",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val themes = listOf(
                        Triple(AppThemes.DYNAMIC, "Dynamic (Material You)", listOf(Color(0xFF6750A4), Color(0xFF625B71))),
                        Triple(AppThemes.AMOLED, "AMOLED Pitch Black", listOf(Color(0xFF000000), Color(0xFF38BDF8))),
                        Triple(AppThemes.CYBERPUNK, "Cyberpunk Neon", listOf(Color(0xFF0A0714), Color(0xFF00F0FF))),
                        Triple(AppThemes.SLATE, "Minimal Slate", listOf(Color(0xFF0B0F19), Color(0xFF38BDF8))),
                        Triple(AppThemes.FOREST, "Deep Forest", listOf(Color(0xFF06140D), Color(0xFF4ADE80)))
                    )

                    themes.forEach { (themeId, label, colors) ->
                        val isSelected = selectedTheme == themeId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedTheme = themeId
                                    onThemeChanged(themeId)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Visual color indicator dots
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        colors.forEach { dotColor ->
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(dotColor)
                                                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTheme = themeId
                                        onThemeChanged(themeId)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 2. GROQ API KEY SECTION
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Groq API Key",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = if (apiKey.isNotBlank()) Color(0xFF22C55E).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (apiKey.isNotBlank()) "Configured" else "Required",
                                color = if (apiKey.isNotBlank()) Color(0xFF16A34A) else Color(0xFFDC2626),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Step-by-step extraction guide card
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "How to get your free key:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("1. Visit console.groq.com and sign up for free", fontSize = 12.sp, lineHeight = 18.sp)
                            Text("2. Open API Keys in the left dashboard menu", fontSize = 12.sp, lineHeight = 18.sp)
                            Text("3. Click 'Create API Key' and copy it (starts with gsk_)", fontSize = 12.sp, lineHeight = 18.sp)
                            Text("4. Paste your key in the field below", fontSize = 12.sp, lineHeight = 18.sp)

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))
                                    context.startActivity(browserIntent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Groq Console")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Groq API Key (gsk_...)") },
                        placeholder = { Text("Paste gsk_... key here") },
                        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle visibility"
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = clipboard.primaryClip
                                        if (clip != null && clip.itemCount > 0) {
                                            val pasted = clip.getItemAt(0).text?.toString()?.trim().orEmpty()
                                            if (pasted.isNotBlank()) {
                                                apiKey = pasted
                                                Toast.makeText(context, "API Key pasted from clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste key from clipboard"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ==========================================
            // 3. ALARM SOUND SETTING SECTION
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Alarm Sound",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Current Sound",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = alarmSoundTitle,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Play/Stop preview button
                            FilledTonalIconButton(
                                onClick = {
                                    if (isPreviewPlaying) {
                                        try {
                                            previewPlayer?.stop()
                                            previewPlayer?.release()
                                        } catch (_: Exception) {}
                                        previewPlayer = null
                                        isPreviewPlaying = false
                                    } else {
                                        try {
                                            val uriToPlay = if (alarmSoundUri != null) {
                                                Uri.parse(alarmSoundUri)
                                            } else {
                                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                                            }

                                            val player = MediaPlayer.create(context, uriToPlay)
                                            if (player != null) {
                                                previewPlayer = player
                                                player.isLooping = false
                                                player.setOnCompletionListener {
                                                    isPreviewPlaying = false
                                                }
                                                player.start()
                                                isPreviewPlaying = true
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot preview sound: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Preview sound"
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val currentPicked = if (alarmSoundUri != null) Uri.parse(alarmSoundUri) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_RINGTONE)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentPicked)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                }
                                ringtonePickerLauncher.launch(intent)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Choose Sound")
                        }

                        OutlinedButton(
                            onClick = {
                                alarmSoundUri = null
                                alarmSoundTitle = "Default System Alarm"
                                try {
                                    previewPlayer?.stop()
                                    previewPlayer?.release()
                                } catch (_: Exception) {}
                                isPreviewPlaying = false
                                Toast.makeText(context, "Sound reset to default", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Reset")
                        }
                    }
                }
            }

            // ==========================================
            // 4. GOOGLE CALENDAR SYNC
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    val isConnected = hasCalendarPerm && connectedCalendarId != -1L && !connectedAccountName.isNullOrBlank()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Google Calendar Sync",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = if (isConnected) Color(0xFF22C55E).copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isConnected) "Connected" else "Not Connected",
                                color = if (isConnected) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isConnected) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Active Account",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = connectedAccountName.orEmpty(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (connectedCalendarId != -1L) {
                                        isSyncing = true
                                        coroutineScope.launch {
                                            val res = repository.syncAllWithGoogleCalendar(connectedCalendarId)
                                            isSyncing = false
                                            Toast.makeText(
                                                context,
                                                "Synced ${res.pushedCount} tasks & imported ${res.pulledCount} calendar events.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                enabled = !isSyncing,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                            ) {
                                Text("Sync Now", fontSize = 12.sp, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    availableCalendars = calendarManager.getGoogleCalendars()
                                    showAccountChooserDialog = true
                                },
                                enabled = !isSyncing,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                            ) {
                                Text("Switch", fontSize = 12.sp, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    prefs.edit()
                                        .remove("selected_calendar_id")
                                        .remove("selected_calendar_account")
                                        .apply()
                                    connectedCalendarId = -1L
                                    connectedAccountName = null
                                    Toast.makeText(context, "Disconnected from calendar", Toast.LENGTH_SHORT).show()
                                },
                                enabled = !isSyncing,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                            ) {
                                Text("Disconnect", fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                if (calendarManager.hasCalendarPermission()) {
                                    hasCalendarPerm = true
                                    availableCalendars = calendarManager.getGoogleCalendars()
                                    showAccountChooserDialog = true
                                } else {
                                    calendarPermLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.READ_CALENDAR,
                                            android.Manifest.permission.WRITE_CALENDAR
                                        )
                                    )
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connect Account")
                        }
                    }
                }
            }

            // Dialog for choosing Google Account / Calendar
            if (showAccountChooserDialog) {
                AlertDialog(
                    onDismissRequest = { showAccountChooserDialog = false },
                    title = {
                        Text("Select Google Account", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (availableCalendars.isEmpty()) {
                                Text(
                                    text = "No calendar accounts found on this device.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        val newId = calendarManager.createLocalCalendarIfNone()
                                        if (newId != null) {
                                            availableCalendars = calendarManager.getGoogleCalendars()
                                            prefs.edit()
                                                .putLong("selected_calendar_id", newId)
                                                .putString("selected_calendar_account", "Local Device Calendar")
                                                .apply()
                                            connectedCalendarId = newId
                                            connectedAccountName = "Local Device Calendar"
                                            showAccountChooserDialog = false
                                            isSyncing = true
                                            coroutineScope.launch {
                                                val res = repository.syncAllWithGoogleCalendar(newId)
                                                isSyncing = false
                                                Toast.makeText(context, "Connected to Local Calendar! Synced ${res.pushedCount} tasks & imported ${res.pulledCount} events.", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            try {
                                                val intent = Intent(android.provider.Settings.ACTION_ADD_ACCOUNT).apply {
                                                    putExtra(android.provider.Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Create Local Calendar")
                                }
                            } else {
                                Text(
                                    text = "Choose an account to sync tasks to and pull events from:",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                availableCalendars.forEach { cal ->
                                    val isSelected = cal.id == connectedCalendarId
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(
                                            if (isSelected) 2.dp else 1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val accName = cal.accountName.ifBlank { cal.displayName }
                                                prefs.edit()
                                                    .putLong("selected_calendar_id", cal.id)
                                                    .putString("selected_calendar_account", accName)
                                                    .apply()
                                                connectedCalendarId = cal.id
                                                connectedAccountName = accName
                                                showAccountChooserDialog = false

                                                // Automatically push tasks to this calendar & pull events from this calendar
                                                isSyncing = true
                                                coroutineScope.launch {
                                                    val res = repository.syncAllWithGoogleCalendar(cal.id)
                                                    isSyncing = false
                                                    Toast.makeText(
                                                        context,
                                                        "Connected to ${accName}! Synced ${res.pushedCount} tasks & imported ${res.pulledCount} calendar events.",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cal.accountName.ifBlank { cal.displayName },
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                if (cal.accountName.isNotBlank() && cal.displayName != cal.accountName) {
                                                    Text(
                                                        text = cal.displayName,
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = null
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showAccountChooserDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
