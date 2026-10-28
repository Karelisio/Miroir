package com.karelisio.miroir.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.karelisio.miroir.MainActivity
import com.karelisio.miroir.R
import com.karelisio.miroir.data.SyncEvent
import com.karelisio.miroir.data.prefs.AppPrefs

object Notifier {
    const val CH_DAILY = "daily"
    const val CH_DAILY_PRIORITY = "daily_priority"
    const val CH_PARTNER = "partner"
    const val CH_PARTNER_PRIORITY = "partner_priority"
    const val CH_REVEAL = "reveal"
    const val CH_UPDATES = "updates"
    const val CH_SYNC = "sync"

    const val ID_DAILY = 1
    const val ID_PARTNER = 2
    const val ID_REVEAL = 3
    const val ID_REACTION = 4
    const val ID_CUSTOM = 5
    const val ID_PAIRING = 6
    const val ID_UPDATE = 7
    const val ID_SYNC = 8
    const val ID_NUDGE = 9

    const val EXTRA_DESTINATION = "miroir.destination"
    const val EXTRA_DAY = "miroir.day"
    const val EXTRA_FULL_SCREEN = "miroir.full_screen"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val canBypass = nm.isNotificationPolicyAccessGranted
        val channels = buildList {
            add(NotificationChannel(CH_DAILY, "Question du jour", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Le rappel quotidien de votre question"
            })
            add(NotificationChannel(CH_PARTNER, "Réponses de l'autre", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Quand l'autre répond, réagit ou ajoute une question"
            })
            add(NotificationChannel(CH_REVEAL, "Révélations", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Quand vos deux réponses sont prêtes à être révélées"
            })
            add(NotificationChannel(CH_UPDATES, "Mises à jour", NotificationManager.IMPORTANCE_DEFAULT))
            add(NotificationChannel(CH_SYNC, "Synchronisation", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
            // setBypassDnd n'est pris en compte qu'à la création, et seulement avec l'accès "Ne pas déranger".
            if (canBypass) {
                add(NotificationChannel(CH_DAILY_PRIORITY, "Question du jour (prioritaire)", NotificationManager.IMPORTANCE_HIGH).apply {
                    setBypassDnd(true)
                    description = "Traverse le mode Ne pas déranger"
                })
                add(NotificationChannel(CH_PARTNER_PRIORITY, "Réponses de l'autre (prioritaire)", NotificationManager.IMPORTANCE_HIGH).apply {
                    setBypassDnd(true)
                    description = "Traverse le mode Ne pas déranger"
                })
            }
        }
        nm.createNotificationChannels(channels)
    }

    fun isAppInForeground(): Boolean = appInForeground()

    fun showInstallConfirmation(context: Context, confirm: Intent) = post(
        context, ID_UPDATE,
        base(context, CH_UPDATES).setContentTitle("Installer la mise à jour")
            .setContentText("Touche pour confirmer l'installation de Miroir.")
            .setContentIntent(PendingIntent.getActivity(context, ID_UPDATE + 300, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build(),
    )

    private fun appInForeground(): Boolean =
        runCatching { ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }.getOrDefault(false)

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channel(context: Context, base: String, prefs: AppPrefs): String {
        if (!prefs.bypassDnd) return base
        val nm = context.getSystemService(NotificationManager::class.java)
        val prio = if (base == CH_DAILY) CH_DAILY_PRIORITY else if (base == CH_PARTNER) CH_PARTNER_PRIORITY else return base
        return if (nm.getNotificationChannel(prio) != null) prio else base
    }

    fun openAppIntent(context: Context, destination: String, day: String? = null, fullScreen: Boolean = false, requestCode: Int = 0): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_DESTINATION, destination)
            day?.let { putExtra(EXTRA_DAY, it) }
            putExtra(EXTRA_FULL_SCREEN, fullScreen)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun base(context: Context, channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_miroir)
        .setColor(ContextCompat.getColor(context, R.color.miroir_primary))
        .setAutoCancel(true)

    @Suppress("MissingPermission")
    private fun post(context: Context, id: Int, n: Notification) {
        if (canNotify(context)) NotificationManagerCompat.from(context).notify(id, n)
    }

    fun showDaily(context: Context, prefs: AppPrefs, question: String, partnerAnswered: Boolean) {
        val title = if (partnerAnswered) "${prefs.partnerDisplayName} a déjà répondu ✨" else "Question du jour 💜"
        val n = base(context, channel(context, CH_DAILY, prefs))
            .setContentTitle(title)
            .setContentText(question)
            .setStyle(NotificationCompat.BigTextStyle().bigText(question))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context, "today", requestCode = ID_DAILY))
            .addAction(0, "Répondre", openAppIntent(context, "today", requestCode = ID_DAILY + 100))
            .build()
        post(context, ID_DAILY, n)
    }

    /** Relance du soir : remplace la notification du jour si elle est encore là. */
    fun showEvening(context: Context, prefs: AppPrefs, question: String, partnerAnswered: Boolean) {
        val text = if (partnerAnswered) "${prefs.partnerDisplayName} a répondu : ta réponse débloquera le miroir." else question
        val n = base(context, channel(context, CH_DAILY, prefs))
            .setContentTitle("Encore un peu de temps avant minuit ⏳")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppIntent(context, "today", requestCode = ID_DAILY))
            .addAction(0, "Répondre", openAppIntent(context, "today", requestCode = ID_DAILY + 100))
            .build()
        post(context, ID_DAILY, n)
    }

    fun onSyncEvents(context: Context, events: List<SyncEvent>, prefs: AppPrefs) {
        if (appInForeground()) return
        val name = prefs.partnerDisplayName
        val revealedDays = events.filterIsInstance<SyncEvent.Revealed>().map { it.day }.toSet()
        for (e in events) when (e) {
            is SyncEvent.Paired -> post(
                context, ID_PAIRING,
                base(context, CH_PARTNER).setContentTitle("Vous êtes reliés 💜").setContentText("$name a rejoint votre miroir.")
                    .setContentIntent(openAppIntent(context, "today", requestCode = ID_PAIRING)).build(),
            )
            is SyncEvent.PartnerAnswered -> if (prefs.notifyPartner && e.day !in revealedDays && !e.iAnswered) post(
                context, ID_PARTNER,
                base(context, channel(context, CH_PARTNER, prefs))
                    .setContentTitle("$name a répondu ✨")
                    .setContentText("À toi ! Ta réponse débloquera la révélation.")
                    .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                    .setContentIntent(openAppIntent(context, "today", e.day, requestCode = ID_PARTNER)).build(),
            )
            is SyncEvent.Revealed -> if (prefs.notifyPartner) {
                val builder = base(context, CH_REVEAL)
                    .setContentTitle("Révélé 💜")
                    .setContentText("$name a répondu : vos deux réponses vous attendent.")
                    .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(openAppIntent(context, "today", e.day, requestCode = ID_REVEAL))
                if (prefs.fullScreenReveal && canUseFullScreen(context)) {
                    builder.setFullScreenIntent(openAppIntent(context, "today", e.day, fullScreen = true, requestCode = ID_REVEAL + 100), true)
                }
                post(context, ID_REVEAL, builder.build())
            }
            is SyncEvent.Reaction -> if (prefs.notifyPartner) post(
                context, ID_REACTION,
                base(context, CH_PARTNER)
                    .setContentTitle("$name a réagi ${e.emoji.orEmpty()}".trim())
                    .setContentText(e.comment ?: "à vos réponses du jour")
                    .setStyle(e.comment?.let { NotificationCompat.BigTextStyle().bigText(it) })
                    .setContentIntent(openAppIntent(context, "day", e.day, requestCode = ID_REACTION)).build(),
            )
            is SyncEvent.CustomQuestionAdded -> if (prefs.notifyPartner) post(
                context, ID_CUSTOM,
                base(context, CH_PARTNER).setContentTitle("$name a ajouté une question ✍️")
                    .setContentText("Elle rejoindra bientôt votre tirage.")
                    .setContentIntent(openAppIntent(context, "custom", requestCode = ID_CUSTOM)).build(),
            )
            is SyncEvent.QuestionChanged -> post(
                context, ID_PARTNER,
                base(context, CH_PARTNER).setContentTitle("La question du jour a changé")
                    .setContentText("Elle a été passée avant l'envoi de ta réponse : réponds à la nouvelle !")
                    .setContentIntent(openAppIntent(context, "today", requestCode = ID_PARTNER + 200)).build(),
            )
        }
    }

    fun showNudge(context: Context, prefs: AppPrefs) {
        if (!prefs.notifyPartner) return
        post(
            context, ID_NUDGE,
            base(context, channel(context, CH_PARTNER, prefs))
                .setContentTitle("${prefs.partnerDisplayName} pense à toi 💜")
                .setContentText("Ta réponse débloquera le miroir du jour.")
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setContentIntent(openAppIntent(context, "today", requestCode = ID_NUDGE)).build(),
        )
    }

    fun showUnpaired(context: Context) = post(
        context, ID_PAIRING,
        base(context, CH_PARTNER).setContentTitle("Miroir dissocié")
            .setContentText("L'autre téléphone a dissocié votre miroir.")
            .setContentIntent(openAppIntent(context, "today", requestCode = ID_PAIRING + 100)).build(),
    )

    fun showUpdate(context: Context, version: String) = post(
        context, ID_UPDATE,
        base(context, CH_UPDATES).setContentTitle("Mise à jour disponible")
            .setContentText("Miroir $version est prête à être installée.")
            .setContentIntent(openAppIntent(context, "update", requestCode = ID_UPDATE)).build(),
    )

    fun syncNotification(context: Context): Notification =
        base(context, CH_SYNC).setContentTitle("Synchronisation…").setSilent(true).setOngoing(true).build()

    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
}
