package com.karelisio.miroir.games.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.miroir.games.ui.components.IntensityPicker
import com.karelisio.miroir.games.ui.components.gamesBackground
import com.karelisio.miroir.ui.components.MorphingBlob
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.staggeredAppear
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/** Présentation de la section à la première ouverture : les modes, le curseur de piments, la discrétion. */
@Composable
fun GamesOnboarding(contentPadding: PaddingValues, onDone: (intensity: Int, lock: Boolean) -> Unit) {
    val pager = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var intensity by rememberSaveable { mutableIntStateOf(1) }
    var lock by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().gamesBackground().padding(contentPadding)) {
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val offset = (pager.currentPage - page + pager.currentPageOffsetFraction).absoluteValue
            Column(
                Modifier.fillMaxSize().padding(28.dp).graphicsLayer { alpha = 1f - offset * 0.6f; scaleX = 1f - offset * 0.08f; scaleY = scaleX },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (page) {
                    0 -> Intro("🪞", "De l'autre côté", "Des jeux à deux, de l'autre côté du miroir. Plus joueurs, plus intimes, toujours chiffrés de bout en bout.")
                    1 -> {
                        Intro("🎲", "Sept façons de jouer", null)
                        Spacer(Modifier.height(12.dp))
                        listOf(
                            "🛋️ Mode canapé · un seul téléphone",
                            "🔮 Devine-moi · l'un répond, l'autre devine",
                            "⚔️ Duel à distance · révélé ensemble",
                            "⚖️ Tu préfères · accords et désaccords",
                            "💞 Envies communes · seuls les matchs",
                            "🎯 Action ou vérité · canapé ou distance",
                            "🏅 Défi de la semaine · chaque lundi",
                        ).forEachIndexed { i, line ->
                            Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).staggeredAppear(i, "ob-m-$i"))
                        }
                    }
                    2 -> {
                        Intro("🌶", "Votre curseur de piments", "Chacun règle le sien. Le jeu n'ira jamais plus loin que le plus doux de vos deux réglages.")
                        Spacer(Modifier.height(20.dp))
                        IntensityPicker(intensity, { intensity = it })
                    }
                    else -> {
                        Intro("🤫", "En toute discrétion", "Captures d'écran bloquées, aperçu masqué dans les applis récentes, notifications sans contenu (« C'est ton tour »).")
                        Spacer(Modifier.height(16.dp))
                        ListItem(
                            supportingContent = { Text("Empreinte ou code du téléphone") },
                            trailingContent = { Switch(lock, { haptics.toggle(it); lock = it }) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        ) { Text("Verrouiller Osé et Intime") }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { i ->
                    val w by animateFloatAsState(if (i == pager.currentPage) 24f else 8f, label = "dot$i")
                    Box(Modifier.height(8.dp).width(w.dp).clip(CircleShape).background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                }
            }
            if (pager.currentPage < 3) {
                TextButton(onClick = { onDone(intensity, lock) }) { Text("Passer") }
                Button(onClick = { haptics.tick(); scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, shapes = ButtonDefaults.shapes()) { Text("Suivant") }
            } else {
                Button(onClick = { haptics.confirm(); onDone(intensity, lock) }, shapes = ButtonDefaults.shapes()) { Text("Traverser le miroir") }
            }
        }
    }
}

@Composable
private fun Intro(emoji: String, title: String, body: String?) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        MorphingBlob(Modifier.fillMaxSize(), colors.primaryContainer, shapes = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Heart, MaterialShapes.Flower, MaterialShapes.Heart), periodMs = 4800)
        Text(emoji, style = MaterialTheme.typography.displayMedium)
    }
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    body?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
    }
}
