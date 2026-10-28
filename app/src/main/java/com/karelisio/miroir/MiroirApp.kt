package com.karelisio.miroir

import android.app.Application
import android.content.Context
import com.karelisio.miroir.data.MiroirRepository
import com.karelisio.miroir.data.local.MiroirDatabase
import com.karelisio.miroir.data.prefs.Prefs
import com.karelisio.miroir.data.questions.QuestionBank
import com.karelisio.miroir.data.remote.MiroirApi
import com.karelisio.miroir.notifications.DailyReminder
import com.karelisio.miroir.notifications.Notifier
import com.karelisio.miroir.sync.SyncScheduler
import com.karelisio.miroir.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Conteneur de dépendances minimal (pas de framework d'injection). */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http = MiroirApi.client()
    val prefs = Prefs(context)
    val database = MiroirDatabase.create(context)
    val bank = QuestionBank(context)
    val api = MiroirApi(http)
    val repository = MiroirRepository(context, database.dao(), prefs, api, bank)
    val updates = UpdateManager(context, http, prefs)
}

open class MiroirApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        startBackgroundWork()
    }

    /** Canaux, travail périodique et rappel (surchargé par l'application de test). */
    protected open fun startBackgroundWork() {
        Notifier.createChannels(this)
        SyncScheduler.schedulePeriodic(this)
        // Replanifie le rappel à chaque changement d'heure ou d'appairage.
        container.scope.launch {
            container.prefs.data
                .map { listOf(it.reminderEnabled && it.paired, it.reminderHour, it.reminderMinute, it.eveningReminder) }
                .distinctUntilChanged()
                .collect { DailyReminder.schedule(this@MiroirApp, container.prefs.current()) }
        }
        container.scope.launch { container.bank.all() }
    }
}

val Context.container: AppContainer get() = (applicationContext as MiroirApp).container
