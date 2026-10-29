package com.karelisio.miroir.games.ui.canape

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.miroir.games.model.GameItem
import com.karelisio.miroir.games.model.GameValue
import com.karelisio.miroir.games.model.ItemFormat
import com.karelisio.miroir.games.ui.components.Confetti
import com.karelisio.miroir.games.ui.components.CountdownRing
import com.karelisio.miroir.games.ui.components.FlipCard
import com.karelisio.miroir.games.ui.components.GameFrame
import com.karelisio.miroir.games.ui.components.ModeTile
import com.karelisio.miroir.games.ui.components.OptionCard
import com.karelisio.miroir.games.ui.components.PassScreen
import com.karelisio.miroir.games.ui.components.QuestionFace
import com.karelisio.miroir.games.ui.components.RevealPair
import com.karelisio.miroir.games.ui.components.SpicyGate
import com.karelisio.miroir.games.ui.components.ValueInput
import com.karelisio.miroir.games.ui.components.isComplete
import com.karelisio.miroir.ui.components.AnimatedCounter
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.staggeredAppear
import kotlinx.coroutines.delay

@Composable
fun CanapeScreen(vm: CanapeViewModel, onBack: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    GameFrame("🛋️ Mode canapé", onBack = { if (ui.step == CanapeStep.Setup || ui.step == CanapeStep.End) onBack() else vm.reset() }, snackbar = snackbar) { padding ->
        SpicyGate(required = ui.lockRequired) { CanapeContent(ui, padding, vm) }
    }
}

@Composable
fun CanapeContent(ui: CanapeUi, padding: PaddingValues, vm: CanapeViewModel?) {
    AnimatedContent(
        targetState = ui.step,
        contentKey = { it::class to (it as? CanapeStep.Pass)?.to },
        transitionSpec = {
            (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
        },
        label = "canape",
        modifier = Modifier.fillMaxSize().padding(padding),
    ) { step ->
        when (step) {
            CanapeStep.Setup -> Setup(ui, vm)
            is CanapeStep.Pass -> PassScreen(ui.players[step.to]) { vm?.passed() }
            is CanapeStep.Answer -> Turn(ui, step.round, step.player, guessing = false, vm)
            is CanapeStep.Guess -> Turn(ui, step.round, step.player, guessing = true, vm)
            is CanapeStep.Reveal -> Reveal(ui, step.round, vm)
            is CanapeStep.AvPick -> AvPick(ui, step, vm)
            is CanapeStep.AvCard -> AvCard(ui, step, vm)
            CanapeStep.End -> End(ui, vm)
        }
    }
}

@Composable
private fun Scroll(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun Setup(ui: CanapeUi, vm: CanapeViewModel?) = Scroll {
    Text("Un seul téléphone, à se passer.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.staggeredAppear(0, "c-title"))
    Text("Fonctionne sans réseau. Le chrono est facultatif.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    CanapeGame.entries.forEachIndexed { i, g ->
        ModeTile(g.emoji, g.label, g.description, Modifier.fillMaxWidth().staggeredAppear(i + 1, "c-$g"), highlight = ui.game == g) { vm?.setGame(g) }
    }
    Text(if (ui.game == CanapeGame.AV) "Tours" else "Manches", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(4, 6, 10, 16).forEach { n -> FilterChip(selected = ui.rounds == n, onClick = { vm?.setRounds(n) }, label = { Text("$n") }) }
    }
    Text("Chrono", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to "Sans", 30 to "30 s", 60 to "1 min", 120 to "2 min").forEach { (s, label) ->
            FilterChip(selected = ui.timer == s, onClick = { vm?.setTimer(s) }, label = { Text(label) })
        }
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = { vm?.start() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text("C'est parti, ${ui.players[0]} commence", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Turn(ui: CanapeUi, round: Int, player: Int, guessing: Boolean, vm: CanapeViewModel?) {
    val item = ui.items.getOrNull(round) ?: return
    var flipped by rememberSaveable(round, guessing) { mutableStateOf(false) }
    var value by remember(round, guessing) { mutableStateOf<GameValue?>(null) }
    LaunchedEffect(round, guessing) { delay(250); flipped = true }
    val name = ui.players[player]
    val other = ui.other(player)
    Scroll {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Manche ${round + 1} sur ${ui.items.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    when {
                        ui.game == CanapeGame.PREFERES -> "$name, choisis en secret"
                        guessing -> "$name, devine la réponse de $other"
                        else -> "$name, réponds pour toi"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            CountdownRing(ui.timer, key = "$round-$guessing")
        }
        FlipCard(flipped) { QuestionFace(item, other = other) }
        if (ui.game == CanapeGame.PREFERES) {
            item.options.forEachIndexed { i, o -> OptionCard(o, selected = value?.choice == i) { value = GameValue(choice = i) } }
        } else {
            ValueInput(item, value, { value = it }, placeholder = if (guessing) "Ce que $other a répondu…" else "Ta réponse…")
        }
        Button(
            onClick = { value?.let { if (guessing) vm?.guess(it) else vm?.answer(it) } },
            enabled = value.isComplete(),
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(if (guessing) "Révéler" else "Valider en secret", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun Reveal(ui: CanapeUi, round: Int, vm: CanapeViewModel?) {
    val item = ui.items.getOrNull(round) ?: return
    val answerer = ui.answererOf(round)
    val guesser = ui.guesserOf(round)
    val answer = ui.answers[round]
    val guess = ui.guesses[round]
    val result = ui.results[round]
    val haptics = rememberHaptics()
    Box(Modifier.fillMaxSize()) {
        Scroll {
            QuestionFace(item, other = ui.players[guesser])
            RevealPair(
                leftName = ui.players[answerer] + if (ui.game == CanapeGame.DEVINE) " a répondu" else "",
                left = display(item, answer, ui.game),
                rightName = ui.players[guesser] + if (ui.game == CanapeGame.DEVINE) " a deviné" else "",
                right = display(item, guess, ui.game),
                match = result,
            )
            if (ui.game == CanapeGame.DEVINE && item.format == ItemFormat.TEXT && result == null) {
                Text("C'était bien deviné, ${ui.players[answerer]} ?", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { haptics.confirm(); vm?.judge(round, true) }, modifier = Modifier.weight(1f)) { Text("Bien vu ✓") }
                    OutlinedButton(onClick = { vm?.judge(round, false) }, modifier = Modifier.weight(1f)) { Text("Raté") }
                }
            } else {
                Text(
                    when {
                        ui.game == CanapeGame.PREFERES -> if (result == true) "Même choix 💞" else "Deux visions, à débattre 😄"
                        result == true -> "Bien vu, ${ui.players[guesser]} ! +1 🔮"
                        else -> "Raté… mais c'est l'occasion d'en parler."
                    },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                ScoreRow(ui)
                Button(onClick = { vm?.next() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(if (round + 1 >= ui.items.size) "Voir le résultat" else "Manche suivante · ${ui.players[guesser]} répond")
                }
            }
        }
        Confetti(trigger = if (result == true) "c-$round" else null)
    }
}

private fun display(item: GameItem, v: GameValue?, game: CanapeGame): String = when {
    v == null -> "—"
    game == CanapeGame.PREFERES -> item.options.getOrNull(v.choice ?: -1) ?: "—"
    else -> v.display(item)
}

@Composable
private fun ScoreRow(ui: CanapeUi) {
    val scores = ui.scores
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        if (ui.game == CanapeGame.PREFERES) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row { AnimatedCounter(scores[0], MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary); Text(" / ${scores[1]}", style = MaterialTheme.typography.headlineSmall) }
                Text("accords", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            ui.players.forEachIndexed { i, name ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedCounter(scores[i], MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                    Text(name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun AvPick(ui: CanapeUi, step: CanapeStep.AvPick, vm: CanapeViewModel?) = Scroll {
    Text("Tour ${step.round + 1} sur ${ui.rounds}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text("${ui.players[step.player]}, action ou vérité ?", style = MaterialTheme.typography.headlineSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ModeTile("🎯", "Action", "Un petit gage, maintenant", Modifier.weight(1f).staggeredAppear(0, "av-a-${step.round}")) { vm?.pick("action") }
        ModeTile("💬", "Vérité", "Une réponse honnête", Modifier.weight(1f).staggeredAppear(1, "av-v-${step.round}"), highlight = true) { vm?.pick("verite") }
    }
    ScoreRow(ui)
}

@Composable
private fun AvCard(ui: CanapeUi, step: CanapeStep.AvCard, vm: CanapeViewModel?) {
    var flipped by rememberSaveable(step.round) { mutableStateOf(false) }
    LaunchedEffect(step.round) { delay(250); flipped = true }
    Scroll {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${ui.players[step.player]} · ${if (step.pick == "action") "Action 🎯" else "Vérité 💬"}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            CountdownRing(ui.timer, key = "av-${step.round}")
        }
        FlipCard(flipped) { QuestionFace(step.item, other = ui.other(step.player)) }
        Button(onClick = { vm?.avDone(true) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(if (step.pick == "action") "C'est fait ✓" else "J'ai répondu ✓", style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = { vm?.avDone(false) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Joker 🃏 (on passe)") }
    }
}

@Composable
private fun End(ui: CanapeUi, vm: CanapeViewModel?) {
    Box(Modifier.fillMaxSize()) {
        Scroll {
            Text("Partie terminée ✨", style = MaterialTheme.typography.displaySmall, modifier = Modifier.staggeredAppear(0, "end-t"))
            Text(
                when (ui.game) {
                    CanapeGame.DEVINE -> "Bons pronostics de chacun :"
                    CanapeGame.PREFERES -> "Vos accords :"
                    CanapeGame.AV -> "Gages et vérités relevés :"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            ScoreRow(ui)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm?.start() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Rejouer") }
            OutlinedButton(onClick = { vm?.reset() }, modifier = Modifier.fillMaxWidth()) { Text("Changer de jeu") }
        }
        Confetti(trigger = "end")
    }
}
