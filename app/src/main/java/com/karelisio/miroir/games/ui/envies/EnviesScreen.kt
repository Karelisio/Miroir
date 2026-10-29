package com.karelisio.miroir.games.ui.envies

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.miroir.games.model.MatchKind
import com.karelisio.miroir.games.model.Wish
import com.karelisio.miroir.games.ui.components.CategoryPill
import com.karelisio.miroir.games.ui.components.Confetti
import com.karelisio.miroir.games.ui.components.GameFrame
import com.karelisio.miroir.games.ui.components.SpicyGate
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.staggeredAppear

@Composable
fun EnviesScreen(vm: EnviesViewModel, onBack: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    GameFrame("💞 Envies communes", onBack) { padding ->
        SpicyGate(required = ui.lockSpicy && ui.spicy) { EnviesContent(ui, padding, vm) }
    }
}

@Composable
fun EnviesContent(ui: EnviesUi, padding: PaddingValues, vm: EnviesViewModel?) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val fresh = ui.matches.count { it.fresh }
    LaunchedEffect(tab, fresh) { if (tab == 1 && fresh > 0) vm?.markSeen() }
    Column(Modifier.fillMaxSize().padding(padding)) {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Répondre en secret") })
            Tab(tab == 1, onClick = { tab = 1 }, text = { Text(if (fresh > 0) "Nos matchs · $fresh nouveau${if (fresh > 1) "x" else ""}" else "Nos matchs (${ui.matches.size})") })
        }
        if (tab == 0) Answer(ui, vm) else Matches(ui)
    }
}

@Composable
private fun Answer(ui: EnviesUi, vm: EnviesViewModel?) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(ui.category == null, onClick = { vm?.setCategory(null) }, label = { Text("Toutes") }) }
            items(ui.categories) { c -> FilterChip(ui.category == c, onClick = { vm?.setCategory(c) }, label = { Text("${c.emoji} ${c.label}") }) }
        }
        val done = ui.total - ui.queue.size
        Text("$done sur ${ui.total} · tes « non » restent toujours secrets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { if (ui.total == 0) 0f else done.toFloat() / ui.total }, modifier = Modifier.fillMaxWidth())
        val current = ui.queue.firstOrNull()
        AnimatedContent(
            targetState = current,
            contentKey = { it?.id },
            transitionSpec = { (scaleIn(initialScale = 0.9f) + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut()) },
            label = "envie",
        ) { item ->
            if (item == null) {
                Text(
                    "Tout est répondu ici ✨\nLes nouvelles envies communes apparaîtront dans « Nos matchs ».",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
                return@AnimatedContent
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CategoryPill(item)
                        Text(item.render(ui.partnerName), style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Button(onClick = { haptics.confirm(); vm?.answer(item, Wish.YES) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Oui, carrément", style = MaterialTheme.typography.titleMedium) }
                FilledTonalButton(onClick = { haptics.tick(); vm?.answer(item, Wish.MAYBE) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Peut-être, à discuter") }
                OutlinedButton(onClick = { haptics.tick(); vm?.answer(item, Wish.NO) }, modifier = Modifier.fillMaxWidth()) { Text("Non (jamais montré)") }
            }
        }
    }
}

@Composable
private fun Matches(ui: EnviesUi) {
    val fresh = ui.matches.firstOrNull { it.fresh }?.item?.id
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "Seuls vos points communs apparaissent ici : oui + oui, ou oui + peut-être. Un « non » n'est jamais montré à ${ui.partnerName}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ui.matches.isEmpty()) item {
                Text("Pas encore de point commun. Répondez chacun de votre côté 💞", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            }
            listOf(MatchKind.MATCH to "Envies communes 💞", MatchKind.DISCUSS to "À discuter 💬").forEach { (kind, title) ->
                val list = ui.matches.filter { it.kind == kind }
                if (list.isNotEmpty()) {
                    item { Text("$title · ${list.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                    items(list, key = { it.item.id }) { m ->
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (kind == MatchKind.MATCH) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth().animateItem().staggeredAppear(list.indexOf(m), "m-${m.item.id}"),
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CategoryPill(m.item, Modifier.weight(1f))
                                    if (m.fresh) Text("Nouveau ✨", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(m.item.render(ui.partnerName), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
        Confetti(fresh)
    }
}
