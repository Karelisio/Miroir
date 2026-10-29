package com.karelisio.miroir.games.ui.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.miroir.games.model.GameItem
import com.karelisio.miroir.games.model.GameKind
import com.karelisio.miroir.games.model.GameValue
import com.karelisio.miroir.games.model.ItemFormat
import com.karelisio.miroir.games.ui.DONE_ITEM
import com.karelisio.miroir.games.ui.SessionStatus
import com.karelisio.miroir.games.ui.SessionUi
import com.karelisio.miroir.games.ui.SessionViewModel
import com.karelisio.miroir.games.ui.components.Confetti
import com.karelisio.miroir.games.ui.components.FlipCard
import com.karelisio.miroir.games.ui.components.GameFrame
import com.karelisio.miroir.games.ui.components.ModeTile
import com.karelisio.miroir.games.ui.components.OptionCard
import com.karelisio.miroir.games.ui.components.QuestionFace
import com.karelisio.miroir.games.ui.components.RevealPair
import com.karelisio.miroir.games.ui.components.SpicyGate
import com.karelisio.miroir.games.ui.components.StatusChip
import com.karelisio.miroir.games.ui.components.ValueInput
import com.karelisio.miroir.games.ui.components.isComplete
import com.karelisio.miroir.games.ui.judgeKey
import com.karelisio.miroir.games.ui.questionKey
import com.karelisio.miroir.games.ui.statusLabel
import com.karelisio.miroir.games.ui.turnKey
import com.karelisio.miroir.ui.components.AnimatedCounter
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.staggeredAppear
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SessionScreen(vm: SessionViewModel, onBack: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }
    val title = ui?.view?.kind?.let { "${it.emoji} ${it.label}" } ?: "Partie"
    GameFrame(title, onBack, snackbar, actions = {
        ui?.let { StatusChip(statusLabel(it.status, it.partnerName), it.status == SessionStatus.YOUR_TURN, Modifier.padding(end = 12.dp)) }
    }) { padding ->
        val s = ui
        if (s == null) {
            LoadingIndicator(Modifier.padding(padding).padding(32.dp))
            return@GameFrame
        }
        SpicyGate(required = s.lockSpicy && s.spicy) {
            SessionContent(s, padding, vm)
        }
    }
}

@Composable
fun SessionContent(s: SessionUi, padding: PaddingValues, vm: SessionViewModel?) {
    Box(Modifier.fillMaxSize().padding(padding)) {
        when (s.view.kind) {
            GameKind.DEVINE -> Devine(s, vm)
            GameKind.DUEL -> Duel(s, vm)
            GameKind.PREFERES -> Preferes(s, vm)
            GameKind.ACTION_VERITE -> ActionVerite(s, vm)
            GameKind.SURPRISE -> Surprise(s, vm)
            else -> Text("Cette partie se consulte depuis l'accueil.", Modifier.padding(24.dp))
        }
    }
}

@Composable
private fun Scroll(key: Any? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

/** Saisie d'une question à la fois, avec carte retournée à chaque nouvelle question. */
@Composable
private fun StepInput(
    s: SessionUi,
    index: Int,
    header: String,
    selfLabel: String?,
    guessLabel: String?,
    onSubmit: (GameValue?, GameValue?) -> Unit,
) {
    val item = s.items.getOrNull(index) ?: return
    var flipped by rememberSaveable(index) { mutableStateOf(false) }
    var self by remember(index) { mutableStateOf<GameValue?>(null) }
    var guess by remember(index) { mutableStateOf<GameValue?>(null) }
    LaunchedEffect(index) { delay(220); flipped = true }
    val haptics = rememberHaptics()
    Scroll {
        Text("Question ${index + 1} sur ${s.items.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(header, style = MaterialTheme.typography.titleLarge)
        FlipCard(flipped) { QuestionFace(item, other = s.partnerName) }
        if (selfLabel != null) {
            Text(selfLabel, style = MaterialTheme.typography.titleSmall)
            Inputs(s, item, self) { self = it }
        }
        if (guessLabel != null) {
            Text(guessLabel, style = MaterialTheme.typography.titleSmall)
            Inputs(s, item, guess, placeholder = "Ce que ${s.partnerName} va répondre…") { guess = it }
        }
        val ready = (selfLabel == null || self.isComplete()) && (guessLabel == null || guess.isComplete())
        Button(
            onClick = { haptics.confirm(); onSubmit(self, guess) },
            enabled = ready,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(if (index + 1 == s.items.size) "Terminer 🔒" else "Valider en secret", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun Inputs(s: SessionUi, item: GameItem, value: GameValue?, placeholder: String = "Ta réponse…", onValue: (GameValue?) -> Unit) {
    if (s.view.kind == GameKind.PREFERES) {
        item.options.forEachIndexed { i, o -> OptionCard(o, selected = value?.choice == i) { onValue(GameValue(choice = i)) } }
    } else {
        ValueInput(item, value, onValue, placeholder = placeholder)
    }
}

@Composable
private fun Waiting(s: SessionUi, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ContainedLoadingIndicator(Modifier.size(80.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            "On te prévient dès que c'est à toi, avec une notification discrète.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PinButton(s: SessionUi, vm: SessionViewModel?, item: String, question: String, answer: String, author: String) {
    val pinned = item in s.pins
    IconButton(onClick = { vm?.togglePin(item, question, answer, author) }) {
        Icon(if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, if (pinned) "Retirer de Nos révélations" else "Épingler dans Nos révélations")
    }
}

// ---------------------------------------------------------------- Devine-moi

@Composable
private fun Devine(s: SessionUi, vm: SessionViewModel?) {
    val v = s.view
    val iAnswer = v.answerer == v.me
    val next = s.items.indices.firstOrNull { !v.answered(v.me, questionKey(it)) }
    LaunchedEffect(next) { if (next == null && !v.answered(v.me, DONE_ITEM)) vm?.finish() }
    if (next != null) {
        StepInput(
            s, next,
            header = if (iAnswer) "Réponds pour toi : ${s.partnerName} devinera" else "Devine ce que ${s.partnerName} a répondu",
            selfLabel = if (iAnswer) "Ta réponse" else null,
            guessLabel = if (iAnswer) null else "Ton pronostic",
        ) { self, guess -> vm?.answer(next, self, guess) }
        return
    }
    var played by rememberSaveable { mutableStateOf(setOf<Int>()) }
    Scroll {
        val correct = s.items.indices.count { i -> devineResult(s, i) == true }
        val judged = s.items.indices.count { i -> devineResult(s, i) != null }
        Text(
            if (iAnswer) "${s.partnerName} a deviné $correct sur $judged" else "Tu as deviné $correct sur $judged",
            style = MaterialTheme.typography.headlineSmall,
        )
        s.items.forEachIndexed { i, item ->
            item ?: return@forEachIndexed
            val self = v.answer(v.answerer, questionKey(i))?.self
            val guess = v.answer(v.guesser, questionKey(i))?.guess
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.staggeredAppear(i, "dv-$i")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.render(s.partnerName), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (!iAnswer && self != null) PinButton(s, vm, questionKey(i), item.render(s.myName), self.display(item), v.answerer)
                    }
                    if (self == null || guess == null) {
                        StatusChip(if (self == null) "En attente de la réponse de ${s.name(v.answerer)}" else "En attente du pronostic de ${s.name(v.guesser)}", false)
                    } else {
                        RevealPair(
                            leftName = "${s.name(v.answerer)} a répondu", left = self.display(item),
                            rightName = "${s.name(v.guesser)} a deviné", right = guess.display(item),
                            match = devineResult(s, i),
                            play = i !in played, onPlayed = { played = played + i },
                        )
                        if (item.format == ItemFormat.TEXT && devineResult(s, i) == null) {
                            if (iAnswer) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FilledTonalButton(onClick = { vm?.judge(i, true) }, modifier = Modifier.weight(1f)) { Text("Bien deviné ✓") }
                                OutlinedButton(onClick = { vm?.judge(i, false) }, modifier = Modifier.weight(1f)) { Text("Raté") }
                            } else StatusChip("${s.partnerName} juge ton pronostic…", false)
                        }
                    }
                }
            }
        }
    }
}

private fun devineResult(s: SessionUi, i: Int): Boolean? {
    val v = s.view
    val item = s.items.getOrNull(i) ?: return null
    val self = v.answer(v.answerer, questionKey(i))?.self ?: return null
    val guess = v.answer(v.guesser, questionKey(i))?.guess ?: return null
    return if (item.format == ItemFormat.TEXT) v.answer(v.answerer, judgeKey(i))?.ok else guess.matches(self)
}

// ---------------------------------------------------------------- Duel

@Composable
private fun Duel(s: SessionUi, vm: SessionViewModel?) {
    val v = s.view
    val next = s.items.indices.firstOrNull { !v.answered(v.me, questionKey(it)) }
    LaunchedEffect(next) { if (next == null && !v.done(v.me)) vm?.finish() }
    when {
        next != null -> StepInput(
            s, next,
            header = "Réponds pour toi, puis devine ${s.partnerName}",
            selfLabel = "Ta réponse",
            guessLabel = "Et selon toi, ${s.partnerName} ?",
        ) { self, guess -> vm?.answer(next, self, guess) }
        !v.done(v.partner) -> Scroll { Waiting(s, "En attente de ${s.partnerName}…") }
        else -> DuelReveal(s, vm)
    }
}

@Composable
private fun DuelReveal(s: SessionUi, vm: SessionViewModel?) {
    val v = s.view
    var page by rememberSaveable { mutableIntStateOf(0) }
    val haptics = rememberHaptics()
    val score = { member: String ->
        s.items.indices.count { i -> v.answer(member, questionKey(i))?.guess?.matches(v.answer(if (member == "A") "B" else "A", questionKey(i))?.self) == true }
    }
    AnimatedContent(
        targetState = page,
        transitionSpec = { (slideInHorizontally { it / 2 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
        label = "duel",
    ) { p ->
        if (p >= s.items.size) {
            Box(Modifier.fillMaxSize()) {
                Scroll {
                    Text("Résultat du duel", style = MaterialTheme.typography.displaySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(v.me to s.myName, v.partner to s.partnerName).forEach { (m, name) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AnimatedCounter(score(m), MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                                Text(name, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    val mine = score(v.me)
                    val theirs = score(v.partner)
                    Text(
                        when {
                            mine == theirs -> "Égalité parfaite : vous vous connaissez autant l'un que l'autre 🪞"
                            mine > theirs -> "Tu connais ${s.partnerName} un peu mieux… pour cette fois 😏"
                            else -> "${s.partnerName} te connaît par cœur 💞"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { page = 0 }, modifier = Modifier.fillMaxWidth()) { Text("Revoir les réponses") }
                }
                Confetti("duel-end")
            }
            return@AnimatedContent
        }
        val item = s.items[p] ?: return@AnimatedContent
        val mine = v.answer(v.me, questionKey(p))
        val theirs = v.answer(v.partner, questionKey(p))
        val same = mine?.self?.matches(theirs?.self)
        Box(Modifier.fillMaxSize()) {
            Scroll {
                Text("Question ${p + 1} sur ${s.items.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                QuestionFace(item, other = s.partnerName)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    theirs?.self?.let { PinButton(s, vm, questionKey(p), item.render(s.myName), it.display(item), v.partner) }
                }
                RevealPair(s.myName, mine?.self?.display(item) ?: "—", s.partnerName, theirs?.self?.display(item) ?: "—", same)
                GuessLine("Tu avais deviné", mine?.guess?.display(item), mine?.guess?.matches(theirs?.self))
                GuessLine("${s.partnerName} avait deviné", theirs?.guess?.display(item), theirs?.guess?.matches(mine?.self))
                Button(onClick = { haptics.tick(); page = p + 1 }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(if (p + 1 == s.items.size) "Voir le score" else "Question suivante")
                }
            }
            Confetti(if (same == true) "duel-$p" else null)
        }
    }
}

@Composable
private fun GuessLine(label: String, guess: String?, right: Boolean?) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (right == true) colors.tertiaryContainer else colors.surfaceContainerHigh,
        contentColor = if (right == true) colors.onTertiaryContainer else colors.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🔮  $label « ${guess ?: "—"} »", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(if (right == true) "+1 ✓" else "Raté", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ---------------------------------------------------------------- Tu préfères

@Composable
private fun Preferes(s: SessionUi, vm: SessionViewModel?) {
    val v = s.view
    val next = s.items.indices.firstOrNull { !v.answered(v.me, questionKey(it)) }
    LaunchedEffect(next) { if (next == null && !v.done(v.me)) vm?.finish() }
    when {
        next != null -> StepInput(s, next, header = "Choisis en secret", selfLabel = "Ton choix", guessLabel = null) { self, _ -> vm?.answer(next, self, null) }
        !v.done(v.partner) -> Scroll { Waiting(s, "${s.partnerName} n'a pas encore fini ses dilemmes…") }
        else -> {
            val pairs = s.items.mapIndexedNotNull { i, item ->
                val a = v.answer(v.me, questionKey(i))?.self?.choice
                val b = v.answer(v.partner, questionKey(i))?.self?.choice
                if (item == null || a == null || b == null) null else Triple(item, a, b)
            }
            val agree = pairs.count { it.second == it.third }
            Box(Modifier.fillMaxSize()) {
                Scroll {
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedCounter(if (pairs.isEmpty()) 0 else (100.0 * agree / pairs.size).roundToInt(), MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                        Text(" % d'accord", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    pairs.groupBy { it.first.category }.forEach { (cat, list) ->
                        Text("${cat.emoji} ${cat.label} : ${list.count { it.second == it.third }} sur ${list.size}", style = MaterialTheme.typography.labelLarge)
                    }
                    pairs.forEachIndexed { i, (item, a, b) ->
                        Column(Modifier.staggeredAppear(i, "pf-$i"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.render(s.partnerName), style = MaterialTheme.typography.titleMedium)
                            RevealPair(s.myName, item.options.getOrElse(a) { "—" }, s.partnerName, item.options.getOrElse(b) { "—" }, a == b)
                        }
                    }
                }
                Confetti(if (pairs.isNotEmpty() && agree * 2 >= pairs.size) "pf-end" else null)
            }
        }
    }
}

// ---------------------------------------------------------------- Action ou vérité

@Composable
private fun ActionVerite(s: SessionUi, vm: SessionViewModel?) {
    val v = s.view
    val turn = v.turnsPlayed
    val finished = turn >= v.totalTurns || v.entity.status != "open"
    val myTurn = !finished && v.turnPlayer(turn) == v.me
    val scope = rememberCoroutineScope()
    var drawn by remember(turn) { mutableStateOf<Pair<String, GameItem>?>(null) }
    var text by remember(turn) { mutableStateOf("") }
    var flipped by remember(turn) { mutableStateOf(false) }
    LaunchedEffect(drawn) { if (drawn != null) { delay(200); flipped = true } }
    Scroll {
        (0 until turn).forEach { i ->
            val player = v.turnPlayer(i)
            val a = v.answer(player, turnKey(i)) ?: return@forEach
            val item = a.item?.let { s.itemsById[it] }
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().staggeredAppear(i, "av-$i")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.name(player)} · ${if (a.pick == "action") "Action 🎯" else "Vérité 💬"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        val answer = a.self?.text
                        if (player != v.me && !answer.isNullOrBlank() && item != null) PinButton(s, vm, turnKey(i), item.render(s.myName), answer, player)
                    }
                    item?.let { Text(it.render(s.name(if (player == "A") "B" else "A")), style = MaterialTheme.typography.titleMedium) }
                    a.self?.text?.takeIf { it.isNotBlank() }?.let { Text("« $it »", style = MaterialTheme.typography.bodyLarge) }
                    Text(if (a.ok == false) "Joker 🃏" else "Fait ✓", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        when {
            finished -> {
                Text("Partie terminée ✨", style = MaterialTheme.typography.headlineSmall)
                Text("${v.totalTurns} tours joués à distance.", style = MaterialTheme.typography.bodyMedium)
            }
            !myTurn -> Waiting(s, "C'est au tour de ${s.partnerName}")
            drawn == null -> {
                Text("Tour ${turn + 1} sur ${v.totalTurns} · action ou vérité ?", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("action" to ("🎯" to "Action"), "verite" to ("💬" to "Vérité")).forEach { (pick, label) ->
                        ModeTile(label.first, label.second, if (pick == "action") "À faire à distance" else "Réponse écrite", Modifier.weight(1f), highlight = pick == "verite") {
                            scope.launch { vm?.drawTurn(pick)?.let { drawn = pick to it } }
                        }
                    }
                }
                TextButton(onClick = { vm?.stop() }) { Text("Arrêter la partie") }
            }
            else -> {
                val (pick, item) = drawn!!
                FlipCard(flipped) { QuestionFace(item, other = s.partnerName) }
                if (pick == "verite") {
                    OutlinedTextField(text, { if (it.length <= 400) text = it }, Modifier.fillMaxWidth(), placeholder = { Text("Ta réponse, en toute honnêteté…") }, shape = MaterialTheme.shapes.large)
                    Button(onClick = { vm?.playTurn(turn, pick, item, text, true) }, enabled = text.isNotBlank(), shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Envoyer à ${s.partnerName}") }
                } else {
                    OutlinedTextField(text, { if (it.length <= 200) text = it }, Modifier.fillMaxWidth(), placeholder = { Text("Un petit mot (facultatif)") }, shape = MaterialTheme.shapes.large)
                    Button(onClick = { vm?.playTurn(turn, pick, item, text.ifBlank { null }, true) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("C'est fait ✓") }
                }
                TextButton(onClick = { vm?.playTurn(turn, pick, item, null, false) }) { Text("Joker 🃏") }
            }
        }
    }
}

// ---------------------------------------------------------------- Question surprise

@Composable
private fun Surprise(s: SessionUi, vm: SessionViewModel?) {
    val v = s.view
    val recipient = v.challenged
    val answer = v.answer(recipient, questionKey(0))?.self?.text
    var text by rememberSaveable { mutableStateOf("") }
    Scroll {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🎁 Question de ${s.name(v.entity.createdBy)}", style = MaterialTheme.typography.labelLarge)
                Text(v.payload.question.orEmpty(), style = MaterialTheme.typography.headlineSmall)
            }
        }
        when {
            answer != null -> Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text("Réponse de ${s.name(recipient)}", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(answer, style = MaterialTheme.typography.titleMedium)
                    }
                    if (recipient != v.me) PinButton(s, vm, questionKey(0), v.payload.question.orEmpty(), answer, recipient)
                }
            }
            recipient == v.me -> {
                OutlinedTextField(text, { if (it.length <= 500) text = it }, Modifier.fillMaxWidth(), placeholder = { Text("Ta réponse…") }, shape = MaterialTheme.shapes.large, minLines = 3)
                Button(onClick = { vm?.answerSurprise(text) }, enabled = text.isNotBlank(), shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Répondre à ${s.partnerName}")
                }
            }
            else -> Waiting(s, "${s.partnerName} n'a pas encore répondu")
        }
    }
}
