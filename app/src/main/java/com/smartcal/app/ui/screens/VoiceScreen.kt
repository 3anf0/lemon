package com.smartcal.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.smartcal.app.ui.theme.AccentBlue
import com.smartcal.app.viewmodel.VoiceMessage
import com.smartcal.app.viewmodel.VoiceViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VoiceScreen(vm: VoiceViewModel = hiltViewModel()) {
    val state         by vm.state.collectAsState()
    val micPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    var textInput       by remember { mutableStateOf("") }
    val listState       = rememberLazyListState()

    LaunchedEffect(state.conversationHistory.size) {
        if (state.conversationHistory.isNotEmpty()) {
            listState.animateScrollToItem(state.conversationHistory.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // Chat history
        LazyColumn(
            state           = listState,
            modifier        = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding  = PaddingValues(vertical = 16.dp)
        ) {
            if (state.conversationHistory.isEmpty()) {
                item {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("🎙️", fontSize = 52.sp)
                            Text(
                                "Powiedz \"Lemon\" aby zacząć",
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign  = TextAlign.Center,
                                color      = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                            )
                            if (state.isAwake) {
                                Card(
                                    shape  = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFF34C759).copy(alpha = 0.15f)
                                    )
                                ) {
                                    Text(
                                        "✅ Lemon jest gotowy!",
                                        modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color      = Color(0xFF34C759),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(state.conversationHistory) { msg ->
                ChatBubble(message = msg)
            }

            if (state.isListening && state.transcript.isNotBlank()) {
                item {
                    ChatBubble(
                        message = VoiceMessage(state.transcript + "...", isUser = true),
                        isLive  = true
                    )
                }
            }

            if (state.isProcessing) {
                item {
                    Row(modifier = Modifier.padding(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp, 12.dp, 12.dp, 0.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                "Lemon myśli...",
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        } // end LazyColumn

        // Expandable help panel — sits above controls
        MarekHelpPanel()

        // Error message
        state.error?.let { error ->
            Text(
                error,
                color    = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Bottom controls
        Column(
            modifier            = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value         = textInput,
                    onValueChange = { textInput = it },
                    placeholder   = { Text("Lub wpisz komendę...") },
                    modifier      = Modifier.weight(1f),
                    singleLine    = true,
                    shape         = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick  = { vm.sendTextCommand(textInput); textInput = "" },
                    enabled  = textInput.isNotBlank() && !state.isProcessing
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Wyślij", tint = AccentBlue)
                }
            }

            if (!state.isAwake && !state.isListening) {
                Text(
                    "Naciśnij mikrofon i powiedz: \"Lemon\"",
                    fontSize  = 12.sp,
                    color     = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                    textAlign = TextAlign.Center
                )
            }

            MicButton(
                isListening = state.isListening,
                isProcessing = state.isProcessing,
                isAwake      = state.isAwake,
                onClick      = {
                    if (!micPermission.status.isGranted) {
                        micPermission.launchPermissionRequest()
                    } else if (state.isListening) {
                        vm.stopListening()
                    } else {
                        vm.startListening()
                    }
                }
            )

            if (!micPermission.status.isGranted) {
                Text(
                    "Potrzebne zezwolenie na mikrofon",
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                )
            }
        }
    }
}

// ── Chat bubble ───────────────────────────────────────────────────────────

@Composable
fun ChatBubble(message: VoiceMessage, isLive: Boolean = false) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            Box(
                modifier         = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AccentBlue),
                contentAlignment = Alignment.Center
            ) {
                Text("L", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart    = 16.dp,
                        topEnd      = 16.dp,
                        bottomStart = if (message.isUser) 16.dp else 4.dp,
                        bottomEnd   = if (message.isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (message.isUser) AccentBlue
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(12.dp)
        ) {
            Text(
                text  = message.text,
                color = if (message.isUser) Color.White
                        else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ── Mic button ────────────────────────────────────────────────────────────

@Composable
fun MicButton(
    isListening  : Boolean,
    isProcessing : Boolean,
    isAwake      : Boolean,
    onClick      : () -> Unit
) {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue  = if (isListening) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30).copy(alpha = 0.2f))
            )
        }
        FloatingActionButton(
            onClick        = onClick,
            containerColor = when {
                isListening  -> Color(0xFFFF3B30)
                isProcessing -> Color(0xFFFF9500)
                isAwake      -> Color(0xFF34C759)
                else         -> AccentBlue
            },
            modifier = Modifier.size(68.dp)
        ) {
            if (isListening) {
                Icon(Icons.Default.MicOff, "Stop", tint = Color.White, modifier = Modifier.size(30.dp))
            } else {
                Icon(Icons.Default.Mic, "Naciśnij i mów", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
    }
}

// ── Wake word toggle ──────────────────────────────────────────────────────

@Composable
fun WakeWordToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.weight(1f)
            ) {
                Text(if (enabled) "🎙️" else "🔇", fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Nasłuchuj w tle",
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (enabled) "Aktywne — powiedz \"cześć lemon\""
                        else         "Wył. — wyższe zużycie baterii gdy włączone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                }
            }
            Switch(
                checked         = enabled,
                onCheckedChange = onToggle
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.onSurface.copy(0.1f)
        )
    }
}

// ── Expandable help panel ─────────────────────────────────────────────────

data class HelpSection(val emoji: String, val title: String, val examples: List<String>)

@Composable
fun MarekHelpPanel() {
    var expanded by remember { mutableStateOf(false) }

    val sections = listOf(
        HelpSection("📅", "Kalendarz", listOf(
            "Dodaj siłownię o 18",
            "Spotkanie jutro o 9",
            "Dodaj dentystę 10 maja o 14",
            "Trening w środę o 7"
        )),
        HelpSection("💰", "Przychód", listOf(
            "Zarobiłem 3000",
            "Wypłata 5000",
            "Premia 1000"
        )),
        HelpSection("🍔", "Jedzenie", listOf(
            "Wydałem 45 na jedzenie",
            "Kupiłem w Biedronce 80"
        )),
        HelpSection("⛽", "Paliwo", listOf(
            "Tankowałem za 200",
            "Orlen 180"
        )),
        HelpSection("🎰", "Hazard", listOf(
            "Przegrałem 200 w kasynie",
            "Straciłem 100 na zakładach"
        )),
        HelpSection("📦", "Inne", listOf(
            "Zapłaciłem 150 za internet",
            "Czynsz 800"
        )),
        HelpSection("📋", "Pytania", listOf(
            "Co mam dziś?",
            "Zaplanuj mój dzień"
        ))
    )

    Surface(
        color          = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column {
            // Tap header to expand/collapse
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("❓", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Jak rozmawiać z Lemonem?",
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    if (expanded) "▲" else "▼",
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically(),
                exit    = shrinkVertically()
            ) {
                Column(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Wake word reminder
                    Card(
                        shape  = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = AccentBlue.copy(alpha = 0.12f)
                        )
                    ) {
                        Row(
                            modifier          = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎙️", fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Zacznij od słowa \"Lemon\"",
                                    fontWeight = FontWeight.Bold,
                                    style      = MaterialTheme.typography.bodySmall,
                                    color      = AccentBlue
                                )
                                Text(
                                    "Lemon odpowie: \"Tu Lemon, co tam?\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                )
                            }
                        }
                    }

                    sections.forEach { section ->
                        HelpSectionRow(section)
                    }
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.onSurface.copy(0.1f)
            )
        }
    }
}

@Composable
fun HelpSectionRow(section: HelpSection) {
    var open by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(section.emoji, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    section.title,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                if (open) "▲" else "▼",
                fontSize = 10.sp,
                color    = MaterialTheme.colorScheme.onSurface.copy(0.4f)
            )
        }

        AnimatedVisibility(
            visible = open,
            enter   = expandVertically(),
            exit    = shrinkVertically()
        ) {
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                section.examples.forEach { example ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(AccentBlue.copy(0.5f))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "\"$example\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentBlue
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.onSurface.copy(0.07f)
        )
    }
}
