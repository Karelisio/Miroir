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

/** Rappel quotidien à heure fixe (et relance du soir), en alarme exacte quand l'autorisation est accordée. */
object DailyReminder {
    const val EVENING_HOUR = 22
    const val EXTRA_EVENING = "miroir.evening"

    private fun pendingIntent(context: Context, evening: Boolean) = PendingIntent.getBroadcast(
        context, if (evening) 1 else 0, Intent(context, DailyReminderReceiver::class.java).putExtra(EXTRA_EVENING, evening),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Inutile si le rappel principal tombe déjà à 21 h ou plus tard. */
    fun eveningUseful(prefs: AppPrefs): Boolean = !prefs.reminderEnabled || prefs.reminderHour < EVENING_HOUR - 1

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(context: Context, prefs: AppPrefs) {
        scheduleOne(context, evening = false, enabled = prefs.reminderEnabled && prefs.paired, prefs.reminderHour, prefs.reminderMinute)
        scheduleOne(context, evening = true, enabled = prefs.eveningReminder && prefs.paired && eveningUseful(prefs), EVENING_HOUR, 0)
    }

    private fun scheduleOne(context: Context, evening: Boolean, enabled: Boolean, hour: Int, minute: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, evening)
        am.cancel(pi)
        if (!enabled) return
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var next = now.toLocalDate().atTime(hour, minute)
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
                    if (intent.getBooleanExtra(DailyReminder.EXTRA_EVENING, false)) {
                        Notifier.showEvening(app, prefs, today.questionText, today.partnerAnswered)
                    } else {
                        Notifier.showDaily(app, prefs, today.questionText, today.partnerAnswered)
                    }
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
