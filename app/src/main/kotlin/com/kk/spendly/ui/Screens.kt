package com.kk.spendly.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kk.spendly.BuildConfig
import com.kk.spendly.data.TxnEntity
import com.kk.spendly.data.isManual
import com.kk.spendly.parser.Categorizer
import com.kk.spendly.update.UpdateState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun rupees(paise: Long): String =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }.format(paise / 100.0)

internal val HeroGradient = Brush.linearGradient(listOf(Palette.AccentLight, Palette.AccentDark))

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Budgets("Budgets", Icons.Rounded.DonutLarge),
    Activity("Activity", Icons.Rounded.Receipt),
    Categories("Categories", Icons.Rounded.Category),
}

/**
 * Opens the add-category dialog. [merchant] is set when it was opened from a parsed transaction (the choice is remembered
 * for that merchant); [manualId] when it was opened from a manual entry (only that entry is recategorised).
 */
private data class AddRequest(val merchant: String?, val manualId: Long? = null)

private fun addRequestFor(t: TxnEntity) = if (t.isManual) AddRequest(null, t.id) else AddRequest(t.merchant)

@Composable
internal fun Screen(vm: MainViewModel = viewModel(), openTab: String? = null, onTabHandled: () -> Unit = {}) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var granted by remember {
        // The demo build never touches SMS, so it behaves as if access were already granted and hides the permission card.
        mutableStateOf(BuildConfig.DEMO || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = it[Manifest.permission.READ_SMS] == true
    }
    LaunchedEffect(granted) { if (granted) vm.backfill() }

    val month by vm.month.collectAsStateWithLifecycle()
    val txns by vm.txns.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val previousSpent by vm.previousSpent.collectAsStateWithLifecycle()
    val customs by vm.customCategories.collectAsStateWithLifecycle()
    val catalog = remember(customs) { Catalog.build(customs) }
    val limits = budgets.associate { it.category to it.limitPaise }
    val spentBy = txns.filter { it.isDebit }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amountPaise } }

    var tab by remember { mutableStateOf(Tab.Home) }
    LaunchedEffect(openTab) {
        if (openTab == MainActivity.TAB_BUDGETS) tab = Tab.Budgets
        if (openTab != null) onTabHandled()
    }
    var editing by remember { mutableStateOf<String?>(null) }
    var addRequest by remember { mutableStateOf<AddRequest?>(null) }
    var removing by remember { mutableStateOf<String?>(null) }
    var addingTxn by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var editingTxn by remember { mutableStateOf<TxnEntity?>(null) }
    var deletingTxn by remember { mutableStateOf<TxnEntity?>(null) }
    var pickingFor by remember { mutableStateOf<TxnEntity?>(null) }
    // The sheet shows the live row, so an edit or a category change appears in it straight away; a deleted row closes it.
    val selected = txns.firstOrNull { it.id == selectedId }
    LaunchedEffect(selected, selectedId) { if (selectedId != null && selected == null) selectedId = null }

    // Budget alerts need notification permission, which is asked separately from SMS access.
    var notifOn by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var notifDenied by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        notifOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (ok) vm.recheckBudgets() else notifDenied = true
    }
    fun enableAlerts() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !notifDenied) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Denied once (or switched off in system settings): Android won't show the prompt again, so open the settings page.
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }
    // Coming back from system settings: pick up the new state and deliver any warnings that were waiting.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (now && !notifOn) vm.recheckBudgets()
        notifOn = now
        vm.onResumed()
    }
    val update by vm.update.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalCatalog provides catalog) {
        GlassBackground {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    FloatingNav(
                        selected = tab,
                        onSelect = { if (tab != it) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); tab = it } },
                        onAdd = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); addingTxn = true },
                    )
                },
            ) { inner ->
                Column(Modifier.fillMaxSize().padding(inner).statusBarsPadding().padding(horizontal = 18.dp)) {
                    Header(month, canGoForward = vm.canGoForward, onPrev = { vm.shiftMonth(-1) }, onNext = { vm.shiftMonth(1) })
                    if (!granted) PermissionCard {
                        launcher.launch(
                            buildList {
                                add(Manifest.permission.READ_SMS)
                                add(Manifest.permission.RECEIVE_SMS)
                                if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                            }.toTypedArray(),
                        )
                    }
                    UpdateBanner(
                        state = update,
                        onUpdate = vm::startUpdate,
                        onLater = vm::dismissUpdate,
                        onAllowInstalls = {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}")),
                            )
                        },
                    )
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            // Slide in the direction of travel so the tab order feels spatial.
                            val forward = targetState.ordinal > initialState.ordinal
                            val offset: (Int) -> Int = { w -> if (forward) w / 3 else -w / 3 }
                            (slideInHorizontally(tween(380, easing = FastOutSlowInEasing), offset) + fadeIn(tween(300, delayMillis = 60))) togetherWith
                                (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { w -> -offset(w) } + fadeOut(tween(160)))
                        },
                        label = "tab",
                    ) {
                        when (it) {
                            Tab.Home -> HomeTab(txns, limits, spentBy, previousSpent, month, onOpenBudgets = { tab = Tab.Budgets })
                            Tab.Budgets -> BudgetsTab(spentBy, limits, alertsOff = !notifOn && !BuildConfig.DEMO, onEnableAlerts = { enableAlerts() }) { c -> editing = c }
                            Tab.Activity -> ActivityTab(txns, onOpen = { selectedId = it.id }, onCategoryPicked = vm::setTxnCategory, onNewCategory = { addRequest = addRequestFor(it) })
                            Tab.Categories -> CategoriesTab(spentBy, onAdd = { addRequest = AddRequest(null) }, onRemove = { c -> removing = c })
                        }
                    }
                }
            }
        }

        if (addingTxn) TxnFormDialog(initial = null, onDismiss = { addingTxn = false }) { amount, isDebit, note, category, timestamp ->
            vm.addManual(amount, isDebit, note, category, timestamp)
            addingTxn = false
        }
        selected?.let { t ->
            TransactionDetailSheet(
                txn = t,
                onDismiss = { selectedId = null },
                onChangeCategory = { pickingFor = t },
                onEdit = { editingTxn = t },
                onDelete = { deletingTxn = t },
            )
        }
        pickingFor?.let { t ->
            CategoryPicker(
                selected = t.category,
                onPick = { vm.setTxnCategory(t, it); pickingFor = null },
                onNew = { pickingFor = null; addRequest = addRequestFor(t) },
                onDismiss = { pickingFor = null },
            )
        }
        editingTxn?.let { t ->
            TxnFormDialog(initial = t, onDismiss = { editingTxn = null }) { amount, isDebit, note, category, timestamp ->
                vm.updateManual(t.id, amount, isDebit, note, category, timestamp)
                editingTxn = null
            }
        }
        deletingTxn?.let { t ->
            GlassDialog(
                title = "Delete this transaction?",
                onDismiss = { deletingTxn = null },
                confirmLabel = "Delete",
                destructive = true,
                onConfirm = { vm.deleteManual(t.id); deletingTxn = null; selectedId = null },
            ) {
                Text(
                    "${t.merchant} · ${rupees(t.amountPaise)} will be removed from your totals, charts and budgets. This can't be undone.",
                    color = Palette.TextMuted,
                )
            }
        }
        addRequest?.let { req ->
            AddCategoryDialog(existing = catalog.names, onDismiss = { addRequest = null }) { name, color, icon ->
                vm.addCategory(name, color, icon, req.merchant, req.manualId)
                addRequest = null
            }
        }
        removing?.let { name ->
            GlassDialog(
                title = "Remove $name?",
                onDismiss = { removing = null },
                confirmLabel = "Remove",
                destructive = true,
                onConfirm = { vm.removeCategory(name); removing = null },
            ) {
                Text("Its transactions move to Other, and its budget and merchant rules are deleted.", color = Palette.TextMuted)
            }
        }
        editing?.let { category ->
            BudgetDialog(category, limits[category], onDismiss = { editing = null }) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.setBudget(category, it)
                editing = null
                if (it != null && !notifOn) enableAlerts()
            }
        }
    }
}

// ---------- Header & navigation

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 0..11 -> "Good Morning"
    in 12..16 -> "Good Afternoon"
    else -> "Good Evening"
}

@Composable
private fun Header(month: Calendar, canGoForward: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting(), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            Text(
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(month.time),
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
            )
        }
        GlassIconButton(Icons.Rounded.ChevronLeft, "Previous month", onClick = onPrev)
        Spacer(Modifier.width(10.dp))
        GlassIconButton(Icons.Rounded.ChevronRight, "Next month", enabled = canGoForward, onClick = onNext)
    }
}

@Composable
private fun GlassIconButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Glass(Modifier.size(44.dp), shape = CircleShape, onClick = if (enabled) onClick else null, onClickLabel = description) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = if (enabled) Palette.Ink else Palette.Ink.copy(alpha = 0.25f))
        }
    }
}

@Composable
private fun FloatingNav(selected: Tab, onSelect: (Tab) -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp)) {
        Glass(Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(30.dp), tint = Color.White.copy(alpha = 0.78f)) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                val gap = 64.dp
                val slot = (maxWidth - gap) / 4
                val index = selected.ordinal
                val indicatorX by animateDpAsState(
                    targetValue = slot * index + if (index >= 2) gap else 0.dp,
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow),
                    label = "indicator",
                )
                // One pill glides behind whichever item is selected, wrapping both its icon and its label.
                Box(
                    Modifier.offset(x = indicatorX + 4.dp, y = 8.dp)
                        .size(slot - 8.dp, 54.dp)
                        .background(Palette.Accent.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                        .border(1.dp, Palette.Accent.copy(alpha = 0.18f), RoundedCornerShape(22.dp)),
                )
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    val tabs = Tab.entries
                    tabs.take(2).forEach { NavItem(it, it == selected, Modifier.weight(1f)) { onSelect(it) } }
                    Spacer(Modifier.width(gap))
                    tabs.drop(2).forEach { NavItem(it, it == selected, Modifier.weight(1f)) { onSelect(it) } }
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-20).dp).size(58.dp)
                .background(HeroGradient, CircleShape)
                .clip(CircleShape)
                .clickable(onClickLabel = "Add a transaction", onClick = onAdd)
                .semantics { contentDescription = "Add transaction" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp)) }
    }
}

private val NavPillWidth = 52.dp

@Composable
private fun NavItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color by animateColorAsState(if (selected) Palette.Accent else Palette.TextMuted, tween(250), label = "navColor")
    // No gray ripple on the glass bar: the gliding pill is the selection, and the icon dips slightly while pressed.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when { pressed -> 0.88f; selected -> 1.18f; else -> 1f },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "navScale",
    )
    Column(
        modifier.height(56.dp).clickable(interactionSource = interaction, indication = null, onClickLabel = tab.label, onClick = onClick).padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(NavPillWidth, 28.dp), contentAlignment = Alignment.Center) {
            Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale })
        }
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun UpdateBanner(state: UpdateState, onUpdate: () -> Unit, onLater: () -> Unit, onAllowInstalls: () -> Unit) {
    val release = state.release ?: return
    Glass(Modifier.fillMaxWidth().padding(bottom = 10.dp), tint = Palette.Accent.copy(alpha = 0.12f)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(42.dp).background(HeroGradient, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text("Update available", fontWeight = FontWeight.SemiBold)
                    val size = if (release.sizeBytes > 0) " · %.1f MB".format(release.sizeBytes / 1_048_576.0) else ""
                    Text("Spendly ${release.version}$size", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
            }
            when (state) {
                is UpdateState.Downloading -> {
                    ProgressBar(state.progress, Palette.Accent)
                    Text("Downloading… ${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
                is UpdateState.NeedsPermission -> {
                    Text("Allow Spendly to install updates, then come back and it will continue.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    UpdateActions("Allow", onLater, onAllowInstalls)
                }
                is UpdateState.Failed -> {
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = Palette.Danger)
                    UpdateActions("Retry", onLater, onUpdate)
                }
                else -> {
                    val highlight = release.highlight
                    if (highlight != null) Text(highlight, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 2)
                    UpdateActions("Update", onLater, onUpdate)
                }
            }
        }
    }
}

@Composable
private fun UpdateActions(primary: String, onLater: () -> Unit, onPrimary: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onLater) { Text("Later", color = Palette.TextMuted, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.height(40.dp).clip(RoundedCornerShape(14.dp)).background(HeroGradient)
                .clickable(onClickLabel = primary, onClick = onPrimary).padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) { Text(primary, color = Color.White, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun PermissionCard(onAllow: () -> Unit) {
    Glass(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).background(Palette.Accent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Sms, contentDescription = null, tint = Palette.Accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Track spending automatically", fontWeight = FontWeight.SemiBold)
                Text(
                    "Spendly reads bank and UPI alert texts on this phone. Nothing leaves your device.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
                )
                Button(onClick = onAllow, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.White)) {
                    Text("Allow SMS access")
                }
            }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, hint: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(60.dp).background(Palette.Accent.copy(alpha = 0.10f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(28.dp))
        }
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    }
}

// ---------- Home

@Composable
private fun HomeTab(
    txns: List<TxnEntity>,
    limits: Map<String, Long>,
    spentBy: Map<String, Long>,
    previousSpent: Long,
    month: Calendar,
    onOpenBudgets: () -> Unit,
) {
    val catalog = LocalCatalog.current
    val spent = spentBy.values.sum()
    val income = txns.filter { !it.isDebit }.sumOf { it.amountPaise }
    val budgeted = limits.values.sum()
    val budgetSpent = limits.keys.sumOf { spentBy[it] ?: 0L }
    val sorted = spentBy.entries.sortedByDescending { it.value }
    val slices = sorted.map { Slice(it.key, it.value, catalog.of(it.key).color) }
    val top = sorted.firstOrNull()?.key
    val animatedSpent by animateFloatAsState(spent.toFloat(), tween(800), label = "spent")

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Rounded.Savings, Palette.Positive, rupees(income), "Received", Modifier.weight(1f))
                StatTile(Icons.Rounded.Receipt, Palette.Accent, "${txns.size}", "Transactions", Modifier.weight(1f))
                StatTile(top?.let { catalog.of(it).icon } ?: Icons.Rounded.Category, top?.let { catalog.of(it).color } ?: Palette.TextMuted, top ?: "–", "Top category", Modifier.weight(1f))
            }
        }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(HeroGradient)) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(Color.White.copy(alpha = 0.10f), size.height * 0.75f, Offset(size.width * 0.95f, size.height * 0.1f))
                    drawCircle(Color.White.copy(alpha = 0.07f), size.height * 0.5f, Offset(size.width * 0.7f, size.height * 1.05f))
                }
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Wallet, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Spent this month", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                    }
                    Text(rupees(animatedSpent.toLong()), color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    if (previousSpent > 0) {
                        val change = ((spent - previousSpent) * 100 / previousSpent).toInt()
                        Row(
                            Modifier.padding(top = 6.dp).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (change > 0) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${kotlin.math.abs(change)}% ${if (change > 0) "more" else "less"} than last month", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.size(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val used = if (budgeted > 0) (budgetSpent * 100 / budgeted).toInt() else 0
                            Text(
                                if (budgeted > 0) "You've used $used% of your budget" else "Set budgets to stay on track",
                                color = Color.White, fontWeight = FontWeight.SemiBold,
                            )
                            if (budgeted > 0) {
                                Spacer(Modifier.size(10.dp))
                                ProgressBar(budgetSpent.toFloat() / budgeted, Color.White, track = Color.White.copy(alpha = 0.3f))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Box(
                            Modifier.size(42.dp).background(Color.White, CircleShape).clip(CircleShape).clickable(onClickLabel = "Open budgets", onClick = onOpenBudgets),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.ArrowForward, "Open budgets", tint = Palette.Accent) }
                    }
                }
            }
        }
        item {
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Where it went", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.size(14.dp))
                    if (slices.isEmpty()) {
                        EmptyState(Icons.Rounded.DonutLarge, "No spending yet", "Transactions from your SMS will show up here.")
                    } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Donut(slices) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(percent(slices.first().value, spent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(slices.first().label, style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            slices.take(5).forEach {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).background(it.color, CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Text("${it.label} · ${percent(it.value, spent)}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Last 7 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.size(14.dp))
                    BarChart(lastSevenDays(txns, month))
                }
            }
        }
    }
}

private fun percent(part: Long, whole: Long): String {
    val p = part * 100 / whole.coerceAtLeast(1)
    return if (p == 0L && part > 0) "<1%" else "$p%"
}

@Composable
private fun StatTile(icon: ImageVector, tint: Color, value: String, label: String, modifier: Modifier) {
    Glass(modifier, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.size(34.dp).background(tint.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(10.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted, maxLines = 1)
        }
    }
}

/** Daily debit totals for the 7 days ending today (or the month's last day when browsing a past month). */
private fun lastSevenDays(txns: List<TxnEntity>, month: Calendar): List<Pair<String, Long>> {
    val now = Calendar.getInstance()
    val last = if (now.get(Calendar.YEAR) == month.get(Calendar.YEAR) && now.get(Calendar.MONTH) == month.get(Calendar.MONTH)) {
        now
    } else (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) }
    last.set(Calendar.HOUR_OF_DAY, 0); last.set(Calendar.MINUTE, 0); last.set(Calendar.SECOND, 0); last.set(Calendar.MILLISECOND, 0)
    val label = SimpleDateFormat("EEEEE", Locale.getDefault())
    return (6 downTo 0).map { back ->
        val from = (last.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -back) }
        val to = (from.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        label.format(from.time) to txns.filter { it.isDebit && it.timestamp >= from.timeInMillis && it.timestamp < to.timeInMillis }
            .sumOf { it.amountPaise }
    }
}

// ---------- Budgets & categories

@Composable
private fun BudgetsTab(
    spentBy: Map<String, Long>,
    limits: Map<String, Long>,
    alertsOff: Boolean,
    onEnableAlerts: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val catalog = LocalCatalog.current
    val categories = catalog.all.filter { it.name != Categorizer.INCOME }.map { it.name }
        .sortedByDescending { c -> limits[c]?.let { (spentBy[c] ?: 0L).toFloat() / it } ?: -1f }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
        if (alertsOff) item(key = "alerts-off") {
            Glass(Modifier.fillMaxWidth(), tint = Palette.Warning.copy(alpha = 0.14f)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = Palette.Warning)
                    Column(Modifier.weight(1f)) {
                        Text("Budget alerts are off", fontWeight = FontWeight.SemiBold)
                        Text("Turn on notifications to be warned at 80% and 100% of a budget.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    }
                    TextButton(onClick = onEnableAlerts) { Text("Turn on", color = Palette.Accent, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        item { Text("Tap a category to set a monthly budget. You'll get an alert at 80% and 100%.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall) }
        items(categories, key = { it }) { category ->
            val amount = spentBy[category] ?: 0L
            val limit = limits[category]
            val fraction = if (limit != null) amount.toFloat() / limit else 0f
            val barColor = when {
                limit == null -> catalog.of(category).color
                fraction >= 1f -> Palette.Danger
                fraction >= 0.8f -> Palette.Warning
                else -> Palette.Accent
            }
            val summary = when {
                limit != null -> "${rupees(amount)} of ${rupees(limit)}"
                amount > 0 -> "${rupees(amount)} spent · no budget"
                else -> "No budget set"
            }
            Glass(Modifier.fillMaxWidth(), onClick = { onEdit(category) }, onClickLabel = "Edit $category budget") {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(category)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(category, fontWeight = FontWeight.SemiBold)
                            Text(summary, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                        if (limit != null) Text("${(fraction * 100).toInt()}%", color = barColor, fontWeight = FontWeight.SemiBold)
                    }
                    if (limit != null) {
                        Spacer(Modifier.size(12.dp))
                        ProgressBar(fraction, barColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoriesTab(spentBy: Map<String, Long>, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    val catalog = LocalCatalog.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Your categories. Built-in ones can't be removed; custom ones can.",
                    color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Color.White)) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
        }
        items(catalog.all, key = { it.name }) { info ->
            Glass(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(info.name)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            (spentBy[info.name]?.let { "${rupees(it)} this month" } ?: "Nothing this month") + if (info.custom) " · Custom" else "",
                            style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
                        )
                    }
                    if (info.custom) IconButton(onClick = { onRemove(info.name) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Remove ${info.name}", tint = Palette.Danger)
                    }
                }
            }
        }
    }
}

@Composable
internal fun Avatar(category: String) {
    val style = LocalCatalog.current.of(category)
    Box(Modifier.size(42.dp).background(style.color.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(style.icon, contentDescription = category, tint = style.color, modifier = Modifier.size(22.dp))
    }
}

// ---------- Activity

private enum class Filter(val label: String) { All("All"), Spent("Spent"), Received("Received") }

internal fun dayLabel(ts: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = ts }
    val today = Calendar.getInstance()
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        same(day, today) -> "Today"
        same(day, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(ts))
    }
}

@Composable
private fun ActivityTab(
    txns: List<TxnEntity>,
    onOpen: (TxnEntity) -> Unit,
    onCategoryPicked: (TxnEntity, String) -> Unit,
    onNewCategory: (TxnEntity) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Filter.All) }
    val visible = txns.filter {
        (filter == Filter.All || (filter == Filter.Spent) == it.isDebit) &&
            (query.isBlank() || it.merchant.contains(query.trim(), ignoreCase = true) || it.category.contains(query.trim(), ignoreCase = true))
    }
    // Rows arrive newest first, so grouping by label keeps days in order.
    val grouped = visible.groupBy { dayLabel(it.timestamp) }

    Column(Modifier.fillMaxSize()) {
        GlassField(
            value = query,
            onValueChange = { query = it },
            label = null,
            placeholder = "Search merchant or category",
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") }
            },
        )
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.entries.forEach {
                FilterChip(
                    selected = filter == it, onClick = { filter = it }, label = { Text(it.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Palette.Accent, selectedLabelColor = Color.White,
                        containerColor = Color.White.copy(alpha = 0.65f),
                    ),
                    border = null,
                )
            }
        }
        if (grouped.isEmpty()) {
            EmptyState(
                Icons.Rounded.Receipt,
                if (txns.isEmpty()) "No transactions this month" else "Nothing matches",
                if (txns.isEmpty()) "New alerts appear here, or tap + to add one." else "Try a different search or filter.",
            )
        } else LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            grouped.forEach { (label, rows) ->
                item(key = "header-$label") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
                        val daySpend = rows.filter { it.isDebit }.sumOf { it.amountPaise }
                        if (daySpend > 0) Text("-${rupees(daySpend)}", style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted)
                    }
                }
                items(rows, key = { it.id }) { TxnRow(it, onOpen, onCategoryPicked, onNewCategory) }
            }
        }
    }
}

@Composable
private fun TxnRow(
    t: TxnEntity,
    onOpen: (TxnEntity) -> Unit,
    onCategoryPicked: (TxnEntity, String) -> Unit,
    onNewCategory: (TxnEntity) -> Unit,
) {
    val catalog = LocalCatalog.current
    var open by remember { mutableStateOf(false) }
    val amount = (if (t.isDebit) "-" else "+") + rupees(t.amountPaise)
    Glass(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        tint = Color.White.copy(alpha = 0.55f),
        onClick = { onOpen(t) },
        onClickLabel = "View details",
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp).semantics { contentDescription = "${t.merchant}, $amount, ${t.category}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(t.category)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.merchant, maxLines = 1, fontWeight = FontWeight.Medium)
                Text(
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t.timestamp)) + if (t.isManual) " · Manual" else "",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, color = if (t.isDebit) Palette.Ink else Palette.Positive, fontWeight = FontWeight.SemiBold)
                Box {
                    Text(
                        t.category, style = MaterialTheme.typography.labelMedium, color = Palette.Accent,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Change category") { open = true }.padding(vertical = 4.dp, horizontal = 2.dp),
                    )
                    if (open) CategoryPicker(
                        selected = t.category,
                        onPick = { open = false; onCategoryPicked(t, it) },
                        onNew = { open = false; onNewCategory(t) },
                        onDismiss = { open = false },
                    )
                }
            }
        }
    }
}

/**
 * Category chooser: a grid of icon tiles that fits the screen. It wraps its content up to roughly half the
 * screen height and then scrolls, so any number of custom categories stays usable.
 */
@Composable
internal fun CategoryPicker(selected: String, onPick: (String) -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    val catalog = LocalCatalog.current
    val maxGridHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Glass(Modifier.fillMaxWidth().padding(horizontal = 22.dp), shape = RoundedCornerShape(32.dp), tint = Color(0xFFFBFAFF)) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 10.dp)) {
                Text("Choose category", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.size(14.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 84.dp),
                    modifier = Modifier.heightIn(max = maxGridHeight),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(catalog.all, key = { it.name }) { c ->
                        val on = c.name == selected
                        Column(
                            Modifier.clip(RoundedCornerShape(18.dp))
                                .background(if (on) c.color.copy(alpha = 0.16f) else Palette.SurfaceHigh.copy(alpha = 0.45f))
                                .then(if (on) Modifier.border(2.dp, c.color, RoundedCornerShape(18.dp)) else Modifier)
                                .clickable(onClickLabel = "Choose ${c.name}") { onPick(c.name) }
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(40.dp).background(c.color.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(c.icon, null, tint = c.color, modifier = Modifier.size(22.dp))
                            }
                            Text(c.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    item(key = "new-category") {
                        Column(
                            Modifier.clip(RoundedCornerShape(18.dp))
                                .border(1.5.dp, Palette.Accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                                .clickable(onClickLabel = "New category", onClick = onNew)
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(40.dp).background(Palette.Accent.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Add, null, tint = Palette.Accent, modifier = Modifier.size(22.dp))
                            }
                            Text("New", style = MaterialTheme.typography.labelMedium, color = Palette.Accent, maxLines = 1)
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel", color = Palette.TextMuted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------- Dialogs

internal val DestructiveGradient = Brush.linearGradient(listOf(Color(0xFFF87171), Color(0xFFDC2626)))

/** Shared frosted dialog shell: rounded glass card, title, content, a quiet secondary action and a gradient primary button. */
@Composable
internal fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    secondaryLabel: String = "Cancel",
    onSecondary: () -> Unit = onDismiss,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Glass(Modifier.fillMaxWidth().padding(horizontal = 22.dp), shape = RoundedCornerShape(32.dp), tint = Color(0xFFFBFAFF)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { content() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onSecondary) { Text(secondaryLabel, color = Palette.TextMuted, fontWeight = FontWeight.SemiBold) }
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (destructive) DestructiveGradient else HeroGradient)
                            .graphicsLayer { alpha = if (confirmEnabled) 1f else 0.4f }
                            .clickable(enabled = confirmEnabled, onClickLabel = confirmLabel, onClick = onConfirm),
                        contentAlignment = Alignment.Center,
                    ) { Text(confirmLabel, color = Color.White, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.TextMuted, fontWeight = FontWeight.SemiBold)

@Composable
internal fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supporting: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        prefix = prefix?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Palette.SurfaceHigh.copy(alpha = 0.45f), unfocusedContainerColor = Palette.SurfaceHigh.copy(alpha = 0.35f),
            focusedBorderColor = Palette.Accent, unfocusedBorderColor = Color.Transparent,
            focusedLabelColor = Palette.Accent, cursorColor = Palette.Accent,
        ),
    )
}

/** Large centered amount entry used for transactions and budgets, so money always looks the same. */
@Composable
internal fun AmountInput(value: String, onValueChange: (String) -> Unit, tint: Color, decimal: Boolean) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val style = MaterialTheme.typography.displaySmall.copy(color = tint, fontWeight = FontWeight.SemiBold)
    Box(
        Modifier.fillMaxWidth().background(tint.copy(alpha = 0.08f), RoundedCornerShape(22.dp)).padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("₹", style = style.copy(color = tint.copy(alpha = 0.6f)))
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 28.dp, max = 190.dp).focusRequester(focus),
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(tint),
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text("0", style = style.copy(color = tint.copy(alpha = 0.25f)))
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Palette.SurfaceHigh.copy(alpha = 0.55f), RoundedCornerShape(16.dp)).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) HeroGradient else SolidColor(Color.Transparent))
                    .clickable(onClickLabel = label) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) Color.White else Palette.TextMuted, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun BudgetDialog(category: String, current: Long?, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    var text by remember { mutableStateOf(current?.let { (it / 100).toString() } ?: "") }
    val info = LocalCatalog.current.of(category)
    GlassDialog(
        title = "$category budget",
        onDismiss = onDismiss,
        confirmLabel = "Save budget",
        onConfirm = { onSave(text.toLongOrNull()?.times(100)) },
        secondaryLabel = if (current != null) "Remove" else "Cancel",
        onSecondary = { if (current != null) onSave(null) else onDismiss() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).background(info.color.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(info.icon, null, tint = info.color)
            }
            Text("Monthly limit. You'll get an alert at 80% and 100%.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        AmountInput(text, { v -> text = v.filter { it.isDigit() }.take(8) }, tint = Palette.Accent, decimal = false)
    }
}

@Composable
private fun AddCategoryDialog(existing: List<String>, onDismiss: () -> Unit, onCreate: (String, Long, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(CategoryChoices.colors.first()) }
    var iconKey by remember { mutableStateOf(CategoryChoices.icons.keys.first()) }
    val trimmed = name.trim()
    val duplicate = existing.any { it.equals(trimmed, ignoreCase = true) }
    val accent by animateColorAsState(Color(color), tween(250), label = "previewColor")

    GlassDialog(
        title = "New category",
        onDismiss = onDismiss,
        confirmLabel = "Add category",
        confirmEnabled = trimmed.isNotEmpty() && !duplicate,
        onConfirm = { onCreate(trimmed, color, iconKey) },
    ) {
        // Live preview so the icon and color choices are visible as they change.
        Row(
            Modifier.fillMaxWidth().background(accent.copy(alpha = 0.10f), RoundedCornerShape(20.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(52.dp).background(accent.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(CategoryChoices.icon(iconKey), null, tint = accent, modifier = Modifier.size(26.dp))
            }
            Text(
                trimmed.ifEmpty { "Category name" },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = if (trimmed.isEmpty()) Palette.TextMuted else Palette.Ink, maxLines = 1,
            )
        }
        GlassField(
            name, { name = it.take(20) }, "Name",
            isError = duplicate, supporting = if (duplicate) "That category already exists" else null,
        )
        SectionLabel("Icon")
        CategoryChoices.icons.entries.chunked(4).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                rowItems.forEach { (key, icon) ->
                    val selected = key == iconKey
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (selected) accent.copy(alpha = 0.16f) else Palette.SurfaceHigh.copy(alpha = 0.5f))
                            .then(if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(18.dp)) else Modifier)
                            .clickable(onClickLabel = "Choose icon $key") { iconKey = key },
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, tint = if (selected) accent else Palette.TextMuted, modifier = Modifier.size(24.dp)) }
                }
            }
        }
        SectionLabel("Color")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CategoryChoices.colors.forEach { c ->
                val selected = c == color
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .then(if (selected) Modifier.border(2.dp, Color(c), CircleShape) else Modifier)
                        .clickable(onClickLabel = "Choose color") { color = c }
                        .padding(if (selected) 5.dp else 2.dp),
                ) { Box(Modifier.fillMaxSize().background(Color(c), CircleShape)) }
            }
        }
    }
}
