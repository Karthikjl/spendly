package com.kk.spendly.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kk.spendly.BuildConfig
import com.kk.spendly.data.AppDatabase
import com.kk.spendly.data.Budget
import com.kk.spendly.data.CustomCategory
import com.kk.spendly.data.MANUAL_PREFIX
import com.kk.spendly.data.Repository
import com.kk.spendly.data.TxnEntity
import com.kk.spendly.data.isManual
import com.kk.spendly.parser.Categorizer
import com.kk.spendly.sms.BudgetAlerts
import java.util.UUID
import com.kk.spendly.sms.SmsBackfill
import com.kk.spendly.update.UpdateState
import com.kk.spendly.update.UpdateWorker
import com.kk.spendly.update.Updater
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()
    val month = MutableStateFlow(startOfMonth(Calendar.getInstance()))

    val txns = month.flatMapLatest { start -> dao.between(start.timeInMillis, nextMonth(start).timeInMillis) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val previousSpent = month.flatMapLatest { start -> dao.spentBetween(shift(start, -1).timeInMillis, start.timeInMillis) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    // The 7-day strip on Home reaches back past the start of the month (early in a month it spans two), so it has its own query.
    val week = month.flatMapLatest { start -> weekOf(start) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customCategories = dao.customCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val budgets = dao.budgets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val updatePrefs = app.getSharedPreferences("updates", Context.MODE_PRIVATE)
    val update = MutableStateFlow<UpdateState>(UpdateState.None)

    // Must stay below the properties it touches: viewModelScope runs on the main thread and can start work immediately.
    init {
        // Deliver any warning that is due but wasn't shown (e.g. notifications were off, or an SMS arrived while the app was dead).
        recheckBudgets()
        checkForUpdate()
    }

    /** Asks GitHub once per app launch whether a newer release exists; the banner stays hidden for a version the user dismissed. */
    private fun checkForUpdate() = viewModelScope.launch {
        if (BuildConfig.DEV_BUILD) return@launch // test builds never offer to update to a release
        val info = Updater.fetchLatest() ?: return@launch
        val isNewer = Updater.isNewer(info.version, Updater.installedVersion(getApplication()))
        if (isNewer && updatePrefs.getString("dismissed_version", null) != info.version) update.value = UpdateState.Available(info)
    }

    fun dismissUpdate() {
        update.value.release?.let { updatePrefs.edit().putString("dismissed_version", it.version).apply() }
        update.value = UpdateState.None
    }

    fun startUpdate() {
        val info = update.value.release ?: return
        val app = getApplication<Application>()
        if (!app.packageManager.canRequestPackageInstalls()) {
            update.value = UpdateState.NeedsPermission(info)
            return
        }
        update.value = UpdateState.Downloading(info, 0f)
        viewModelScope.launch {
            try {
                val apk = Updater.download(app, info) { update.value = UpdateState.Downloading(info, it) }
                Updater.install(app, apk)
                // Android now shows its own "Install this update?" screen; if the user backs out the banner is ready to retry.
                update.value = UpdateState.Available(info)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                update.value = UpdateState.Failed(info, e.message ?: "The update failed. Please try again.")
            }
        }
    }

    /** Back from the "allow installs" settings page: carry on with the update if the user granted it. */
    fun onResumed() {
        if (update.value is UpdateState.NeedsPermission && getApplication<Application>().packageManager.canRequestPackageInstalls()) startUpdate()
    }

    val canGoForward get() = month.value.timeInMillis < startOfMonth(Calendar.getInstance()).timeInMillis

    /** Creates a category; when [assignTo] is set (the transaction it was created from), that transaction is filed into it. */
    fun addCategory(name: String, colorArgb: Long, iconKey: String, assignTo: TxnEntity? = null) = viewModelScope.launch {
        dao.addCategory(CustomCategory(name, colorArgb, iconKey))
        if (assignTo != null) applyCategory(assignTo, name)
    }

    /** Records a cash or other off-SMS transaction. */
    fun addManual(
        amountPaise: Long,
        isDebit: Boolean,
        merchant: String,
        category: String,
        timestamp: Long = System.currentTimeMillis(),
    ) = viewModelScope.launch {
        val finalCategory = if (isDebit) category else Categorizer.INCOME
        dao.insert(
            TxnEntity(
                dedupeKey = "$MANUAL_PREFIX${UUID.randomUUID()}",
                amountPaise = amountPaise,
                isDebit = isDebit,
                merchant = merchant.ifBlank { "Cash" },
                category = finalCategory,
                account = null,
                timestamp = timestamp,
            ),
        )
        if (isDebit) BudgetAlerts.check(getApplication(), dao, finalCategory)
    }

    /** Fixes a manual entry. Transactions parsed from SMS are never touched: the query only matches manual rows. */
    fun updateManual(id: Long, amountPaise: Long, isDebit: Boolean, merchant: String, category: String, timestamp: Long) =
        viewModelScope.launch {
            val finalCategory = if (isDebit) category else Categorizer.INCOME
            dao.updateManual(id, amountPaise, isDebit, merchant.ifBlank { "Cash" }, finalCategory, timestamp)
            if (isDebit) BudgetAlerts.check(getApplication(), dao, finalCategory)
        }

    fun deleteManual(id: Long) = viewModelScope.launch { dao.deleteManual(id) }

    fun removeCategory(name: String) = viewModelScope.launch { Repository.removeCategory(AppDatabase.get(getApplication()), name) }

    fun setBudget(category: String, limitPaise: Long?) = viewModelScope.launch {
        if (limitPaise == null || limitPaise <= 0) {
            dao.deleteBudget(category)
        } else {
            dao.saveBudget(Budget(category, limitPaise))
            // A budget set on a category that's already past 80% should warn straight away.
            BudgetAlerts.check(getApplication(), dao, category)
        }
    }

    /** Re-evaluates every budget; used once notifications get switched on so pending warnings are delivered. */
    fun recheckBudgets() = viewModelScope.launch(Dispatchers.IO) {
        dao.allBudgets().forEach { BudgetAlerts.check(getApplication(), dao, it.category) }
    }

    fun shiftMonth(delta: Int) {
        val next = shift(month.value, delta)
        if (next.timeInMillis <= startOfMonth(Calendar.getInstance()).timeInMillis) month.value = next
    }

    // The inbox scan reads every SMS, so keep it off the main thread.
    fun backfill() = viewModelScope.launch(Dispatchers.IO) {
        SmsBackfill.run(getApplication())
        dao.allBudgets().forEach { BudgetAlerts.check(getApplication(), dao, it.category) }
    }

    fun setTxnCategory(t: TxnEntity, category: String) = viewModelScope.launch { applyCategory(t, category) }

    /** Manual entries change on their own; parsed spending at a named merchant is remembered for that merchant. */
    private suspend fun applyCategory(t: TxnEntity, category: String) {
        if (t.isManual) dao.setCategoryById(t.id, category) else Repository.setCategory(dao, t, category)
        // The new category may have a budget that this change pushes past a warning level.
        if (t.isDebit) BudgetAlerts.check(getApplication(), dao, category)
    }

    private fun weekOf(start: Calendar): Flow<List<TxnEntity>> {
        val end = Dates.weekEnd(start)
        return dao.between(Dates.startMillis(end.minusDays(6)), Dates.startMillis(end.plusDays(1)))
    }

    private fun shift(c: Calendar, months: Int) = (c.clone() as Calendar).apply { add(Calendar.MONTH, months) }
    private fun nextMonth(c: Calendar) = shift(c, 1)

    private fun startOfMonth(c: Calendar) = (c.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
}

class MainActivity : ComponentActivity() {
    /** Tab a notification asked us to open; cleared once the screen has switched. */
    private var requestedTab by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTab = intent.getStringExtra(EXTRA_TAB)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        requestedTab = intent?.getStringExtra(EXTRA_TAB)
        super.onCreate(savedInstanceState)
        UpdateWorker.schedule(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            FintechTheme {
                // rememberSaveable keeps the intro from replaying on rotation.
                var showIntro by rememberSaveable { mutableStateOf(true) }
                Box(Modifier.fillMaxSize()) {
                    Screen(openTab = requestedTab, onTabHandled = { requestedTab = null })
                    if (showIntro) IntroOverlay { showIntro = false }
                }
            }
        }
    }

    companion object {
        const val EXTRA_TAB = "open_tab"
        const val TAB_BUDGETS = "budgets"
    }
}
