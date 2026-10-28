package com.karelisio.miroir.ui.history

import kotlin.math.roundToInt
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.ui.components.AnimatedCounter
import com.karelisio.miroir.ui.components.MorphingBlob
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.staggeredAppear
import com.karelisio.miroir.ui.navigation.sharedContainer
import com.karelisio.miroir.ui.navigation.sharedItem
import java.time.format.DateTimeFormatter
import java.util.Locale

private val itemDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.FRENCH)

@Composable
fun HistoryScreen(vm: HistoryViewModel, contentPadding: PaddingValues, onOpenDay: (String) -> Unit, onOpenRecap: () -> Unit = {}) {
    val ui by vm.state.collectAsStateWithLifecycle()
    HistoryContent(ui, contentPadding, vm::setQuery, vm::setCategory, onOpenDay, vm::setFavoritesOnly, onOpenRecap)
}

@Composable
fun HistoryContent(
    ui: HistoryUi,
    contentPadding: PaddingValues,
    onQuery: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onOpenDay: (String) -> Unit,
    onFavoritesOnly: (Boolean) -> Unit = {},
    onOpenRecap: () -> Unit = {},
) {
    val haptics = rememberHaptics()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Row(Modifier.padding(horizontal = 20.dp).staggeredAppear(0, "h-title"), verticalAlignment = Alignment.CenterVertically) {
                Text("Notre histoire", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                if (ui.together > 0) {
                    FilledTonalButton(onClick = { haptics.press(); onOpenRecap() }, shapes = ButtonDefaults.shapes()) { Text("✨ Récap") }
                }
            }
        }
        item(key = "stats") {
            Row(Modifier.padding(horizontal = 20.dp).staggeredAppear(1, "h-stats"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("à deux", ui.together, "💜", Modifier.weight(1f))
                StatTile("série", ui.streak, "🔥", Modifier.weight(1f))
                StatTile("record", ui.best, "🏆", Modifier.weight(1f))
            }
        }
        if (ui.agreements.isNotEmpty()) {
            item(key = "agreements") { AgreementsCard("Vos accords", ui.agreements, Modifier.padding(horizontal = 20.dp).staggeredAppear(2, "h-agree")) }
        }
        if (ui.guesses.isNotEmpty()) {
            item(key = "guesses") {
                AgreementsCard("On se connaît à ${ui.guesses.overall()} %", ui.guesses, Modifier.padding(horizontal = 20.dp).staggeredAppear(2, "h-guess"))
            }
        }
        item(key = "search") {
            TextField(
                value = ui.query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).staggeredAppear(2, "h-search"),
                placeholder = { Text("Rechercher dans vos réponses", maxLines = 1) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Effacer") }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
        }
        item(key = "filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.staggeredAppear(3, "h-filters"),
            ) {
                item {
                    FilterChip(selected = ui.category == null && !ui.favoritesOnly, onClick = { haptics.tick(); onCategory(null); onFavoritesOnly(false) }, label = { Text("Tout") })
                }
                item {
                    FilterChip(
                        selected = ui.favoritesOnly,
                        onClick = { haptics.tick(); onFavoritesOnly(!ui.favoritesOnly) },
                        label = { Text("⭐ Favoris") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    )
                }
                items(Category.entries, key = { it.key }) { c ->
                    val selected = ui.category == c.key
                    FilterChip(
                        selected = selected,
                        onClick = { haptics.tick(); onCategory(if (selected) null else c.key) },
                        label = { Text("${c.emoji} ${c.label}") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
        }
        if (ui.items.isEmpty()) {
            item(key = "empty") { EmptyHistory(ui.query.isNotEmpty() || ui.category != null) }
        }
        itemsIndexed(ui.items, key = { _, it -> it.day }) { index, item ->
            HistoryCard(
                item = item,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .animateItem()
                    .staggeredAppear(index + 4, item.day),
                onClick = { haptics.press(); onOpenDay(item.day) },
            )
        }
    }
}

/** Taux d'accord : barres qui se remplissent en ressort, pourcentage animé. */
@Composable
fun AgreementsCard(title: String, agreements: List<Agreement>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            agreements.forEach { a ->
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                val progress by animateFloatAsState(
                    if (shown) a.percent / 100f else 0f,
                    spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow),
                    label = "agree",
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${a.emoji}  ${a.label}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        AnimatedCounter((progress * 100).roundToInt(), MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.primary)
                        Text(" %", style = MaterialTheme.typography.titleMedium, color = colors.primary)
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        trackColor = colors.surfaceContainerHighest,
                        drawStopIndicator = {},
                    )
                    Text("sur ${a.total} question${if (a.total > 1) "s" else ""}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, emoji: String, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(emoji)
            AnimatedCounter(value, MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryCard(item: HistoryItem, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        modifier = modifier.fillMaxWidth().sharedContainer("day-${item.day}"),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = (if (item.revealed) MaterialShapes.Cookie9Sided else MaterialShapes.Circle).toShape(),
                    color = if (item.revealed) colors.primaryContainer else colors.surfaceContainerHighest,
                    modifier = Modifier.size(48.dp),
                ) {}
                Text(item.category.emoji)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.date.format(itemDate).replaceFirstChar { it.titlecase(Locale.FRENCH) } + if (item.favorite) "  ⭐" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                )
                Text(
                    item.question,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.sharedItem("q-${item.day}"),
                )
                Spacer(Modifier.height(4.dp))
                Text(item.status, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (item.reactions.isNotEmpty()) {
                Text(item.reactions.joinToString(""), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyHistory(filtered: Boolean) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            MorphingBlob(Modifier.size(140.dp).graphicsLayer { alpha = 0.3f }, MaterialTheme.colorScheme.primary)
            Text(if (filtered) "🔍" else "📖", style = MaterialTheme.typography.displaySmall)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (filtered) "Aucun souvenir ne correspond." else "Votre histoire commence aujourd'hui.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}
