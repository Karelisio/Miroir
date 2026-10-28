package com.karelisio.miroir.ui.history

import com.karelisio.miroir.ui.today.AnswerInput
import com.karelisio.miroir.ui.components.rememberHaptics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import com.karelisio.miroir.ui.components.miroirTopBar
import com.karelisio.miroir.ui.components.miroirSafe
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.data.model.Pack
import com.karelisio.miroir.ui.components.staggeredAppear
import com.karelisio.miroir.ui.navigation.sharedContainer
import com.karelisio.miroir.ui.navigation.sharedItem
import com.karelisio.miroir.ui.today.CategoryChip
import com.karelisio.miroir.ui.today.MirrorReveal
import com.karelisio.miroir.ui.today.ReactionsSection
import com.karelisio.miroir.ui.today.display
import com.karelisio.miroir.ui.today.myAnswerPayload
import com.karelisio.miroir.ui.today.myReactionPayload
import com.karelisio.miroir.ui.today.partnerAnswerPayload
import com.karelisio.miroir.ui.today.partnerReactionPayload
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val detailDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)

@Composable
fun DayDetailScreen(vm: HistoryViewModel, day: String, onBack: () -> Unit, onMessage: (String) -> Unit = {}) {
    val flow = remember(day) { vm.day(day) }
    val ui by flow.collectAsState(initial = null)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) { vm.events.collect(onMessage) }
    // La révélation joue si elle n'a jamais été vue sur ce téléphone (ex. question rattrapée).
    var play by rememberSaveable(day) { mutableStateOf<Boolean?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.miroirSafe,
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                windowInsets = WindowInsets.miroirTopBar,
                title = { Text(LocalDate.parse(day).format(detailDate).replaceFirstChar { it.titlecase(Locale.FRENCH) }) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = {
                    val favorite = ui?.entity?.favorite == true
                    val scale by animateFloatAsState(if (favorite) 1.2f else 1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium), label = "star")
                    IconButton(onClick = { haptics.toggle(!favorite); vm.toggleFavorite(day, !favorite) }, enabled = ui != null) {
                        Icon(
                            if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = if (favorite) "Retirer des favoris" else "Ajouter aux favoris",
                            tint = if (favorite) MaterialTheme.colorScheme.tertiary else LocalContentColor.current,
                            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = (scale - 1f) * 72f },
                        )
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        val d = ui
        if (d == null) {
            LoadingIndicator(Modifier.padding(padding).padding(32.dp))
            return@Scaffold
        }
        val e = d.entity
        val p = d.prefs
        val mine = e.myAnswerPayload()
        val theirs = e.partnerAnswerPayload()
        val nameOf: (String) -> String = { if (it == p.member.key) p.myName.ifBlank { "Toi" } else p.partnerDisplayName }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth().sharedContainer("day-$day"),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val category = Category.of(e.category)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryChip("${category.emoji} ${category.label}")
                        Pack.of(e.pack)?.let { CategoryChip("${it.emoji} ${it.label}", highlight = true) }
                    }
                    Text(e.questionText, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.sharedItem("q-$day"))
                }
            }
            when {
                mine != null && theirs != null -> {
                    if (play == null) play = !e.revealSeen
                    MirrorReveal(
                        d.question, mine, theirs, p.member, p.myName, p.partnerDisplayName,
                        play = play == true,
                        onPlayed = { vm.markRevealSeen(day) },
                        modifier = Modifier.staggeredAppear(1, "d-reveal"),
                    )
                    ReactionsSection(e.myReactionPayload(), e.partnerReactionPayload(), p.partnerDisplayName) { emoji, comment ->
                        vm.react(day, emoji, comment)
                    }
                }
                mine != null -> Text(
                    "Ta réponse : ${mine.display(d.question, nameOf)}\n\n" +
                        if (d.isYesterday) "En attente de ${p.partnerDisplayName}, qui peut encore répondre jusqu'à minuit." else "${p.partnerDisplayName} n'a pas répondu ce jour-là.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                d.canCatchUp && d.question != null -> {
                    if (play == null) play = true
                    Text(
                        if (e.partnerAnswered) "${p.partnerDisplayName} a répondu. Rattrape la question avant minuit pour garder votre série 🔥"
                        else "Encore jusqu'à minuit pour répondre et garder votre série 🔥",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnswerInput(d.question, p.member, p.myName, p.partnerDisplayName) { vm.answer(day, it) }
                }
                e.partnerAnswered -> Text("${p.partnerDisplayName} a répondu, mais pas toi : sa réponse reste secrète.", style = MaterialTheme.typography.bodyLarge)
                else -> Text("Personne n'a répondu ce jour-là.", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
