package com.karelisio.miroir.games.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.karelisio.miroir.container
import com.karelisio.miroir.data.prefs.AppPrefs
import com.karelisio.miroir.games.data.GameSyncResult
import com.karelisio.miroir.notifications.Notifier

/**
 * Notifications de « De l'autre côté » : toujours neutres (« Nouveau défi », « C'est ton tour »),
 * sans jamais le texte d'une question ni d'une réponse, quelle que soit la catégorie.
 */
object GameNotifications {
    const val CH_GAMES = "games"
    private const val ID_GAME = 20

    fun channel() = NotificationChannel(CH_GAMES, "Jeux à deux", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Défis, tours de jeu et révélations (sans contenu)"
        lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
    }

    fun texts(event: String?): Pair<String, String> = when (event) {
        "turn" -> "C'est ton tour" to "Une partie t'attend dans Miroir."
        "reveal" -> "Révélation prête" to "Vos réponses vous attendent."
        "match" -> "Nouveau point commun" to "Ouvre Miroir pour le découvrir."
        "surprise" -> "Une surprise t'attend" to "Ouvre Miroir pour la découvrir."
        else -> "Nouveau défi" to "Un jeu t'attend dans Miroir."
    }

    fun show(context: Context, prefs: AppPrefs, event: String?, session: String?) {
        if (!prefs.notifyPartner || Notifier.isAppInForeground()) return
        val (title, text) = texts(event)
        val open = Notifier.openAppIntent(context, "games", requestCode = ID_GAME, session = session)
        val public = Notifier.base(context, CH_GAMES).setContentTitle(title).setContentIntent(open).build()
        val n = Notifier.base(context, CH_GAMES)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(open)
            .build()
        Notifier.post(context, ID_GAME, n)
    }

    /** Filet de sécurité sans push : un nouveau point commun trouvé par la synchro périodique. */
    suspend fun onSync(context: Context, result: GameSyncResult) {
        if (result.newMatches > 0) {
            val prefs = context.applicationContext.container.prefs.current()
            show(context, prefs, "match", null)
        }
    }
}
