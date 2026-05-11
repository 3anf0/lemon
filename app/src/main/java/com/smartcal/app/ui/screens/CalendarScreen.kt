package com.smartcal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcal.app.data.model.EventCategory
import com.smartcal.app.ui.components.EventCard
import com.smartcal.app.ui.theme.AccentBlue
import com.smartcal.app.viewmodel.CalendarViewModel
import com.smartcal.app.viewmodel.FinanceViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val GreenColor = Color(0xFF34C759)
private val RedColor   = Color(0xFFFF3B30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    vm    : CalendarViewModel = hiltViewModel(),
    finVm : FinanceViewModel  = hiltViewModel()
) {
    val state    by vm.state.collectAsState()
    val finState by finVm.state.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var currentMonth  by remember { mutableStateOf(YearMonth.now()) }

    val balanceByDate: Map<LocalDate, Double> = remember(finState.allTransactions) {
        finState.allTransactions
            .groupBy { it.date }
            .mapValues { (_, txs) -> txs.sumOf { it.signedAmount } }
    }

    val selectedDayTx      = finState.allTransactions.filter { it.date == state.selectedDate }
    val selectedDayBalance = selectedDayTx.sumOf { it.signedAmount }

    // Box lets FAB sit on top at bottom-right, never scrolling with content
    Box(modifier = Modifier.fillMaxSize()) {

        LazyColumn(
            modifier        = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // Bottom padding reserves space so last item never hides behind FAB
            contentPadding  = PaddingValues(top = 16.dp, bottom = 88.dp)
        ) {

            // Month navigation header
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Poprzedni miesiąc")
                    }
                    Text(
                        text       = currentMonth
                            .format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("pl")))
                            .replaceFirstChar { it.uppercase() },
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Następny miesiąc")
                    }
                }
            }

            item { SimpleWeekHeader() }

            item {
                SimpleCalendarGrid(
                    yearMonth    = currentMonth,
                    selectedDate = state.selectedDate,
                    eventDates   = state.allEvents.map { it.startTime.toLocalDate() }.toSet(),
                    balanceByDate = balanceByDate,
                    onDateSelected = { date ->
                        vm.selectDate(date)
                        currentMonth = YearMonth.from(date)
                    }
                )
            }

            // Selected date header + balance badge
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text       = state.selectedDate
                            .format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("pl")))
                            .replaceFirstChar { it.uppercase() },
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.weight(1f)
                    )
                    if (selectedDayTx.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (selectedDayBalance >= 0) GreenColor.copy(0.15f)
                                    else RedColor.copy(0.15f)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text       = "${if (selectedDayBalance >= 0) "+" else ""}${"%.0f".format(selectedDayBalance)} zł",
                                fontWeight = FontWeight.Bold,
                                color      = if (selectedDayBalance >= 0) GreenColor else RedColor,
                                fontSize   = 14.sp
                            )
                        }
                    }
                }
            }

            // Empty state
            if (state.eventsForDay.isEmpty() && selectedDayTx.isEmpty()) {
                item {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Brak wydarzeń i transakcji",
                            color     = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Calendar events
            if (state.eventsForDay.isNotEmpty()) {
                item {
                    Text(
                        "📅 Wydarzenia",
                        style    = MaterialTheme.typography.labelLarge,
                        color    = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(state.eventsForDay) { event ->
                    EventCard(event = event, onDelete = { vm.deleteEvent(event) })
                }
            }

            // Finance for selected day
            if (selectedDayTx.isNotEmpty()) {
                item {
                    Text(
                        "💰 Finanse",
                        style    = MaterialTheme.typography.labelLarge,
                        color    = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(selectedDayTx) { tx ->
                    TransactionRow(tx = tx, onDelete = { finVm.deleteTransaction(tx.id) })
                }
                item {
                    Card(
                        shape  = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor =
                                if (selectedDayBalance >= 0) GreenColor.copy(0.1f)
                                else RedColor.copy(0.1f)
                        )
                    ) {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Bilans dnia", fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${if (selectedDayBalance >= 0) "+" else ""}${"%.2f".format(selectedDayBalance)} zł",
                                fontWeight = FontWeight.Bold,
                                color      = if (selectedDayBalance >= 0) GreenColor else RedColor
                            )
                        }
                    }
                }
            }

        } // end LazyColumn

        // FAB pinned to bottom-right — never conflicts with delete buttons
        FloatingActionButton(
            onClick        = { showAddDialog = true },
            containerColor = AccentBlue,
            modifier       = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.Add,
                contentDescription = "Dodaj wydarzenie",
                tint               = Color.White
            )
        }

    } // end Box

    if (showAddDialog) {
        AddEventDialog(
            onDismiss = { showAddDialog = false },
            onAdd     = { title, hour, minute, category ->
                vm.addEvent(title, hour, minute, category = category)
                showAddDialog = false
            }
        )
    }
}

// ── Week header ───────────────────────────────────────────────────────────

@Composable
fun SimpleWeekHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("Pn","Wt","Śr","Cz","Pt","Sb","Nd").forEach { day ->
            Text(
                text       = day,
                modifier   = Modifier.weight(1f),
                textAlign  = TextAlign.Center,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface.copy(0.5f)
            )
        }
    }
}

// ── Calendar grid ─────────────────────────────────────────────────────────

@Composable
fun SimpleCalendarGrid(
    yearMonth     : YearMonth,
    selectedDate  : LocalDate,
    eventDates    : Set<LocalDate>,
    balanceByDate : Map<LocalDate, Double>,
    onDateSelected: (LocalDate) -> Unit
) {
    val today          = LocalDate.now()
    val firstDay       = yearMonth.atDay(1)
    val firstDayOfWeek = firstDay.dayOfWeek.value
    val daysInMonth    = yearMonth.lengthOfMonth()

    val cells = mutableListOf<LocalDate?>()
    repeat(firstDayOfWeek - 1) { cells.add(null) }
    for (d in 1..daysInMonth) cells.add(yearMonth.atDay(d))
    while (cells.size % 7 != 0) cells.add(null)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val isSelected = date == selectedDate
                            val isToday    = date == today
                            val hasEvent   = date in eventDates
                            val balance    = balanceByDate[date]

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> AccentBlue
                                            isToday    -> AccentBlue.copy(alpha = 0.15f)
                                            else       -> Color.Transparent
                                        }
                                    )
                                    .clickable { onDateSelected(date) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text       = date.dayOfMonth.toString(),
                                        fontSize   = 13.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color      = when {
                                            isSelected -> Color.White
                                            isToday    -> AccentBlue
                                            else       -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment     = Alignment.CenterVertically
                                    ) {
                                        if (hasEvent) {
                                            Box(
                                                modifier = Modifier.size(4.dp).clip(CircleShape)
                                                    .background(if (isSelected) Color.White else AccentBlue)
                                            )
                                        }
                                        if (balance != null) {
                                            Box(
                                                modifier = Modifier.size(4.dp).clip(CircleShape)
                                                    .background(
                                                        when {
                                                            isSelected   -> Color.White
                                                            balance >= 0 -> GreenColor
                                                            else         -> RedColor
                                                        }
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Add event dialog ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    onDismiss : () -> Unit,
    onAdd     : (String, Int, Int, EventCategory) -> Unit
) {
    var title            by remember { mutableStateOf("") }
    var hour             by remember { mutableStateOf("09") }
    var minute           by remember { mutableStateOf("00") }
    var selectedCategory by remember { mutableStateOf(EventCategory.PERSONAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj wydarzenie", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                OutlinedTextField(
                    value         = title,
                    onValueChange = { title = it },
                    label         = { Text("Nazwa wydarzenia") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = hour,
                        onValueChange = { if (it.length <= 2) hour = it },
                        label         = { Text("Godzina") },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true
                    )
                    OutlinedTextField(
                        value         = minute,
                        onValueChange = { if (it.length <= 2) minute = it },
                        label         = { Text("Minuta") },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true
                    )
                }

                Text("Kategoria", style = MaterialTheme.typography.labelMedium)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    EventCategory.entries.take(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick  = { selectedCategory = cat },
                            label    = { Text(cat.emoji, fontSize = 16.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    EventCategory.entries.drop(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick  = { selectedCategory = cat },
                            label    = { Text(cat.emoji, fontSize = 16.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (EventCategory.entries.drop(3).size % 2 != 0) {
                        Spacer(Modifier.weight(1f))
                    }
                }

                Text(
                    "${selectedCategory.emoji}  ${selectedCategory.label}",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color      = AccentBlue,
                    modifier   = Modifier.fillMaxWidth(),
                    textAlign  = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    val h = hour.toIntOrNull()?.coerceIn(0, 23) ?: 9
                    val m = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    if (title.isNotBlank()) onAdd(title, h, m, selectedCategory)
                },
                enabled = title.isNotBlank()
            ) { Text("Dodaj") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}
