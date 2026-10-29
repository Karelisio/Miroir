package com.karelisio.miroir.games.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karelisio.miroir.games.model.GameKind
import com.karelisio.miroir.games.ui.DefiCard
import com.karelisio.miroir.games.ui.GamesUi
import com.karelisio.miroir.games.ui.SessionCard
import com.karelisio.miroir.games.ui.SessionStatus
import com.karelisio.miroir.games.ui.components.FlipCard
import com.karelisio.miroir.games.ui.components.ModeTile
import com.karelisio.miroir.games.ui.components.Peppers
import com.karelisio.miroir.games.ui.components.StatusChip
import com.karelisio.miroir.games.ui.components.gamesBackground
import com.karelisio.miroir.ui.components.AnimatedCounter
import com.karelisio.miroir.ui.components.MorphingBlob
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.staggeredAppear
import kotlinx.coroutines.launch

/** Actions de l'accueil (séparées pour un contenu sans état, testable en capture). */
class GamesActions(
    val refresh: () -> Unit = {},
    val openSession: (String) -> Unit = {},
    val openCanape: () -> Unit = {},
    val openEnvies: () -> Unit = {},
    val openDefis: () -> Unit = {},
    val openStats: () -> Unit = {},
    val openRevelations: () -> Unit = {},
    val openSettings: () -> Unit = {},
    val markDefi: (Boolean) -> Unit = {},
    /** Lance une partie à distance, renvoie son id (null si rien à jouer). */
    val challenge: suspend (GameKind, Int, Boolean, String?) -> String? = { _, _, _, _ -> null },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesHome(ui: GamesUi, refreshing: Boolean, contentPadding: PaddingValues, actions: GamesActions) {
    var sheet by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    Box(Modifier.fillMaxSize().gamesBackground()) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = actions.refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp,
                    top = contentPadding.calculateTopPadding() + 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "head") { Header(ui, actions, Modifier.staggeredAppear(0, "g-head")) }
                item(key = "score") { ScoreCard(ui, Modifier.staggeredAppear(1, "g-score"), actions.openStats) }
                if (!ui.partnerJoined) {
                    item(key = "pair") { Text("Reliez d'abord vos deux téléphones pour jouer à distance. Le mode canapé marche déjà.", style = MaterialTheme.typography.bodyMedium) }
                }
                if (ui.ongoing.isNotEmpty()) {
                    item(key = "ongoing-t") { Text("Parties en cours", style = MaterialTheme.typography.titleMedium) }
                    items(ui.ongoing, key = { it.id }) { card -> OngoingCard(card, Modifier.animateItem()) { actions.openSession(card.id) } }
                }
                ui.defi?.let { d -> item(key = "defi") { DefiHomeCard(d, Modifier.staggeredAppear(2, "g-defi"), actions.markDefi, actions.openDefis) } }
                item(key = "envies") {
                    ModeTile(
                        "💞", "Envies communes",
                        when {
                            ui.newMatches > 0 -> "${ui.newMatches} nouveau${if (ui.newMatches > 1) "x" else ""} point${if (ui.newMatches > 1) "s" else ""} commun${if (ui.newMatches > 1) "s" else ""} ✨"
                            ui.matches > 0 -> "${ui.matches} envie${if (ui.matches > 1) "s" else ""} en commun · les « non » restent secrets"
                            else -> "Oui, non, peut-être : seuls vos matchs sont révélés"
                        },
                        Modifier.fillMaxWidth().staggeredAppear(3, "g-envies"),
                        highlight = ui.newMatches > 0,
                        onClick = actions.openEnvies,
                    )
                }
                item(key = "modes-t") { Text("Jouer", style = MaterialTheme.typography.titleMedium) }
                item(key = "modes") {
                    val tiles = listOf(
                        Triple("🛋️", "Mode canapé", "Un téléphone qu'on se passe, même hors ligne") to actions.openCanape,
                        Triple("🔮", "Devine-moi", "L'un répond, l'autre devine") to { sheet = true },
                        Triple("⚔️", "Duel à distance", "Le même quiz, révélé ensemble") to { sheet = true },
                        Triple("⚖️", "Tu préfères", "A ou B, chacun de son côté") to { sheet = true },
                        Triple("🎲", "Action ou vérité", "En canapé ou à distance") to { sheet = true },
                        Triple("🎁", "Question surprise", "Écris une question pour ${ui.partnerName}") to { sheet = true },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        tiles.chunked(2).forEachIndexed { row, pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEachIndexed { i, (t, onClick) ->
                                    ModeTile(t.first, t.second, t.third, Modifier.weight(1f).staggeredAppear(4 + row * 2 + i, "g-m-${t.second}"), onClick = onClick)
                                }
                            }
                        }
                    }
                }
                item(key = "links") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinkChip("📌 Nos révélations (${ui.pins.size})", actions.openRevelations)
                        LinkChip("🏅 Défis de la semaine", actions.openDefis)
                        LinkChip("✍️ Questions perso (${ui.custom.size})", actions.openSettings)
                    }
                }
            }
        }
        if (ui.partnerJoined) {
            ExtendedFloatingActionButton(
                onClick = { haptics.press(); sheet = true },
                icon = { Icon(Icons.Rounded.Bolt, null) },
                text = { Text("Défier ${ui.partnerName}") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            )
        }
    }
    if (sheet) ChallengeSheet(ui, onDismiss = { sheet = false }, onLaunched = { id -> sheet = false; actions.openSession(id) }, challenge = actions.challenge)
}

@Composable
private fun Header(ui: GamesUi, actions: GamesActions, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("De l'autre côté", style = MaterialTheme.typography.displaySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Intensité commune ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Peppers(ui.intensity, size = 14.dp)
            }
        }
        IconButton(onClick = actions.openStats) { Icon(Icons.Rounded.Insights, "Stats et badges") }
        IconButton(onClick = actions.openSettings) { Icon(Icons.Rounded.Tune, "Réglages de jeu") }
    }
}

@Composable
private fun ScoreCard(ui: GamesUi, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val stats = ui.stats
    Surface(onClick = onClick, shape = MaterialTheme.shapes.extraLarge, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer, modifier = modifier.fillMaxWidth()) {
        Box {
            MorphingBlob(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).width(120.dp).height(120.dp), colors.primary.copy(alpha = 0.14f))
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedCounter(stats?.connivence ?: 0, MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold), color = colors.primary)
                        Text(" %", style = MaterialTheme.typography.headlineSmall, color = colors.primary, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Text(if (stats?.connivence == null) "de connivence (jouez pour la mesurer)" else "de connivence", style = MaterialTheme.typography.labelLarge)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("✨ ${stats?.points ?: 0} points", style = MaterialTheme.typography.titleSmall)
                    Text("🔥 ${stats?.streak ?: 0} j de jeu", style = MaterialTheme.typography.titleSmall)
                    Text("🏅 ${stats?.badges?.count { it.unlocked } ?: 0} badges", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun OngoingCard(card: SessionCard, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialShapes.Cookie9Sided.toShape(), color = colors.primary.copy(alpha = 0.14f)) {
                Text(card.kind.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.kind.label, style = MaterialTheme.typography.titleMedium)
                Text(card.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                StatusChip(card.label, emphasis = card.status == SessionStatus.YOUR_TURN || card.status == SessionStatus.REVEAL)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun DefiHomeCard(d: DefiCard, modifier: Modifier, onDone: (Boolean) -> Unit, onOpen: () -> Unit) {
    var flipped by rememberSaveable(d.week) { mutableStateOf(d.done) }
    val colors = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    FlipCard(flipped, modifier.fillMaxWidth(), back = {
        Surface(onClick = { haptics.press(); flipped = true }, shape = MaterialTheme.shapes.extraLarge, color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🏅", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Défi de la semaine", style = MaterialTheme.typography.titleMedium)
                    Text("Touche pour le retourner", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏅 Défi de la semaine", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Peppers(d.intensity, size = 13.dp)
                }
                Text(d.text, style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = d.done, onClick = { haptics.confirm(); onDone(!d.done) }, label = { Text(if (d.done) "Relevé ✓" else "On l'a fait !") })
                    Spacer(Modifier.weight(1f))
                    Text("Historique", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(4.dp))
                    IconButton(onClick = onOpen) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Historique des défis") }
                }
            }
        }
    }
}

@Composable
private fun LinkChip(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

/** « Défier l'autre » : choix du mode à distance et de ses options, puis envoi (notification neutre). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChallengeSheet(ui: GamesUi, onDismiss: () -> Unit, onLaunched: (String) -> Unit, challenge: suspend (GameKind, Int, Boolean, String?) -> String?) {
    val state = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf(GameKind.DUEL) }
    var count by rememberSaveable { mutableIntStateOf(7) }
    var iAnswer by rememberSaveable { mutableStateOf(true) }
    var question by rememberSaveable { mutableStateOf("") }
    var sending by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Défier ${ui.partnerName}", style = MaterialTheme.typography.headlineSmall)
            Text("${ui.partnerName} reçoit seulement « Nouveau défi », jamais le contenu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val modes = listOf(GameKind.DEVINE, GameKind.DUEL, GameKind.PREFERES, GameKind.ACTION_VERITE, GameKind.SURPRISE)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { k ->
                    FilterChip(selected = kind == k, onClick = {
                        kind = k
                        count = when (k) { GameKind.DEVINE -> 5; GameKind.DUEL -> 7; GameKind.PREFERES -> 10; GameKind.ACTION_VERITE -> 6; else -> 1 }
                    }, label = { Text("${k.emoji} ${k.label}") })
                }
            }
            AnimatedVisibility(kind == GameKind.DEVINE, enter = expandVertically() + fadeIn()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(iAnswer, onClick = { iAnswer = true }, label = { Text("Je réponds, ${ui.partnerName} devine") })
                    FilterChip(!iAnswer, onClick = { iAnswer = false }, label = { Text("${ui.partnerName} répond") })
                }
            }
            if (kind != GameKind.SURPRISE) {
                val choices = when (kind) {
                    GameKind.DUEL -> listOf(5, 7, 10)
                    GameKind.ACTION_VERITE -> listOf(4, 6, 10)
                    GameKind.PREFERES -> listOf(5, 10, 15)
                    else -> listOf(3, 5, 8)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (kind == GameKind.ACTION_VERITE) "Tours" else "Questions", style = MaterialTheme.typography.titleSmall)
                    choices.forEach { n -> FilterChip(count == n, onClick = { count = n }, label = { Text("$n") }) }
                }
            } else {
                OutlinedTextField(
                    question, { if (it.length <= 200) question = it },
                    Modifier.fillMaxWidth(),
                    placeholder = { Text("Ta question pour ${ui.partnerName}…") },
                    shape = MaterialTheme.shapes.large,
                    minLines = 2,
                )
            }
            Button(
                onClick = {
                    sending = true
                    scope.launch {
                        val id = challenge(kind, count, iAnswer, question)
                        sending = false
                        if (id != null) onLaunched(id)
                    }
                },
                enabled = !sending && (kind != GameKind.SURPRISE || question.isNotBlank()),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Envoyer le défi", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
