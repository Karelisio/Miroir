package com.karelisio.miroir.ui.recap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.miroir.ui.components.AnimatedCounter
import com.karelisio.miroir.ui.components.MorphingBlob
import com.karelisio.miroir.ui.components.miroirSafe
import com.karelisio.miroir.ui.components.miroirTopBar
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.staggeredAppear
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)
private val monthOnly = DateTimeFormatter.ofPattern("MMMM", Locale.FRENCH)

fun YearMonth.label(): String = format(monthFormat).replaceFirstChar { it.titlecase(Locale.FRENCH) }

@Composable
fun RecapScreen(vm: RecapViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    RecapContent(state, onMonth = vm::show, onBack = onBack)
}

@Composable
fun RecapContent(state: Pair<Recap, List<YearMonth>>?, onMonth: (YearMonth) -> Unit, onBack: () -> Unit) {
    val haptics = rememberHaptics()
    Scaffold(
        contentWindowInsets = WindowInsets.miroirSafe,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.miroirTopBar,
                title = { Text("Récap du mois") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        val (recap, months) = state ?: run {
            LoadingIndicator(Modifier.padding(padding).padding(32.dp))
            return@Scaffold
        }
        val index = months.indexOf(recap.month)
        val previous = if (index > 0) months[index - 1] else months.lastOrNull { it < recap.month }
        val next = if (index in 0 until months.lastIndex) months[index + 1] else months.firstOrNull { it > recap.month }
        AnimatedContent(
            targetState = recap,
            contentKey = { it.month },
            transitionSpec = {
                val forward = targetState.month > initialState.month
                (slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            label = "month",
        ) { r ->
            RecapBody(
                r,
                PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
                onPrevious = previous?.let { { haptics.tick(); onMonth(it) } },
                onNext = next?.let { { haptics.tick(); onMonth(it) } },
            )
        }
    }
}

@Composable
private fun RecapBody(r: Recap, padding: PaddingValues, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val key = r.month.toString()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.staggeredAppear(0, "$key-title")) {
            IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Mois précédent") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Votre mois en miroir", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                Text(r.month.label(), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            }
            IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Mois suivant") }
        }

        // Le chiffre phare : grossit en ressort, sur une forme qui se transforme.
        var shown by remember(key) { mutableStateOf(false) }
        LaunchedEffect(key) { shown = true }
        val pop by animateFloatAsState(if (shown) 1f else 0.6f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow), label = "pop")
        Box(Modifier.fillMaxWidth().height(230.dp).staggeredAppear(1, "$key-hero"), contentAlignment = Alignment.Center) {
            MorphingBlob(Modifier.size(220.dp).graphicsLayer { alpha = 0.22f; scaleX = pop; scaleY = pop }, colors.primary)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop }) {
                AnimatedCounter(if (shown) r.together else 0, MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold), color = colors.primary)
                Text(if (r.together > 1) "questions à deux" else "question à deux", style = MaterialTheme.typography.titleMedium)
                Text("sur ${r.days} jour${if (r.days > 1) "s" else ""} de miroir", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.staggeredAppear(2, "$key-row1")) {
            Tile("🔥", "${r.bestStreak} j", "meilleure série", colors.tertiaryContainer, Modifier.weight(1f))
            Tile("💬", "${r.comments}", if (r.comments > 1) "petits mots" else "petit mot", colors.secondaryContainer, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.staggeredAppear(3, "$key-row2")) {
            Tile("👀", r.agreement?.let { "$it %" } ?: "—", "d'accord", colors.secondaryContainer, Modifier.weight(1f))
            Tile("🔮", r.guess?.let { "$it %" } ?: "—", "bien devinés", colors.tertiaryContainer, Modifier.weight(1f))
        }

        if (r.reactions.isNotEmpty()) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth().staggeredAppear(4, "$key-react")) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Vos réactions préférées", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        r.reactions.forEachIndexed { i, (emoji, count) ->
                            var on by remember(key) { mutableStateOf(false) }
                            LaunchedEffect(key) { kotlinx.coroutines.delay(250L + 120L * i); on = true }
                            val s by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow), label = "e$i")
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s }) {
                                Text(emoji, style = MaterialTheme.typography.displaySmall)
                                Text("× $count", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        r.topCategory?.let { c ->
            Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth().staggeredAppear(5, "$key-cat")) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialShapes.Cookie9Sided.toShape(), color = colors.primaryContainer, modifier = Modifier.size(52.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(c.emoji, style = MaterialTheme.typography.headlineSmall) }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Votre thème du mois", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        Text(c.label, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }

        r.highlight?.let { h ->
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
                modifier = Modifier.fillMaxWidth().staggeredAppear(6, "$key-hl"),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (h.favorite) "⭐ Votre favori du mois" else "💜 Un souvenir du mois", style = MaterialTheme.typography.labelLarge)
                    Text(h.question, style = MaterialTheme.typography.titleLarge)
                    Text(h.mine, style = MaterialTheme.typography.bodyLarge)
                    Text(h.theirs, style = MaterialTheme.typography.bodyLarge, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
                }
            }
        }

        Text(
            "À vous d'écrire ${r.month.plusMonths(1).format(monthOnly)} 💜",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = colors.primary,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).staggeredAppear(7, "$key-end"),
        )
    }
}

@Composable
private fun Tile(emoji: String, value: String, label: String, container: Color, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = container, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
