package com.kk.spendly.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kk.spendly.data.TxnEntity
import com.kk.spendly.data.isManual
import com.kk.spendly.parser.Categorizer
import com.kk.spendly.sms.SmsLookup
import com.kk.spendly.sms.SmsLookupResult
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Rupees to the paise when there is a fractional part, since the detail view is where exact figures matter. */
private fun exact(paise: Long): String =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = if (paise % 100 == 0L) 0 else 2
        maximumFractionDigits = 2
    }.format(paise / 100.0)

private fun plainAmount(paise: Long): String = BigDecimal(paise).movePointLeft(2).stripTrailingZeros().toPlainString()

private fun format(pattern: String, ts: Long): String = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ts))

/**
 * Everything known about one transaction. Parsed transactions show the message they came from, read from the phone's inbox
 * when the sheet opens; manual ones show Edit and Delete instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailSheet(
    txn: TxnEntity,
    onDismiss: () -> Unit,
    onChangeCategory: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    // Keyed on the fingerprint, not the whole row, so changing the category doesn't re-read the inbox.
    var lookup by remember(txn.id, txn.dedupeKey) { mutableStateOf<SmsLookupResult?>(null) }
    LaunchedEffect(txn.id, txn.dedupeKey) { lookup = if (txn.isManual) null else SmsLookup.find(context, txn) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFFBFAFF),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            TransactionDetailContent(txn, lookup, onChangeCategory, onEdit, onDelete)
        }
    }
}

/** The sheet's body, separate from the sheet itself so it can be drawn and checked on its own. */
@Composable
internal fun TransactionDetailContent(
    txn: TxnEntity,
    lookup: SmsLookupResult?,
    onChangeCategory: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val info = LocalCatalog.current.of(txn.category)
    val manual = txn.isManual
    val reference = (lookup as? SmsLookupResult.Found)?.sms?.reference

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).background(info.color.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(info.icon, contentDescription = null, tint = info.color, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(txn.merchant, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (txn.isDebit) "Spent" else "Received", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            }
        }

        Text(
            (if (txn.isDebit) "-" else "+") + exact(txn.amountPaise),
            style = MaterialTheme.typography.displaySmall,
            color = if (txn.isDebit) Palette.Ink else Palette.Positive,
        )

        Row(
            Modifier.clip(RoundedCornerShape(50)).background(info.color.copy(alpha = 0.14f))
                .clickable(onClickLabel = "Change category", onClick = onChangeCategory)
                .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(info.icon, contentDescription = null, tint = info.color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(txn.category, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.size(18.dp))
        }

        Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), tint = Color.White.copy(alpha = 0.6f)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                DetailRow("Date", format("EEE, d MMM yyyy", txn.timestamp))
                DetailRow("Time", format("h:mm a", txn.timestamp))
                val extra = buildList {
                    txn.account?.let { add("Account" to "•• $it") }
                    reference?.let { add("Reference" to it) }
                }
                DetailRow("Source", if (manual) "Added by you" else "Bank or UPI SMS", last = extra.isEmpty())
                extra.forEachIndexed { i, (label, value) -> DetailRow(label, value, last = i == extra.lastIndex) }
            }
        }

        if (manual) ManualActions(onEdit, onDelete) else OriginalMessageCard(lookup)
    }
}

@Composable
private fun DetailRow(label: String, value: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.width(96.dp))
        Text(value, modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (!last) HorizontalDivider(color = Palette.SurfaceHigh)
}

@Composable
private fun ManualActions(onEdit: () -> Unit, onDelete: () -> Unit) {
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), tint = Color.White.copy(alpha = 0.6f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Made a mistake?", fontWeight = FontWeight.SemiBold)
                Text(
                    "Fix the amount, note, date or category, or remove the entry.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("Delete", Icons.Rounded.Delete, Modifier.weight(1f), danger = true, onClick = onDelete)
                ActionButton("Edit", Icons.Rounded.Edit, Modifier.weight(1f), danger = false, onClick = onEdit)
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, modifier: Modifier, danger: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.height(48.dp).clip(shape)
            .then(if (danger) Modifier.border(1.5.dp, Palette.Danger.copy(alpha = 0.6f), shape) else Modifier.background(HeroGradient))
            .clickable(onClickLabel = "$label transaction", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (danger) Palette.Danger else Color.White
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OriginalMessageCard(result: SmsLookupResult?) {
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), tint = Color.White.copy(alpha = 0.6f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(34.dp).background(Palette.Accent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Sms, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(18.dp))
                }
                Text("Original message", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                (result as? SmsLookupResult.Found)?.let {
                    Text(
                        it.sms.sender, style = MaterialTheme.typography.labelMedium, color = Palette.Accent, maxLines = 1,
                        modifier = Modifier.background(Palette.Accent.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            when (result) {
                null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Palette.Accent, strokeWidth = 2.dp)
                    Text("Looking for the message…", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                }
                is SmsLookupResult.Found -> {
                    // Shaped like a received chat bubble so it reads as a message at a glance; long-press selects and copies.
                    SelectionContainer {
                        Text(
                            result.sms.body,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                                .background(Palette.SurfaceHigh.copy(alpha = 0.55f), RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp))
                                .padding(14.dp),
                        )
                    }
                    Text(
                        "Received ${format("EEE, d MMM · h:mm a", result.sms.receivedAt)}. Read from your messages just now; Spendly doesn't keep a copy.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
                    )
                }
                SmsLookupResult.NotFound -> MessageNote(
                    Icons.Rounded.SearchOff, "Couldn't find the message",
                    "It may have been deleted from your messages app.",
                )
                SmsLookupResult.NoPermission -> MessageNote(
                    Icons.Rounded.Lock, "SMS access is off",
                    "Turn on SMS permission for Spendly in your phone's settings to see the original message.",
                )
            }
        }
    }
}

@Composable
private fun MessageNote(icon: ImageVector, title: String, hint: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.size(22.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
    }
}

// ---------- Add / edit form

@OptIn(ExperimentalMaterial3Api::class)
private object NotInFuture : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= TxnDates.utcMidnightOf(System.currentTimeMillis())
}

@OptIn(ExperimentalMaterial3Api::class)
private val pickerColors: DatePickerColors
    @Composable get() = DatePickerDefaults.colors(
        containerColor = Palette.Surface,
        titleContentColor = Palette.TextMuted,
        headlineContentColor = Palette.Ink,
        weekdayContentColor = Palette.TextMuted,
        subheadContentColor = Palette.TextMuted,
        navigationContentColor = Palette.Ink,
        selectedDayContainerColor = Palette.Accent,
        selectedDayContentColor = Color.White,
        todayContentColor = Palette.Accent,
        todayDateBorderColor = Palette.Accent,
        currentYearContentColor = Palette.Accent,
        selectedYearContainerColor = Palette.Accent,
        selectedYearContentColor = Color.White,
    )

/** One form for adding a manual transaction and for fixing one: pass [initial] to edit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TxnFormDialog(
    initial: TxnEntity?,
    onDismiss: () -> Unit,
    onSave: (paise: Long, isExpense: Boolean, note: String, category: String, timestamp: Long) -> Unit,
) {
    val catalog = LocalCatalog.current
    var isExpense by remember { mutableStateOf(initial?.isDebit ?: true) }
    var amount by remember { mutableStateOf(initial?.let { plainAmount(it.amountPaise) } ?: "") }
    var note by remember { mutableStateOf(initial?.merchant ?: "") }
    var category by remember { mutableStateOf(initial?.category?.takeIf { it != Categorizer.INCOME } ?: Categorizer.OTHER) }
    var timestamp by remember { mutableLongStateOf(initial?.timestamp ?: System.currentTimeMillis()) }
    var pickingDate by remember { mutableStateOf(false) }
    val paise = amount.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
    val tint by animateColorAsState(if (isExpense) Palette.Accent else Palette.Positive, tween(250), label = "txnTint")
    val editing = initial != null

    GlassDialog(
        title = if (editing) "Edit transaction" else "Add transaction",
        onDismiss = onDismiss,
        confirmLabel = when {
            editing -> "Save changes"
            isExpense -> "Add expense"
            else -> "Add income"
        },
        confirmEnabled = paise > 0,
        onConfirm = { onSave(paise, isExpense, note.trim(), category, timestamp) },
    ) {
        Segmented(listOf("Expense", "Income"), if (isExpense) 0 else 1) { isExpense = it == 0 }
        AmountInput(
            amount,
            { v ->
                val f = v.filter { it.isDigit() || it == '.' }
                if (f.count { it == '.' } <= 1 && f.substringAfter('.', "").length <= 2) amount = f.take(10)
            },
            tint = tint, decimal = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50, 100, 500).forEach { add ->
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Palette.SurfaceHigh.copy(alpha = 0.55f))
                        .clickable(onClickLabel = "Add $add rupees") {
                            amount = ((amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) + add.toBigDecimal()).stripTrailingZeros().toPlainString()
                        }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("+₹$add", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Palette.Accent) }
            }
        }
        GlassField(note, { note = it.take(40) }, label = null, placeholder = "Add a note, e.g. Chai or Auto")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.SurfaceHigh.copy(alpha = 0.45f))
                .clickable(onClickLabel = "Change date") { pickingDate = true }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("${dayLabel(timestamp)} · ${format("h:mm a", timestamp)}", Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text("Change", color = Palette.Accent, style = MaterialTheme.typography.labelMedium)
        }
        if (isExpense) {
            SectionLabel("Category")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                catalog.all.filter { it.name != Categorizer.INCOME }.forEach { c ->
                    val selected = c.name == category
                    Row(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (selected) c.color.copy(alpha = 0.16f) else Palette.SurfaceHigh.copy(alpha = 0.5f))
                            .then(if (selected) Modifier.border(1.5.dp, c.color, RoundedCornerShape(50)) else Modifier)
                            .clickable(onClickLabel = "Choose ${c.name}") { category = c.name }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(c.icon, contentDescription = null, tint = c.color, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(c.name, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = TxnDates.utcMidnightOf(timestamp), selectableDates = NotInFuture)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { timestamp = TxnDates.withDate(timestamp, it) }
                    pickingDate = false
                }) { Text("OK", color = Palette.Accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel", color = Palette.TextMuted, fontWeight = FontWeight.SemiBold) } },
            colors = pickerColors,
        ) { DatePicker(state = state, colors = pickerColors, showModeToggle = false) }
    }
}
