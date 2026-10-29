package com.karelisio.miroir.games.ui.extras

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.miroir.games.model.CustomGameItem
import com.karelisio.miroir.games.model.GameCategory
import com.karelisio.miroir.games.model.ItemFormat
import com.karelisio.miroir.games.model.ItemKind
import com.karelisio.miroir.games.ui.CategoryScore
import com.karelisio.miroir.games.ui.GamesUi
import com.karelisio.miroir.games.ui.components.FlipCard
import com.karelisio.miroir.games.ui.components.INTENSITY_LABELS
import com.karelisio.miroir.games.ui.components.IntensityPicker
import com.karelisio.miroir.games.ui.components.Peppers
import com.karelisio.miroir.games.ui.theme.LocalReduceMotion
import com.karelisio.miroir.ui.components.AnimatedCounter
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.staggeredAppear
import java.time.format.DateTimeFormatter
import java.util.Locale

private val pinDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

// ---------------------------------------------------------------- Défis de la semaine

@Composable
fun DefisContent(ui: GamesUi, padding: PaddingValues, onDone: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Un défi commun tiré chaque lundi, à relever ensemble avant dimanche.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ui.defi?.let { d ->
            item {
                var flipped by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) { flipped = true }
                FlipCard(flipped) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Cette semaine · ${d.week}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                Peppers(d.intensity, size = 14.dp)
                            }
                            Text(d.text, style = MaterialTheme.typography.headlineSmall)
                            Button(onClick = { haptics.confirm(); onDone(!d.done) }, shapes = ButtonDefaults.shapes()) {
                                Text(if (d.done) "Relevé ✓ (annuler)" else "On l'a fait !")
                            }
                        }
                    }
                }
            }
        }
        val past = ui.defis.filter { it.week != ui.defi?.week }
        if (past.isNotEmpty()) item { Text("Historique", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        items(past, key = { it.sessionId }) { d ->
            ListItem(
                supportingContent = { Text(d.week) },
                trailingContent = { Text(if (d.done) "✓" else "—", style = MaterialTheme.typography.titleLarge) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.animateItem(),
            ) { Text(d.text) }
        }
    }
}

// ---------------------------------------------------------------- Stats et badges

@Composable
fun StatsContent(ui: GamesUi, padding: PaddingValues, onRevelations: () -> Unit) {
    val stats = ui.stats
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().staggeredAppear(0, "st-0")) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedCounter(stats?.connivence ?: 0, MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        Text(" %", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Text("de connivence", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat("✨", stats?.points ?: 0, "points")
                        Stat("🎲", stats?.played ?: 0, "parties")
                        Stat("🔥", stats?.streak ?: 0, "jours")
                        Stat("🏆", stats?.bestStreak ?: 0, "record")
                    }
                }
            }
        }
        if (!stats?.byCategory.isNullOrEmpty()) item { Bars("Pronostics justes par catégorie", stats.byCategory, Modifier.staggeredAppear(1, "st-1")) }
        if (!stats?.agreementByCategory.isNullOrEmpty()) item { Bars("Accords « Tu préfères »", stats.agreementByCategory, Modifier.staggeredAppear(2, "st-2")) }
        item {
            Surface(onClick = onRevelations, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("📌", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Nos révélations", style = MaterialTheme.typography.titleMedium)
                        Text("${ui.pins.size} réponse${if (ui.pins.size > 1) "s" else ""} épinglée${if (ui.pins.size > 1) "s" else ""} · ${stats?.matches ?: 0} envie${if ((stats?.matches ?: 0) > 1) "s" else ""} en commun", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Text("Badges · ${stats?.badges?.count { it.unlocked } ?: 0} sur ${stats?.badges?.size ?: 0}", style = MaterialTheme.typography.titleMedium) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stats?.badges?.forEachIndexed { i, b ->
                    val colors = MaterialTheme.colorScheme
                    Surface(
                        shape = if (b.unlocked) MaterialShapes.Cookie9Sided.toShape() else MaterialTheme.shapes.large,
                        color = if (b.unlocked) colors.tertiaryContainer else colors.surfaceContainerHigh,
                        modifier = Modifier.size(104.dp).staggeredAppear(i, "b-${b.id}").graphicsLayer { alpha = if (b.unlocked) 1f else 0.55f },
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(if (b.unlocked) b.emoji else "🔒", style = MaterialTheme.typography.headlineSmall)
                            Text(b.title, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
            }
        }
        item {
            Text(
                stats?.badges?.filter { !it.unlocked }?.take(3)?.joinToString("\n") { "🔒 ${it.title} : ${it.description}" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(emoji: String, value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji)
        AnimatedCounter(value, MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Graphe en barres : chaque barre pousse en ressort, en cascade. */
@Composable
private fun Bars(title: String, scores: List<CategoryScore>, modifier: Modifier) {
    val reduce = LocalReduceMotion.current
    var shown by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { shown = true }
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                scores.forEachIndexed { i, s ->
                    val h by animateFloatAsState(
                        if (shown) s.percent / 100f else 0f,
                        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow - i * 12f),
                        label = "bar$i",
                    )
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${s.percent}%", style = MaterialTheme.typography.labelSmall)
                        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                            val barH = size.height * h.coerceIn(0f, 1.1f)
                            drawRoundRect(colors.surfaceContainerHighest, Offset.Zero, size, CornerRadius(12f, 12f))
                            drawRoundRect(if (s.category.spicy) colors.tertiary else colors.primary, Offset(0f, size.height - barH), Size(size.width, barH), CornerRadius(12f, 12f))
                        }
                        Text(s.category.emoji, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Nos révélations

@Composable
fun RevelationsContent(ui: GamesUi, padding: PaddingValues, onUnpin: (com.karelisio.miroir.games.data.GamePinEntity) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ui.pins.isEmpty()) item {
            Text("Épingle 📌 une réponse de ${ui.partnerName} pendant une révélation pour la retrouver ici.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        }
        items(ui.pins, key = { it.sessionId + it.item }) { p ->
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().animateItem()) {
                Row(Modifier.padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.question, style = MaterialTheme.typography.labelLarge)
                        Text("« ${p.answer} »", style = MaterialTheme.typography.titleLarge)
                        Text(java.time.Instant.ofEpochMilli(p.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(pinDate), style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { onUnpin(p) }) { Icon(Icons.Rounded.Close, "Retirer") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Réglages de jeu et questions perso

class GameSettingsActions(
    val setIntensity: (Int) -> Unit = {},
    val toggleCategory: (String) -> Unit = {},
    val setLock: (Boolean) -> Unit = {},
    val setTimer: (Int) -> Unit = {},
    val addCustom: (CustomGameItem) -> Unit = {},
    val deleteCustom: (String) -> Unit = {},
)

@Composable
fun GameSettingsContent(ui: GamesUi, padding: PaddingValues, actions: GameSettingsActions) {
    val haptics = rememberHaptics()
    var adding by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Mon curseur de piments", style = MaterialTheme.typography.titleMedium) }
        item { IntensityPicker(ui.myIntensity, actions.setIntensity) }
        item {
            Text(
                "Intensité commune : ${INTENSITY_LABELS[ui.intensity - 1]} " + "🌶".repeat(ui.intensity) +
                    (ui.partnerIntensity?.let { " · ${ui.partnerName} est sur ${INTENSITY_LABELS[it - 1]}" } ?: " · en attente du réglage de ${ui.partnerName}"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Text("Catégories", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GameCategory.entries.forEach { c ->
                    val mine = c.key !in ui.excluded
                    val blockedByPartner = c.key in ui.partnerExcluded
                    FilterChip(
                        selected = mine && !blockedByPartner,
                        onClick = { haptics.tick(); actions.toggleCategory(c.key) },
                        label = { Text("${c.emoji} ${c.label}" + if (blockedByPartner) " · off pour ${ui.partnerName}" else "") },
                    )
                }
            }
        }
        item { Text("Une catégorie désactivée par l'un de vous n'apparaît pour personne.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            ListItem(
                supportingContent = { Text("Empreinte ou code du téléphone avant ces contenus") },
                trailingContent = { Switch(ui.lockSpicy, { haptics.toggle(it); actions.setLock(it) }) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) { Text("Verrouiller Osé et Intime") }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Chrono du mode canapé", style = MaterialTheme.typography.titleSmall)
                listOf(0 to "Sans", 30 to "30 s", 60 to "1 min").forEach { (s, l) -> FilterChip(ui.timer == s, onClick = { actions.setTimer(s) }, label = { Text(l) }) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Questions perso · ${ui.custom.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Ajouter") }
            }
        }
        items(ui.custom, key = { it.id }) { q ->
            ListItem(
                supportingContent = { Text("${q.category.emoji} ${q.category.label} · ${kindLabel(q.kind)}" + if (q.author != null && q.author != ui.me) " · de ${ui.partnerName}" else "") },
                leadingContent = { Peppers(q.intensity, size = 12.dp, animated = false) },
                trailingContent = { if (q.author == ui.me) IconButton(onClick = { actions.deleteCustom(q.id) }) { Icon(Icons.Rounded.Delete, "Supprimer") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.animateItem(),
            ) { Text(q.text) }
        }
        item { Text("Chiffrées de bout en bout, partagées avec ${ui.partnerName} et ajoutées au tirage.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (adding) CustomDialog(onDismiss = { adding = false }) { actions.addCustom(it); adding = false }
}

private fun kindLabel(k: ItemKind) = when (k) {
    ItemKind.DEVINE -> "Devine-moi"
    ItemKind.PREFERES -> "Tu préfères"
    ItemKind.VERITE -> "Vérité"
    ItemKind.ACTION -> "Action"
    ItemKind.ENVIE -> "Envie"
    ItemKind.DEFI -> "Défi"
}

@Composable
private fun CustomDialog(onDismiss: () -> Unit, onSave: (CustomGameItem) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(ItemKind.DEVINE) }
    var category by rememberSaveable { mutableStateOf(GameCategory.NOUS_DEUX) }
    var intensity by rememberSaveable { mutableIntStateOf(1) }
    var text by rememberSaveable { mutableStateOf("") }
    var a by rememberSaveable { mutableStateOf("") }
    var b by rememberSaveable { mutableStateOf("") }
    val needsOptions = kind == ItemKind.PREFERES
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle question perso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ItemKind.entries.forEach { k -> FilterChip(kind == k, onClick = { kind = k }, label = { Text(kindLabel(k)) }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GameCategory.entries.forEach { c -> FilterChip(category == c, onClick = { category = c; if (c == GameCategory.INTIME && intensity < 2) intensity = 2 }, label = { Text(c.emoji) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    (1..3).forEach { i -> FilterChip(intensity == i, onClick = { intensity = i }, label = { Text("🌶".repeat(i)) }) }
                }
                OutlinedTextField(text, { if (it.length <= 170) text = it }, Modifier.fillMaxWidth(), label = { Text(if (kind == ItemKind.ENVIE) "L'envie" else "La question ou le gage") }, minLines = 2)
                if (needsOptions) {
                    OutlinedTextField(a, { if (it.length <= 70) a = it }, Modifier.fillMaxWidth(), label = { Text("Option A") }, singleLine = true)
                    OutlinedTextField(b, { if (it.length <= 70) b = it }, Modifier.fillMaxWidth(), label = { Text("Option B") }, singleLine = true)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        CustomGameItem(
                            category = category.key, kind = kind.key, intensity = intensity, text = text.trim(),
                            format = if (kind == ItemKind.DEVINE) ItemFormat.TEXT.key else null,
                            options = if (needsOptions) listOf(a.trim(), b.trim()) else emptyList(),
                        ),
                    )
                },
                enabled = text.isNotBlank() && (!needsOptions || (a.isNotBlank() && b.isNotBlank())),
                shapes = ButtonDefaults.shapes(),
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
