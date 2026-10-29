package com.karelisio.miroir.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.karelisio.miroir.container
import com.karelisio.miroir.data.remote.ApiException
import com.karelisio.miroir.diagnostics.Diagnostics
import com.karelisio.miroir.notifications.Notifier
import java.io.IOException

/** Envoie la file hors ligne (réponses, réactions, questions perso, réglages), puis synchronise. */
class SendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = applicationContext.container.repository
        if (!repo.flushPending()) {
            Diagnostics.record("envoi", "file hors ligne non vidée, nouvel essai plus tard")
            return Result.retry()
        }
        return try {
            repo.sync()
            Result.success()
        } catch (e: ApiException) {
            Diagnostics.record("synchro", "erreur serveur ${e.status}", e)
            if (e.status >= 500) Result.retry() else Result.success()
        } catch (e: IOException) {
            Diagnostics.record("synchro", "réseau indisponible (${e.javaClass.simpleName})")
            Result.retry()
        } catch (e: Exception) {
            Diagnostics.record("synchro", "erreur inattendue", e)
            throw e
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = syncForegroundInfo(applicationContext)
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = applicationContext.container.repository
        return try {
            repo.flushPending()
            repo.sync()
            Result.success()
        } catch (e: ApiException) {
            Diagnostics.record("synchro", "erreur serveur ${e.status}", e)
            if (e.status >= 500) Result.retry() else Result.success()
        } catch (e: IOException) {
            Diagnostics.record("synchro", "réseau indisponible (${e.javaClass.simpleName})")
            Result.retry()
        } catch (e: Exception) {
            Diagnostics.record("synchro", "erreur inattendue", e)
            throw e
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = syncForegroundInfo(applicationContext)
}

/** Requis pour le travail "expedited" avant Android 12. */
internal fun syncForegroundInfo(context: Context): ForegroundInfo {
    val notification = Notifier.syncNotification(context)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(Notifier.ID_SYNC, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(Notifier.ID_SYNC, notification)
    }
}
