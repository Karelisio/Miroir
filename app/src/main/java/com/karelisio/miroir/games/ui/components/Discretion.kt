package com.karelisio.miroir.games.ui.components

import android.app.Activity
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.miroir.security.AppLock
import com.karelisio.miroir.security.findActivity
import com.karelisio.miroir.ui.components.MorphingBlob

/**
 * FLAG_SECURE tant qu'un écran de la section est affiché : aperçu masqué dans les applis récentes,
 * captures et enregistrements bloqués. Compteur partagé : passer d'un écran de jeu à l'autre ne
 * retire jamais le drapeau entre les deux.
 */
object SecureWindow {
    private var count = 0

    fun acquire(activity: Activity) {
        if (count++ == 0) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(activity: Activity) {
        if (--count <= 0) {
            count = 0
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Composable
fun SecureScreen() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        SecureWindow.acquire(activity)
        onDispose { SecureWindow.release(activity) }
    }
}

/** Déverrouillage des catégories Osé et Intime : valable un quart d'heure. */
object SpicyUnlock {
    private const val VALIDITY_MS = 15 * 60_000L
    @Volatile private var until = 0L

    val unlocked: Boolean get() = SystemClock.elapsedRealtime() < until

    fun grant() { until = SystemClock.elapsedRealtime() + VALIDITY_MS }
    fun lock() { until = 0L }
}

/**
 * Si [required] (contenu Osé ou Intime et verrou activé), demande l'empreinte ou le code de
 * l'appareil avant d'afficher [content].
 */
@Composable
fun SpicyGate(required: Boolean, content: @Composable () -> Unit) {
    var open by remember(required) { mutableStateOf(!required || SpicyUnlock.unlocked) }
    if (open) {
        content()
        return
    }
    val activity = LocalContext.current.findActivity()
    val ask = {
        if (activity == null || !AppLock.available(activity)) {
            // Pas de verrouillage d'écran sur l'appareil : rien à vérifier.
            SpicyUnlock.grant(); open = true
        } else {
            AppLock.authenticate(activity, "Contenu 🌶 protégé") { ok -> if (ok) { SpicyUnlock.grant(); open = true } }
        }
    }
    LaunchedEffect(Unit) { ask() }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            MorphingBlob(Modifier.fillMaxSize(), colors.primaryContainer, shapes = listOf(MaterialShapes.Heart, MaterialShapes.Cookie9Sided), periodMs = 4200)
            Icon(Icons.Rounded.Fingerprint, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(60.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Contenu 🌶 protégé", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Les catégories Osé et Intime demandent ton empreinte ou ton code.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = ask, shapes = ButtonDefaults.shapes()) { Text("Déverrouiller") }
    }
}
