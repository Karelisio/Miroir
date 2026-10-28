package com.karelisio.miroir.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.karelisio.miroir.container
import com.karelisio.miroir.data.prefs.AppPrefs
import com.karelisio.miroir.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.ZoneId

/** Rappel quotidien à heure fixe, en alarme exacte quand l'autorisation est accordée. */
object DailyReminder {
    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, DailyReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(context: Context, prefs: AppPrefs) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)
        am.cancel(pi)
        if (!prefs.reminderEnabled || !prefs.paired) return
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var next = now.toLocalDate().atTime(prefs.reminderHour, prefs.reminderMinute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val at = next.atZone(zone).toInstant().toEpochMilli()
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 15 * 60_000L, pi)
        }
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        receiverScope.launch {
            try {
                val repo = app.container.repository
                withTimeoutOrNull(7_000) { runCatching { repo.sync() } }
                val prefs = repo.prefs.current()
                val today = repo.ensureToday()
                if (today != null && today.myAnswer == null && prefs.partnerJoined) {
                    Notifier.showDaily(app, prefs, today.questionText, today.partnerAnswered)
                }
                DailyReminder.schedule(app, prefs)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Replanifie après redémarrage, mise à jour, changement d'heure ou d'autorisation. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        receiverScope.launch {
            try {
                DailyReminder.schedule(app, app.container.prefs.current())
                SyncScheduler.schedulePeriodic(app)
            } finally {
                pending.finish()
            }
        }
    }
}
