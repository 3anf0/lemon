package com.smartcal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.smartcal.app.data.model.*
import com.smartcal.app.ui.theme.AccentBlue
import com.smartcal.app.viewmodel.FinanceViewModel
import java.time.format.DateTimeFormatter

private val Green  = Color(0xFF34C759)
private val Red    = Color(0xFFFF3B30)

@Composable
fun FinanceScreen(vm: FinanceViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    // Box allows FAB to float above content, pinned at bottom-right
    Box(modifier = Modifier.fillMaxSize()) {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // Bottom padding = FAB height + margin so last item is never hidden
            contentPadding = PaddingValues(top = 20.dp, bottom = 88.dp)
        ) {

            item {
                Text(
                    "Finanse",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Przychody i wydatki",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard(
                        summary  = state.currentMonthSummary,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCard(
                        summary  = state.lastMonthSummary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                SummaryCard(
                    summary  = state.yesterdaySummary,
                    modifier = Modifier.fillMaxWidth(),
                    expanded = true
                )
            }

            item {
                Text(
                    "Podział kategorii — ten miesiąc",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                CategoryBreakdownCard(state.currentMonthSummary)
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Wszystkie transakcje",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.allTransactions.isEmpty()) {
                item {
                    Box(
                        modifier          = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment  = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💳", fontSize = 40.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Brak transakcji",
                                color       = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                textAlign   = TextAlign.Center
                            )
                            Text(
                                "Naciśnij + aby dodać",
                                color     = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                                fontSize  = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(state.allTransactions) { tx ->
                    TransactionRow(
                        tx       = tx,
                        onDelete = { vm.deleteTransaction(tx.id) }
                    )
                }
            }

        } // end LazyColumn

        // FAB pinned to bottom-right — always above content, never conflicts with delete buttons
        FloatingActionButton(
            onClick         = { vm.setAdding(true) },
            containerColor  = AccentBlue,
            modifier        = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.Add,
                contentDescription = "Dodaj transakcję",
                tint               = Color.White
            )
        }

    } // end Box

    if (state.isAddingTransaction) {
        AddTransactionDialog(
            onDismiss = { vm.setAdding(false) },
            onAdd     = { title, amount, type, category ->
                vm.addTransaction(title, amount, type, category)
                vm.setAdding(false)
            }
        )
    }
}

// ── Summary card ──────────────────────────────────────────────────────────

@Composable
fun SummaryCard(
    summary  : PeriodSummary,
    modifier : Modifier = Modifier,
    expanded : Boolean  = false
) {
    val balanceColor = when {
        summary.balance > 0 -> Green
        summary.balance < 0 -> Red
        else                -> MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                summary.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text       = formatMoney(summary.balance),
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = balanceColor
            )
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    LabelValue("+ Przychód", formatMoney(summary.income),   Green, Modifier.weight(1f))
                    LabelValue("- Wydatki",  formatMoney(summary.expenses), Red,   Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun LabelValue(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

// ── Category breakdown ────────────────────────────────────────────────────

@Composable
fun CategoryBreakdownCard(summary: PeriodSummary) {
    Card(
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier             = Modifier.padding(16.dp),
            verticalArrangement  = Arrangement.spacedBy(10.dp)
        ) {
            ExpenseCategory.entries.forEach { cat ->
                val amount = summary.byCategory[cat] ?: 0.0
                val color  = categoryColor(cat)
                Row(
                    modifier            = Modifier.fillMaxWidth(),
                    verticalAlignment   = Alignment.CenterVertically
                ) {
                    Text(cat.emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            cat.label,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        val maxAbs   = summary.byCategory.values
                            .maxOfOrNull { kotlin.math.abs(it) }
                            ?.takeIf { it > 0 } ?: 1.0
                        val progress = (kotlin.math.abs(amount) / maxAbs)
                            .toFloat().coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress   = { progress },
                            modifier   = Modifier.fillMaxWidth().height(4.dp).padding(top = 2.dp),
                            color      = color,
                            trackColor = color.copy(alpha = 0.15f)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        formatMoney(amount),
                        fontWeight = FontWeight.SemiBold,
                        color      = if (amount >= 0) Green else Red,
                        fontSize   = 14.sp
                    )
                }
            }
        }
    }
}

// ── Single transaction row ────────────────────────────────────────────────

@Composable
fun TransactionRow(
    tx       : com.smartcal.app.data.model.Transaction,
    onDelete : () -> Unit
) {
    val dateFmt  = DateTimeFormatter.ofPattern("dd.MM")
    val isIncome = tx.type == TransactionType.INCOME
    val color    = categoryColor(tx.category)

    Card(
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(tx.category.emoji, fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tx.title, fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${tx.category.label} · ${tx.date.format(dateFmt)}",
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
            Text(
                text       = "${if (isIncome) "+" else "-"} ${formatMoney(tx.amount)}",
                fontWeight = FontWeight.Bold,
                color      = if (isIncome) Green else Red,
                fontSize   = 15.sp
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector        = Icons.Default.Delete,
                    contentDescription = "Usuń",
                    tint               = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                    modifier           = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Add transaction dialog ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    onDismiss : () -> Unit,
    onAdd     : (String, Double, TransactionType, ExpenseCategory) -> Unit
) {
    var title     by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var type      by remember { mutableStateOf(TransactionType.EXPENSE) }
    var category  by remember { mutableStateOf(ExpenseCategory.FOOD) }

    LaunchedEffect(category) {
        type = if (category == ExpenseCategory.WORK) TransactionType.INCOME
               else TransactionType.EXPENSE
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowa transakcja", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                Row(
                    modifier                = Modifier.fillMaxWidth(),
                    horizontalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        TransactionType.INCOME  to "➕ Przychód",
                        TransactionType.EXPENSE to "➖ Wydatek"
                    ).forEach { (t, label) ->
                        FilterChip(
                            selected = type == t,
                            onClick  = { type = t },
                            label    = { Text(label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor =
                                    if (t == TransactionType.INCOME) Green.copy(0.2f)
                                    else Red.copy(0.2f)
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value         = title,
                    onValueChange = { title = it },
                    label         = { Text("Opis (np. Biedronka, Praca, Kasyno)") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )

                OutlinedTextField(
                    value           = amountStr,
                    onValueChange   = { amountStr = it.filter { c -> c.isDigit() || c == '.' } },
                    label           = { Text("Kwota (PLN)") },
                    modifier        = Modifier.fillMaxWidth(),
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix          = { Text("zł ") }
                )

                Text("Kategoria", style = MaterialTheme.typography.labelMedium)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    ExpenseCategory.entries.take(3).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick  = { category = cat },
                            label    = { Text(cat.emoji, fontSize = 18.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier              = Modifier.fillMaxWidth()
                ) {
                    ExpenseCategory.entries.drop(3).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick  = { category = cat },
                            label    = { Text(cat.emoji, fontSize = 18.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (ExpenseCategory.entries.drop(3).size % 2 != 0) {
                        Spacer(Modifier.weight(1f))
                    }
                }

                Text(
                    "${category.emoji}  ${category.label}",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color      = categoryColor(category),
                    modifier   = Modifier.fillMaxWidth(),
                    textAlign  = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    val amt = amountStr.toDoubleOrNull() ?: return@Button
                    val t   = if (title.isBlank()) category.label else title
                    onAdd(t, amt, type, category)
                },
                enabled = amountStr.toDoubleOrNull() != null && amountStr.isNotBlank()
            ) { Text("Dodaj") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

// ── Helpers ───────────────────────────────────────────────────────────────

fun formatMoney(amount: Double): String {
    val abs = kotlin.math.abs(amount)
    return if (abs >= 1000) "%.0f zł".format(abs)
    else                    "%.2f zł".format(abs)
}

fun categoryColor(cat: ExpenseCategory): Color = when (cat) {
    ExpenseCategory.WORK     -> Color(0xFF4A90E2)
    ExpenseCategory.FOOD     -> Color(0xFFFF9500)
    ExpenseCategory.FUEL     -> Color(0xFF34C759)
    ExpenseCategory.GAMBLING -> Color(0xFFAF52DE)
    ExpenseCategory.OTHER    -> Color(0xFF8E8E93)
}
