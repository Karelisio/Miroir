package com.karelisio.miroir.sync

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.karelisio.miroir.container
import com.karelisio.miroir.notifications.Notifier
import kotlinx.coroutines.runBlocking

/**
 * Les messages FCM ne contiennent aucune donnée sensible : juste un type et un jour.
 * Ils déclenchent une synchronisation, qui détecte les changements et notifie.
 */
class MiroirMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val c = applicationContext.container
        when (message.data["type"]) {
            "unpaired" -> runBlocking {
                // Un vieux push d'une paire précédente ne doit pas effacer la paire actuelle.
                if (message.data["pairId"] == c.repository.currentPairId()) {
                    c.repository.forgetPairing()
                    Notifier.showUnpaired(applicationContext)
                }
            }
            else -> SyncScheduler.enqueueSync(applicationContext)
        }
    }

    override fun onRegistered(token: String) = saveToken(token)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) = saveToken(token)

    private fun saveToken(token: String) {
        runBlocking {
            applicationContext.container.prefs.update {
                fcmToken(token)
                fcmTokenSent = false
            }
        }
        SyncScheduler.enqueueSend(applicationContext)
    }
}
