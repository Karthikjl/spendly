package com.kk.spendly.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.res.ResourcesCompat
import com.kk.spendly.R
import com.kk.spendly.data.TxnDao
import com.kk.spendly.ui.Catalog
import com.kk.spendly.ui.MainActivity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object BudgetAlerts {
    private const val CHANNEL = "budget_alerts"
    private const val WARN_PERCENT = 80
    private const val AMBER = 0xFFF59E0B.toInt()
    private const val RED = 0xFFEF4444.toInt()

    /** True when the system will actually show our notifications (permission granted and not switched off in settings). */
    fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Notifies once per month per category when spending crosses 80% and again at 100% of its budget. */
    suspend fun check(context: Context, dao: TxnDao, category: String) {
        val budget = dao.budgetFor(category)?.takeIf { it.limitPaise > 0 } ?: return
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        val spent = dao.spentIn(category, start.timeInMillis, end.timeInMillis)
        val percent = (spent * 100 / budget.limitPaise).toInt()
        val level = when {
            percent >= 100 -> 100
            percent >= WARN_PERCENT -> WARN_PERCENT
            else -> return
        }

        val prefs = context.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE)
        val key = "${start.get(Calendar.YEAR)}-${start.get(Calendar.MONTH)}-$category"
        if (prefs.getInt(key, 0) >= level) return
        // Without permission nothing is shown, and the alert must stay pending so it fires once the user turns notifications on.
        if (!canNotify(context)) return

        val categoryColor = Catalog.build(dao.allCustomCategories()).of(category).color.toArgb()
        val month = SimpleDateFormat("MMMM", Locale.getDefault()).format(start.time)
        if (post(context, category, categoryColor, month, spent, budget.limitPaise, percent)) prefs.edit().putInt(key, level).apply()
    }

    private fun post(context: Context, category: String, categoryColor: Int, month: String, spent: Long, limit: Long, percent: Int): Boolean {
        val money = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
        fun rupees(paise: Long) = money.format(paise / 100.0)
        val over = spent >= limit

        val title = if (over) "$category budget exceeded" else "$category budget · $percent% used"
        val text = if (over) {
            if (spent == limit) "You've used the whole ${rupees(limit)} budget."
            else "${rupees(spent)} spent, ${rupees(spent - limit)} over your ${rupees(limit)} budget."
        } else {
            "${rupees(spent)} of ${rupees(limit)} spent · ${rupees(limit - spent)} left"
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Budget alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Warns you at 80% and 100% of a category budget"
            },
        )
        val open = PendingIntent.getActivity(
            context, category.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_BUDGETS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(if (over) RED else AMBER)
            .setLargeIcon(badge(context, category, categoryColor))
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("Spendly · $month")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setProgress(100, percent.coerceAtMost(100), false)
            .addAction(0, "View budgets", open)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(category.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /** A round badge in the category's own color with its initial, so each alert is recognizable at a glance. */
    private fun badge(context: Context, label: String, color: Int): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.apply {
            this.color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = size * 0.46f
            typeface = ResourcesCompat.getFont(context, R.font.poppins_semibold)
        }
        val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(label.take(1).uppercase(), size / 2f, baseline, paint)
        return bitmap
    }
}
