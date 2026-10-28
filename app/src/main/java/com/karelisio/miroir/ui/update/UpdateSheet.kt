package com.karelisio.miroir.ui.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.miroir.permissions.AppPermission
import com.karelisio.miroir.permissions.SpecialPermissionLauncher
import com.karelisio.miroir.ui.components.AnimatedCounter
import com.karelisio.miroir.ui.components.MorphingBlob
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.settings.findActivity
import com.karelisio.miroir.update.UpdateInfo
import com.karelisio.miroir.update.UpdateManager
import com.karelisio.miroir.update.UpdateState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Bottom sheet de mise à jour : changelog, téléchargement animé, puis installation. */
@Composable
fun UpdateSheet(updates: UpdateManager) {
    val state by updates.state.collectAsStateWithLifecycle()
    val info = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.NeedsPermission -> s.info
        is UpdateState.Installing -> s.info
        is UpdateState.Failed -> s.info
        else -> null
    } ?: return

    val sheet = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val busy = state is UpdateState.Downloading

    // Retour des réglages "installer des applis inconnues" : on reprend l'installation automatiquement.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val s = updates.state.value
        if (s is UpdateState.NeedsPermission && updates.canInstall()) scope.launch { updates.installOrAskPermission(s.info, s.apk) }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) updates.dismiss() },
        sheetState = sheet,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                MorphingBlob(Modifier.size(96.dp), MaterialTheme.colorScheme.primaryContainer, periodMs = 5000)
                Icon(
                    if (state is UpdateState.NeedsPermission) Icons.Rounded.InstallMobile else Icons.Rounded.SystemUpdate,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                )
            }
            AnimatedContent(
                targetState = state::class,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "update",
            ) { _ ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (val s = state) {
                        is UpdateState.NeedsPermission -> PermissionExplainer(
                            onOpen = {
                                haptics.press()
                                context.findActivity()?.let { SpecialPermissionLauncher.launch(it, AppPermission.INSTALL_APPS) }
                            },
                            onLater = { updates.dismiss() },
                        )
                        is UpdateState.Downloading -> DownloadProgress(s.progress, info)
                        is UpdateState.Installing -> {
                            LoadingIndicator(Modifier.size(64.dp))
                            Text("Installation de Miroir ${info.version}…", style = MaterialTheme.typography.titleLarge)
                            Text("Confirme l'installation si Android te le demande.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> Available(
                            info = info,
                            error = (s as? UpdateState.Failed)?.message,
                            onUpdate = { haptics.confirm(); scope.launch { updates.downloadAndInstall(info) } },
                            onIgnore = { haptics.press(); scope.launch { sheet.hide(); updates.ignore(info) } },
                            onLater = { scope.launch { sheet.hide(); updates.dismiss() } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Available(info: UpdateInfo, error: String?, onUpdate: () -> Unit, onIgnore: () -> Unit, onLater: () -> Unit) {
    Text("Miroir ${info.version} est disponible", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    if (info.changelog.isNotBlank()) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Changelog(info.changelog)
            }
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
    Button(onClick = onUpdate, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(if (error != null) "Réessayer" else "Mettre à jour", style = MaterialTheme.typography.titleMedium)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onIgnore) { Text("Ignorer cette version") }
        TextButton(onClick = onLater) { Text("Plus tard") }
    }
}

/** Rendu minimal du Markdown des releases GitHub : titres, puces, texte. */
@Composable
private fun Changelog(markdown: String) {
    markdown.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.take(60).forEach { line ->
        when {
            line.startsWith("#") -> Text(line.trimStart('#', ' '), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") ->
                Row { Text("•  ", color = MaterialTheme.colorScheme.primary); Text(line.trimStart().drop(2).replace("**", ""), style = MaterialTheme.typography.bodyMedium) }
            else -> Text(line.replace("**", ""), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DownloadProgress(progress: Float, info: UpdateInfo) {
    val animated by animateFloatAsState(progress, spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow), label = "dl")
    Text("Téléchargement de Miroir ${info.version}", style = MaterialTheme.typography.titleLarge)
    Row(verticalAlignment = Alignment.Bottom) {
        AnimatedCounter((animated * 100).roundToInt(), MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Text(" %", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
    }
    LinearWavyProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth().height(14.dp))
    val mb = info.apkSize / 1_048_576f
    if (mb > 0) Text("%.1f Mo".format(mb * animated) + " / " + "%.1f Mo".format(mb), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PermissionExplainer(onOpen: () -> Unit, onLater: () -> Unit) {
    Text("Une dernière autorisation", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Pour installer la mise à jour, Android doit autoriser Miroir à installer des applications. Active « Autoriser depuis cette source », puis reviens : l'installation reprendra toute seule.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onOpen, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("Ouvrir les réglages", style = MaterialTheme.typography.titleMedium)
    }
    OutlinedButton(onClick = onLater, modifier = Modifier.fillMaxWidth().graphicsLayer { }) { Text("Plus tard") }
    Spacer(Modifier.width(1.dp))
}
