package com.opportunitytracker.app.ui.main

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opportunitytracker.app.data.calendar.CalendarContractManager
import com.opportunitytracker.app.data.model.Opportunity
import com.opportunitytracker.app.data.ocr.ImageOcrHelper
import com.opportunitytracker.app.ui.components.EditOpportunityDialog
import com.opportunitytracker.app.ui.components.OpportunityCard
import com.opportunitytracker.app.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

fun Modifier.drawVerticalScrollbar(
    state: LazyListState,
    color: Color = Color(0xFF94A3B8).copy(alpha = 0.5f),
    width: Dp = 4.dp
): Modifier = this.drawWithContent {
    drawContent()
    val totalItems = state.layoutInfo.totalItemsCount
    val visibleItemsInfo = state.layoutInfo.visibleItemsInfo
    if (totalItems <= 1 || visibleItemsInfo.isEmpty()) return@drawWithContent

    val firstVisible = visibleItemsInfo.first().index
    val visibleCount = visibleItemsInfo.size
    val viewportHeight = size.height
    val scrollableItems = (totalItems - visibleCount).coerceAtLeast(1)
    val progress = (firstVisible.toFloat() / scrollableItems).coerceIn(0f, 1f)
    val thumbHeight = (viewportHeight * visibleCount / totalItems.toFloat()).coerceIn(40.dp.toPx(), viewportHeight * 0.6f)
    val thumbOffset = (viewportHeight - thumbHeight) * progress

    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - width.toPx(), thumbOffset),
        size = Size(width.toPx(), thumbHeight),
        cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onThemeChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val opportunities by viewModel.opportunities.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val groupMode by viewModel.groupMode.collectAsState()

    var showSettingsScreen by remember { mutableStateOf(false) }
    var opportunityToEdit by remember { mutableStateOf<Opportunity?>(null) }
    var pasteText by remember { mutableStateOf("") }
    var isOcrScanning by remember { mutableStateOf(false) }

    // Auto-clipboard detection state
    var detectedClipboardText by remember { mutableStateOf<String?>(null) }
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    val listState = rememberLazyListState()
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // Photo picker launcher for "Scan Poster / Screenshot" (inside Extract card)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isOcrScanning = true
            Toast.makeText(context, "Scanning poster text with ML Kit...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                val ocrResult = ImageOcrHelper.extractTextFromImageUri(context, uri)
                isOcrScanning = false
                if (ocrResult.isSuccess) {
                    val extractedText = ocrResult.getOrThrow()
                    pasteText = extractedText
                    Toast.makeText(context, "Text extracted from poster! Starting analysis...", Toast.LENGTH_SHORT).show()
                    viewModel.pasteAndExtract(extractedText)
                } else {
                    val err = ocrResult.exceptionOrNull()?.localizedMessage ?: "Could not recognize text"
                    Toast.makeText(context, "Poster OCR failed: $err", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Auto-Clipboard Detection on App Launch
    LaunchedEffect(Unit) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val clipText = clip.getItemAt(0).text?.toString()?.trim().orEmpty()
                val lastSeen = prefs.getString("last_clipboard_seen", "")
                if (clipText.isNotBlank() && clipText != lastSeen && clipText.length >= 8) {
                    val hasUrl = clipText.contains("http://", ignoreCase = true) || clipText.contains("https://", ignoreCase = true)
                    val hasKeywords = listOf("deadline", "hackathon", "internship", "fellowship", "drive", "apply", "register", "event", "grant", "contest").any {
                        clipText.contains(it, ignoreCase = true)
                    }
                    if (hasUrl || hasKeywords) {
                        detectedClipboardText = clipText
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // Auto-sync with connected Google Calendar on app open
    LaunchedEffect(Unit) {
        val savedCalId = prefs.getLong("selected_calendar_id", -1L)
        val calendarManager = CalendarContractManager(context)
        if (savedCalId != -1L && calendarManager.hasCalendarPermission()) {
            try {
                viewModel.syncGoogleCalendar(savedCalId)
            } catch (_: Exception) {}
        }
    }

    // If Settings is open, display full screen Settings page look
    if (showSettingsScreen) {
        BackHandler {
            showSettingsScreen = false
        }
        SettingsScreen(
            onBack = { showSettingsScreen = false },
            onThemeChanged = onThemeChanged
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Opportunity Tracker",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    // Settings page button
                    IconButton(onClick = { showSettingsScreen = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Auto-Clipboard Detection non-intrusive bottom banner
            detectedClipboardText?.let { clipText ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Opportunity in clipboard",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = clipText.take(45) + if (clipText.length > 45) "..." else "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    prefs.edit().putString("last_clipboard_seen", clipText).apply()
                                    viewModel.pasteAndExtract(clipText)
                                    detectedClipboardText = null
                                    Toast.makeText(context, "Extracting from clipboard...", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Extract", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    prefs.edit().putString("last_clipboard_seen", clipText).apply()
                                    detectedClipboardText = null
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .drawVerticalScrollbar(listState),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Quick Paste, Poster Scan & Extract Card
            item(key = "quick_paste_box") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Extract Opportunity Details",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = pasteText,
                                onValueChange = { pasteText = it },
                                placeholder = { Text("Paste link, post text, or flyer...", fontSize = 13.sp) },
                                singleLine = true,
                                leadingIcon = {
                                    if (isOcrScanning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                                            Icon(
                                                imageVector = Icons.Default.PhotoCamera,
                                                contentDescription = "Scan poster or screenshot",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                            if (clip.isNotBlank()) pasteText = clip
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard", modifier = Modifier.size(20.dp))
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    val trimmed = pasteText.trim()
                                    if (trimmed.isNotEmpty()) {
                                        viewModel.pasteAndExtract(trimmed)
                                        pasteText = ""
                                    }
                                },
                                enabled = pasteText.trim().isNotEmpty() || isOcrScanning,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(52.dp)
                            ) {
                                if (isOcrScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Extract", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Minimal status filter chips: All, Due Soon (<48h), Upcoming, Done
            stickyHeader(key = "filter_chips_header") {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // All Status
                        FilterChip(
                            selected = filter == OpportunityFilter.ALL,
                            onClick = { viewModel.setFilter(OpportunityFilter.ALL) },
                            label = { Text("All") }
                        )

                        // Due Soon
                        FilterChip(
                            selected = filter == OpportunityFilter.DUE_SOON,
                            onClick = { viewModel.setFilter(OpportunityFilter.DUE_SOON) },
                            label = { Text("Due Soon (<48h)") }
                        )

                        // Upcoming
                        FilterChip(
                            selected = filter == OpportunityFilter.UPCOMING,
                            onClick = { viewModel.setFilter(OpportunityFilter.UPCOMING) },
                            label = { Text("Upcoming") }
                        )

                        // Done
                        FilterChip(
                            selected = filter == OpportunityFilter.COMPLETED,
                            onClick = { viewModel.setFilter(OpportunityFilter.COMPLETED) },
                            label = { Text("Done") }
                        )

                        // Sort & Group indicator badge
                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { sortMenuExpanded = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${sortMode.label}${if (groupMode == GroupMode.CATEGORY) " • Grouped" else ""}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                Text(
                                    text = "Sort By",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                                SortMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(mode.label)
                                                if (sortMode == mode) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.setSortMode(mode)
                                            sortMenuExpanded = false
                                        }
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                Text(
                                    text = "Grouping",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                                GroupMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(mode.label)
                                                if (groupMode == mode) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.setGroupMode(mode)
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Opportunity List or Grouped Categories
            if (opportunities.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No opportunities found",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Share any flyer, screenshot, post, or link to Opportunity Tracker via the Android Share sheet, or scan a poster above!",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else if (groupMode == GroupMode.CATEGORY) {
                // Group by category
                val grouped = opportunities.groupBy { it.category.ifBlank { "Other" } }
                grouped.forEach { (categoryName, catOpportunities) ->
                    item(key = "cat_header_$categoryName") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = categoryName.uppercase(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "${catOpportunities.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    items(catOpportunities, key = { it.id }) { opp ->
                        OpportunityCard(
                            opportunity = opp,
                            onEdit = { opportunityToEdit = opp },
                            onDelete = { viewModel.deleteOpportunity(opp) },
                            onToggleComplete = { viewModel.toggleComplete(opp) },
                            onRetry = { viewModel.retryExtraction(opp) }
                        )
                    }
                }
            } else {
                // Standard flat list
                items(opportunities, key = { it.id }) { opp ->
                    OpportunityCard(
                        opportunity = opp,
                        onEdit = { opportunityToEdit = opp },
                        onDelete = { viewModel.deleteOpportunity(opp) },
                        onToggleComplete = { viewModel.toggleComplete(opp) },
                        onRetry = { viewModel.retryExtraction(opp) }
                    )
                }
            }
        }
    }

    // Edit Opportunity Dialog
    opportunityToEdit?.let { opp ->
        EditOpportunityDialog(
            opportunity = opp,
            onDismiss = { opportunityToEdit = null },
            onSave = { updated ->
                viewModel.updateOpportunity(updated)
                opportunityToEdit = null
            }
        )
    }
}
