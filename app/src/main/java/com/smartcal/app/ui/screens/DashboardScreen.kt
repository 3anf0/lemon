package com.smartcal.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcal.app.data.model.ExpenseCategory
import com.smartcal.app.data.model.TransactionType
import com.smartcal.app.ui.components.EventCard
import com.smartcal.app.ui.components.StatCard
import com.smartcal.app.ui.theme.AccentBlue
import com.smartcal.app.ui.theme.AccentGreen
import com.smartcal.app.viewmodel.CalendarViewModel
import com.smartcal.app.viewmodel.FinanceViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    calVm: CalendarViewModel = hiltViewModel(),
    finVm: FinanceViewModel  = hiltViewModel(),
    onVoiceClick: () -> Unit
) {
    val calState  by calVm.state.collectAsState()
    val finState  by finVm.state.collectAsState()
    val today     = LocalDate.now()
    val todayEvents = calState.allEvents.filter { it.startTime.toLocalDate() == today }

    val greeting = when (today.dayOfWeek.value) {
        6, 7 -> "Miłego weekendu! 🌿"
        else -> "Zaplanujmy Twój dzień 📅"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        item {
            Text(
                text  = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",
                    java.util.Locale("pl"))),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
            )
            Text(greeting,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
        }

        // Voice shortcut
        item {
            Card(
                onClick = onVoiceClick,
                shape   = RoundedCornerShape(20.dp),
                colors  = CardDefaults.cardColors(containerColor = AccentBlue)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Zapytaj asystenta Lemon",
                            fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("\"Dodaj siłownię o 18\" lub \"Marek, zaplanuj dzień\"",
                            fontSize = 13.sp, color = Color.White.copy(0.75f))
                    }
                }
            }
        }

        // Stats: events + balance
        item {
            val balance = finState.currentMonthSummary.balance
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    label   = "Wydarzeń dziś",
                    value   = "${todayEvents.size}",
                    emoji   = "📋",
                    color   = AccentBlue,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label   = "Bilans miesiąca",
                    value   = formatMoney(balance),
                    emoji   = if (balance >= 0) "📈" else "📉",
                    color   = if (balance >= 0) AccentGreen else Color(0xFFFF3B30),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Today's events
        item {
            Text("Plan na dziś",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
        }

        if (todayEvents.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface)) {
                    Box(modifier = Modifier.fillMaxWidth().padding(28.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📭", fontSize = 32.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Brak wydarzeń",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                            Text("Naciśnij mikrofon, żeby dodać",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.35f))
                        }
                    }
                }
            }
        } else {
            items(todayEvents) { event ->
                EventCard(event = event, onDelete = { calVm.deleteEvent(event) })
            }
        }

        // Today's transactions
        val todayTx = finState.allTransactions.filter { it.date == today }
        if (todayTx.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Transakcje dziś",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
            items(todayTx) { tx ->
                TransactionRow(tx = tx, onDelete = { finVm.deleteTransaction(tx.id) })
            }
        }
    }
}
