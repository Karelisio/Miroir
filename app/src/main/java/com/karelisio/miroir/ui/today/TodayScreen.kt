package com.karelisio.miroir.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.miroir.data.model.AnswerPayload
import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.data.model.Pack
import com.karelisio.miroir.data.model.Question
import com.karelisio.miroir.data.model.ReactionPayload
import com.karelisio.miroir.permissions.AppPermission
import com.karelisio.miroir.ui.components.AnimatedCounter
import com.karelisio.miroir.ui.components.MorphingBlob
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.springPress
import com.karelisio.miroir.ui.components.staggeredAppear
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

val REACTIONS = listOf("💜", "😂", "🥰", "😮", "🔥", "🤔", "👏", "🙈")

private val dateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
private val longDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
private val monthName = DateTimeFormatter.ofPattern("MMMM", Locale.FRENCH)

@Composable
fun TodayScreen(
    vm: TodayViewModel,
    contentPadding: PaddingValues,
    onShowInvite: () -> Unit,
    onOpenPermissions: () -> Unit,
    onMessage: (String) -> Unit,
    onOpenDay: (String) -> Unit = {},
    onOpenRecap: (String) -> Unit = {},
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsState()
    val skipping by vm.skipping.collectAsState()
    LaunchedEffect(Unit) { vm.events.collect { if (it is TodayMessage.Text) onMessage(it.text) } }
    TodayContent(
        ui = ui,
        refreshing = refreshing,
        skipping = skipping,
        contentPadding = contentPadding,
        actions = TodayActions(
            refresh = { vm.refresh() },
            submit = vm::submit,
            skip = vm::skip,
            react = vm::react,
            revealSeen = vm::markRevealSeen,
            dismissPermissionReminder = vm::dismissPermissionReminder,
            showInvite = onShowInvite,
            openPermissions = onOpenPermissions,
            checkUpdate = vm::checkUpdate,
            nudge = vm::nudge,
            openDay = onOpenDay,
            openRecap = { month -> vm.markRecapSeen(month); onOpenRecap(month) },
            dismissRecap = vm::markRecapSeen,
        ),
    )
}

/** Actions de l'écran du jour (séparées pour garder un contenu sans état, prévisualisable et testable). */
class TodayActions(
    val refresh: () -> Unit = {},
    val submit: (AnswerPayload) -> Unit = {},
    val skip: () -> Unit = {},
    val react: (String?, String?) -> Unit = { _, _ -> },
    val revealSeen: () -> Unit = {},
    val dismissPermissionReminder: () -> Unit = {},
    val showInvite: () -> Unit = {},
    val openPermissions: () -> Unit = {},
    val checkUpdate: () -> Unit = {},
    val nudge: () -> Unit = {},
    val openDay: (String) -> Unit = {},
    val openRecap: (String) -> Unit = {},
    val dismissRecap: (String) -> Unit = {},
)

@Composable
fun TodayContent(ui: TodayUi, refreshing: Boolean, skipping: Boolean, contentPadding: PaddingValues, actions: TodayActions) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = actions.refresh, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Header(ui, Modifier.staggeredAppear(0, "header"))

            if (!ui.partnerJoined) {
                PairingPendingCard(actions.showInvite, Modifier.staggeredAppear(1, "pending"))
                return@Column
            }

            if (ui.entity != null && ui.question == null && ui.entity.locked) {
                UnknownQuestionCard(actions.checkUpdate, Modifier.staggeredAppear(1, "unknown"))
                return@Column
            }
            milestoneOf(ui.together, ui.day)?.let { MilestoneCard(it, ui.together!!, Modifier.staggeredAppear(1, "milestone")) }
            QuestionCard(ui, Modifier.staggeredAppear(1, "question"))

            val motion = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = if (ui.phase == Phase.READY_TO_REVEAL || ui.phase == Phase.REVEALED) Phase.REVEALED else ui.phase,
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.slowSpatialSpec()) { it / 6 }) togetherWith
                        (fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { -it / 8 })
                },
                label = "phase",
                modifier = Modifier.staggeredAppear(2, "phase"),
            ) { phase ->
                when (phase) {
                    Phase.ANSWER -> AnswerSection(ui, skipping, onSubmit = actions.submit, onSkip = actions.skip)
                    Phase.WAITING -> WaitingSection(ui, actions.nudge)
                    else -> RevealSection(ui, onPlayed = actions.revealSeen, onReact = actions.react)
                }
            }

            ui.catchUp?.let { CatchUpCard(it, ui.partnerName) { actions.openDay(it.day) } }
            ui.recapMonth?.let { month ->
                RecapCard(month, onOpen = { actions.openRecap(month.toString()) }, onDismiss = { actions.dismissRecap(month.toString()) })
            }
            PermissionReminder(ui, actions.openPermissions, actions.dismissPermissionReminder)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(ui: TodayUi, modifier: Modifier) {
    val hour = LocalTime.now().hour
    val hello = when (hour) {
        in 5..11 -> "Bonjour"
        in 12..17 -> "Bel après-midi"
        else -> "Bonsoir"
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                ui.day.format(dateFormat).replaceFirstChar { it.titlecase(Locale.FRENCH) },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (ui.myName.isNotBlank()) "$hello ${ui.myName}" else hello,
                style = MaterialTheme.typography.headlineLarge,
            )
            daysTogether(ui.together, ui.day)?.takeIf { it > 0 }?.let { days ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text("💞 ", style = MaterialTheme.typography.labelLarge)
                    AnimatedCounter(days.toInt(), MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.tertiary)
                    Text(
                        if (days > 1) " jours ensemble" else " jour ensemble",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        StreakChip(ui.streak)
    }
}

@Composable
fun StreakChip(streak: Int, modifier: Modifier = Modifier) {
    val active = streak > 0
    val transition = rememberInfiniteTransition(label = "flame")
    val flicker by transition.animateFloat(1f, 1.12f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "f")
    Surface(
        shape = MaterialShapes.Cookie7Sided.toShape(),
        color = if (active) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (active) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(72.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("🔥", modifier = Modifier.graphicsLayer { if (active) { scaleX = flicker; scaleY = flicker } })
            AnimatedCounter(streak, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun QuestionCard(ui: TodayUi, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
    ) {
        Box {
            MorphingBlob(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 44.dp, y = (-44).dp)
                    .size(150.dp)
                    .graphicsLayer { alpha = 0.12f },
                color = colors.primary,
            )
            AnimatedContent(
                targetState = ui.entity?.questionId to ui.questionText,
                transitionSpec = {
                    (fadeIn(tween(300, 120)) + scaleIn(spring(dampingRatio = 0.7f), initialScale = 0.92f)) togetherWith fadeOut(tween(120))
                },
                label = "question",
            ) { (_, text) ->
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val pack = Pack.of(ui.entity?.pack)
                    val category = Category.of(ui.entity?.category)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryChip("${category.emoji} ${category.label}")
                        if (pack != null) CategoryChip("${pack.emoji} ${pack.label}", highlight = true)
                    }
                    if (text.isBlank()) {
                        LoadingIndicator(Modifier.size(48.dp))
                    } else {
                        Text(text, style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryChip(label: String, highlight: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (highlight) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (highlight) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun AnswerSection(ui: TodayUi, skipping: Boolean, onSubmit: (AnswerPayload) -> Unit, onSkip: () -> Unit) {
    var confirmSkip by remember { mutableStateOf(false) }
    val question: Question = ui.question ?: return
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AnimatedVisibility(ui.partnerAnswered, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✨", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Text("${ui.partnerName} a déjà répondu. À toi !", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        AnswerInput(question, ui.me, ui.myName, ui.partnerName, onSubmit)
        if (ui.canSkip) {
            TextButton(onClick = { confirmSkip = true }, enabled = !skipping, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (skipping) "Nouvelle question…" else "Passer cette question")
            }
        }
    }
    if (confirmSkip) {
        AlertDialog(
            onDismissRequest = { confirmSkip = false },
            icon = { Icon(Icons.Rounded.SkipNext, null) },
            title = { Text("Passer la question ?") },
            text = { Text("Tu as un joker par jour. La question changera aussi pour ${ui.partnerName}.") },
            confirmButton = { Button(onClick = { confirmSkip = false; onSkip() }, shapes = ButtonDefaults.shapes()) { Text("Passer") } },
            dismissButton = { TextButton(onClick = { confirmSkip = false }) { Text("Garder") } },
        )
    }
}

@Composable
private fun WaitingSection(ui: TodayUi, onNudge: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        ContainedLoadingIndicator(Modifier.size(84.dp))
        Text("En attente de la réponse de ${ui.partnerName}…", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            "Ta réponse est bien gardée 🔒. On te prévient dès que vos deux réponses sont prêtes.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        ui.myAnswer?.let { answer ->
            Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Ta réponse", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(answer.display(ui.question) { key -> if (key == ui.me.key) ui.myName.ifBlank { "Toi" } else ui.partnerName }, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        AnimatedVisibility(!ui.mySynced) {
            AssistChip(
                onClick = {},
                label = { Text("Envoi dès le retour du réseau") },
                leadingIcon = { Icon(Icons.Rounded.CloudOff, null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
        val haptics = rememberHaptics()
        AnimatedContent(ui.nudgeSent, label = "nudge") { sent ->
            if (sent) {
                Text("Coucou envoyé 💜", style = MaterialTheme.typography.titleSmall, color = colors.primary)
            } else {
                FilledTonalButton(onClick = { haptics.confirm(); onNudge() }, shapes = ButtonDefaults.shapes(), enabled = ui.mySynced) {
                    Text("Envoyer un coucou à ${ui.partnerName} 💜")
                }
            }
        }
    }
}

/** Palier du couple (100 jours, anniversaire…) : forme qui respire et petits cœurs. */
@Composable
private fun MilestoneCard(m: Milestone, since: LocalDate, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val burst = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(2600)) }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer, modifier = modifier.fillMaxWidth()) {
        Box {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    MorphingBlob(Modifier.fillMaxSize(), colors.tertiary.copy(alpha = 0.35f), periodMs = 2600)
                    Text(m.emoji, style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(m.title, style = MaterialTheme.typography.titleLarge)
                    Text("Depuis le ${since.format(longDate)} 💜", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (burst.value < 1f) HeartBurst(burst.value, listOf(colors.primary, colors.tertiary, colors.secondary), Modifier.matchParentSize())
        }
    }
}

/** Récap du mois précédent, proposé la première semaine du mois. */
@Composable
private fun RecapCard(month: YearMonth, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onOpen, shape = MaterialTheme.shapes.extraLarge, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Row(Modifier.padding(start = 18.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                MorphingBlob(Modifier.fillMaxSize(), colors.primary.copy(alpha = 0.3f), periodMs = 3200)
                Text("✨", style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                val name = month.format(monthName)
                Text("Votre mois ${if (name.first() in "aeiouâéo") "d'" else "de "}$name en miroir", style = MaterialTheme.typography.titleMedium)
                Text("Vos chiffres, vos réactions, un souvenir", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Plus tard") }
        }
    }
}

/** Rattrapage : la question d'hier reste ouverte jusqu'à ce soir minuit, pour garder la série. */
@Composable
private fun CatchUpCard(day: com.karelisio.miroir.data.local.DayEntity, partnerName: String, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onOpen, shape = MaterialTheme.shapes.extraLarge, color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⏳", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("La question d'hier t'attend", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (day.partnerAnswered) "$partnerName a répondu. Réponds avant minuit pour garder votre série 🔥"
                    else "Réponds avant minuit pour garder votre série 🔥",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun RevealSection(ui: TodayUi, onPlayed: () -> Unit, onReact: (String?, String?) -> Unit) {
    val mine = ui.myAnswer ?: return
    val theirs = ui.partnerAnswer ?: return
    var playing by rememberSaveable(ui.entity?.day) { mutableStateOf(false) }
    val haptics = rememberHaptics()

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (ui.phase == Phase.READY_TO_REVEAL && !playing) {
            RevealPrompt(onReveal = { haptics.confirm(); playing = true })
        } else {
            MirrorReveal(
                question = ui.question,
                myAnswer = mine,
                partnerAnswer = theirs,
                me = ui.me,
                myName = ui.myName,
                partnerName = ui.partnerName,
                play = playing,
                onPlayed = onPlayed,
            )
            AnimatedVisibility(ui.phase == Phase.REVEALED, enter = fadeIn(tween(400, 200)) + expandVertically()) {
                ReactionsSection(ui.myReaction, ui.partnerReaction, ui.partnerName, onReact)
            }
        }
    }
}

@Composable
private fun RevealPrompt(onReveal: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "p")
    val interaction = remember { MutableInteractionSource() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier
                .size(200.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .springPress(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onReveal),
            contentAlignment = Alignment.Center,
        ) {
            MorphingBlob(
                Modifier.fillMaxSize(),
                colors.primary,
                shapes = listOf(MaterialShapes.Cookie12Sided, MaterialShapes.Heart, MaterialShapes.Flower, MaterialShapes.Heart),
                periodMs = 2400,
                rotate = false,
            )
            Text("✨", style = MaterialTheme.typography.displayMedium)
        }
        Text("Vos deux réponses sont là", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Button(onClick = onReveal, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(56.dp)) {
            Text("Révéler le miroir", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun ReactionsSection(mine: ReactionPayload?, theirs: ReactionPayload?, partnerName: String, onReact: (String?, String?) -> Unit) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    var comment by rememberSaveable(mine?.at) { mutableStateOf(mine?.comment.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (theirs != null) {
            Surface(shape = MaterialTheme.shapes.large, color = colors.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(theirs.emoji ?: "💬", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("$partnerName a réagi", style = MaterialTheme.typography.labelLarge)
                        theirs.comment?.let { Text("« $it »", style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
        }
        Text("Ta réaction", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            REACTIONS.forEach { emoji ->
                val selected = mine?.emoji == emoji
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    if (selected) 1.2f else 1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium), label = "e",
                )
                Box(
                    Modifier
                        .size(40.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(if (selected) MaterialShapes.Cookie6Sided.toShape() else CircleShape)
                        .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh)
                        .clickable {
                            haptics.tick()
                            onReact(if (selected) null else emoji, mine?.comment)
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji) }
            }
        }
        OutlinedTextField(
            value = comment,
            onValueChange = { if (it.length <= 280) comment = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Un petit mot pour $partnerName…") },
            shape = MaterialTheme.shapes.large,
            singleLine = false,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            trailingIcon = {
                IconButton(
                    onClick = { haptics.confirm(); onReact(mine?.emoji, comment) },
                    enabled = comment.trim() != (mine?.comment ?: ""),
                ) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Envoyer") }
            },
        )
    }
}

@Composable
private fun UnknownQuestionCard(onUpdate: () -> Unit, modifier: Modifier) {
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🧩", style = MaterialTheme.typography.displaySmall)
            Text("Une nouvelle question vous attend", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                "Elle vient d'une version plus récente de Miroir. Mets l'appli à jour pour la découvrir.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onUpdate, shapes = ButtonDefaults.shapes()) { Text("Rechercher une mise à jour") }
        }
    }
}

@Composable
private fun PairingPendingCard(onShowInvite: () -> Unit, modifier: Modifier) {
    Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LoadingIndicator(Modifier.size(64.dp))
            Text("En attente de l'autre téléphone", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                "Fais scanner ton QR code depuis Miroir sur le téléphone de l'autre pour relier vos deux miroirs.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onShowInvite, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.QrCode2, null)
                Spacer(Modifier.width(8.dp))
                Text("Afficher le QR code")
            }
        }
    }
}

@Composable
private fun PermissionReminder(ui: TodayUi, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val missing = remember(ui.skippedPermissions) {
        AppPermission.all.filter { it.name in ui.skippedPermissions && !it.optional && !it.isGranted(context) }
    }
    val threeDays = 3L * 24 * 3600 * 1000
    val visible = missing.isNotEmpty() && System.currentTimeMillis() - ui.permissionReminderDismissedAt > threeDays
    AnimatedVisibility(visible, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, onClick = onOpen) {
            Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    "${missing.size} autorisation${if (missing.size > 1) "s" else ""} à revoir pour profiter de tout Miroir",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Plus tard") }
            }
        }
    }
}
