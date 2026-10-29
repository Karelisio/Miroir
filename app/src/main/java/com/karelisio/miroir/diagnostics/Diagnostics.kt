package com.karelisio.miroir.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.karelisio.miroir.BuildConfig
import com.karelisio.miroir.container
import com.karelisio.miroir.notifications.DailyReminder
import com.karelisio.miroir.security.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Journal technique local : plantages et erreurs avalées (synchro, mises à jour, déchiffrement…).
 * Ne contient ni réponses, ni prénoms, ni clés ; il ne quitte le téléphone que partagé par l'utilisateur.
 */
object Diagnostics {
    private const val MAX_BYTES = 192 * 1024
    private const val KEEP_BYTES = 96 * 1024
    private const val REPORT_CHARS = 60_000
    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.FRENCH)
    private val lock = Any()

    @Volatile private var log: File? = null
    @Volatile private var crashFlag: File? = null

    /** À appeler en premier dans Application.onCreate : capte aussi les plantages. */
    fun install(context: Context) {
        val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        useDir(dir)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                write("PLANTAGE", "fil « ${thread.name} »", e, fullStack = true)
                crashFlag?.writeText(System.currentTimeMillis().toString())
            }
            previous?.uncaughtException(thread, e)
        }
    }

    internal fun useDir(dir: File) {
        log = File(dir, "journal.txt")
        crashFlag = File(dir, "crash.flag")
    }

    /** Erreur non fatale : une ligne, et le début de la pile si une exception est fournie. */
    fun record(tag: String, message: String, error: Throwable? = null) {
        runCatching { write(tag, message, error, fullStack = false) }
    }

    private fun write(tag: String, message: String, error: Throwable?, fullStack: Boolean) {
        val file = log ?: return
        val entry = buildString {
            append(LocalDateTime.now().format(time)).append(" [").append(tag).append("] ").append(message)
            if (error != null) {
                append(" : ").append(error.javaClass.simpleName)
                error.message?.let { append(" – ").append(it.take(300)) }
                val stack = error.stackTraceToString().lines().drop(1)
                append('\n').append((if (fullStack) stack else stack.take(8)).joinToString("\n"))
            }
            append('\n')
        }
        synchronized(lock) {
            file.appendText(entry)
            if (file.length() > MAX_BYTES) file.writeText(file.readText().takeLast(KEEP_BYTES).substringAfter('\n'))
        }
    }

    fun journal(): String = synchronized(lock) { log?.takeIf { it.exists() }?.readText().orEmpty() }

    /** Nombre d'événements enregistrés (lignes horodatées). */
    fun entryCount(): Int = journal().lineSequence().count { it.length > 20 && it[0].isDigit() && it[4] == '-' }

    /** Vrai une seule fois après un plantage (pour proposer de le signaler au lancement suivant). */
    fun consumeCrash(): Boolean = crashFlag?.let { it.exists() && it.delete() } == true

    fun clear() = synchronized(lock) { log?.delete(); Unit }

    /** Rapport à partager : contexte technique, sans aucune donnée du couple. */
    suspend fun report(context: Context): String = withContext(Dispatchers.IO) {
        val c = context.applicationContext.container
        val p = c.prefs.current()
        val pending = runCatching { c.repository.dao.pendingAnswers().size + c.repository.dao.pendingReactions().size }.getOrDefault(-1)
        val (_, tokenSent) = runCatching { c.prefs.fcmToken() }.getOrDefault(null to false)
        fun yes(b: Boolean) = if (b) "oui" else "non"
        val journal = journal()
        buildString {
            appendLine("Rapport Miroir")
            appendLine("(aucune réponse, aucun prénom ni aucune clé n'est inclus)")
            appendLine()
            appendLine("Version : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
            appendLine("Android : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Date : ${LocalDateTime.now().format(time)} · fuseau ${java.time.ZoneId.systemDefault()}")
            appendLine("Appairé : ${yes(p.paired)} · l'autre relié : ${yes(p.partnerJoined)} · révision ${p.rev}")
            appendLine("Envois en attente : $pending · jeton push envoyé : ${yes(tokenSent)} · push activé : ${yes(BuildConfig.FIREBASE_ENABLED)}")
            appendLine(
                "Notifications : ${yes(NotificationManagerCompat.from(context).areNotificationsEnabled())} · " +
                    "alarmes exactes : ${yes(DailyReminder.canScheduleExact(context))} · " +
                    "rappel : ${if (p.reminderEnabled) "%02d:%02d".format(p.reminderHour, p.reminderMinute) else "non"} · relance du soir : ${yes(p.eveningReminder)}",
            )
            appendLine("Verrouillage : ${yes(p.appLock)} (disponible : ${yes(AppLock.available(context))}) · plein écran : ${yes(p.fullScreenReveal)}")
            appendLine()
            appendLine("--- Journal (${entryCount()} événements) ---")
            append(if (journal.isBlank()) "(vide)" else journal.takeLast(REPORT_CHARS))
        }
    }

    /** Ouvre la feuille de partage Android avec le rapport. */
    suspend fun share(context: Context) {
        val text = report(context)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Rapport Miroir ${BuildConfig.VERSION_NAME}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Partager le rapport").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
