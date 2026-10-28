package com.karelisio.miroir.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.miroir.data.model.AnswerPayload
import com.karelisio.miroir.data.model.Guess
import com.karelisio.miroir.data.model.Member
import com.karelisio.miroir.data.model.Question
import com.karelisio.miroir.data.model.QuestionType
import com.karelisio.miroir.ui.components.AnimatedCounter
import com.karelisio.miroir.ui.components.ShapeAvatar
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.springPress
import kotlin.math.roundToInt

private const val MAX_TEXT = 500

/** Zone de réponse adaptée au format de la question. */
@Composable
fun AnswerInput(question: Question, me: Member, myName: String, partnerName: String, onSubmit: (AnswerPayload) -> Unit) {
    val haptics = rememberHaptics()
    var text by rememberSaveable(question.id) { mutableStateOf("") }
    var choice by rememberSaveable(question.id) { mutableIntStateOf(-1) }
    var scale by rememberSaveable(question.id) { mutableFloatStateOf(5f) }
    var scaleTouched by rememberSaveable(question.id) { mutableStateOf(false) }
    var who by rememberSaveable(question.id) { mutableStateOf<String?>(null) }
    // Pronostic facultatif sur la réponse de l'autre.
    var guessChoice by rememberSaveable(question.id) { mutableIntStateOf(-1) }
    var guessScale by rememberSaveable(question.id) { mutableIntStateOf(0) }
    var guessWho by rememberSaveable(question.id) { mutableStateOf<String?>(null) }
    val guess = when (question.type) {
        QuestionType.CHOICE -> guessChoice.takeIf { it >= 0 }?.let { Guess(choice = it) }
        QuestionType.SCALE -> guessScale.takeIf { it > 0 }?.let { Guess(scale = it) }
        QuestionType.WHO -> guessWho?.let { Guess(who = it) }
        QuestionType.TEXT -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (question.type) {
            QuestionType.TEXT -> OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= MAX_TEXT) text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                placeholder = { Text("Écris ta réponse…") },
                supportingText = { Text("${text.length} / $MAX_TEXT", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            QuestionType.CHOICE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                question.options.forEachIndexed { i, option ->
                    ChoiceOption(option, selected = choice == i) {
                        if (choice != i) haptics.tick()
                        choice = i
                    }
                }
            }

            QuestionType.SCALE -> ScaleInput(
                value = scale,
                minLabel = question.minLabel ?: "1",
                maxLabel = question.maxLabel ?: "10",
                onChange = { v ->
                    if (v.roundToInt() != scale.roundToInt()) haptics.tick()
                    scale = v
                    scaleTouched = true
                },
            )

            QuestionType.WHO -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WhoOption("Moi", myName, selected = who == me.key, polygon = MaterialShapes.Cookie9Sided, modifier = Modifier.weight(1f)) {
                    haptics.tick(); who = me.key
                }
                WhoOption(partnerName, partnerName, selected = who == me.other.key, polygon = MaterialShapes.Flower, modifier = Modifier.weight(1f)) {
                    haptics.tick(); who = me.other.key
                }
            }
        }

        val ready = when (question.type) {
            QuestionType.TEXT -> text.isNotBlank()
            QuestionType.CHOICE -> choice >= 0
            QuestionType.SCALE -> scaleTouched
            QuestionType.WHO -> who != null
        }
        AnimatedVisibility(ready && question.type != QuestionType.TEXT, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            GuessSection(question, me, myName, partnerName, guess) { g ->
                haptics.tick()
                guessChoice = g?.choice ?: -1
                guessScale = g?.scale ?: 0
                guessWho = g?.who
            }
        }
        val interaction = remember { MutableInteractionSource() }
        Button(
            onClick = {
                haptics.confirm()
                onSubmit(
                    AnswerPayload(
                        questionId = question.id,
                        type = question.type,
                        text = text.trim().takeIf { question.type == QuestionType.TEXT },
                        choice = choice.takeIf { question.type == QuestionType.CHOICE },
                        scale = scale.roundToInt().takeIf { question.type == QuestionType.SCALE },
                        who = who.takeIf { question.type == QuestionType.WHO },
                        at = System.currentTimeMillis(),
                        guess = guess,
                    ),
                )
            },
            enabled = ready,
            interactionSource = interaction,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth().height(56.dp).springPress(interaction),
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Envoyer ma réponse", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Ta réponse reste cachée jusqu'à ce que $partnerName ait répondu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** « Et selon toi ? » : on devine la réponse de l'autre, révélée en même temps que les vôtres. */
@Composable
private fun GuessSection(question: Question, me: Member, myName: String, partnerName: String, guess: Guess?, onGuess: (Guess?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔮", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (question.type == QuestionType.WHO) "Et selon toi, qui $partnerName va désigner ?" else "Et selon toi, $partnerName va répondre…",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text("Facultatif · dévoilé avec vos réponses", style = MaterialTheme.typography.labelSmall)
                }
            }
            when (question.type) {
                QuestionType.CHOICE -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    question.options.forEachIndexed { i, option ->
                        val selected = guess?.choice == i
                        FilterChip(selected = selected, onClick = { onGuess(if (selected) null else Guess(choice = i)) }, label = { Text(option, maxLines = 2) })
                    }
                }
                QuestionType.WHO -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(me.key to myName.ifBlank { "Moi" }, me.other.key to partnerName).forEach { (key, name) ->
                        val selected = guess?.who == key
                        FilterChip(selected = selected, onClick = { onGuess(if (selected) null else Guess(who = key)) }, label = { Text(name, maxLines = 1) })
                    }
                }
                QuestionType.SCALE -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    for (n in 1..10) {
                        val selected = guess?.scale == n
                        val scale by animateFloatAsState(if (selected) 1.15f else 1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium), label = "g")
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(if (selected) MaterialShapes.Cookie6Sided.toShape() else CircleShape)
                                .background(if (selected) colors.tertiary else colors.surfaceContainerLowest.copy(alpha = 0.6f))
                                .clickable { onGuess(if (selected) null else Guess(scale = n)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$n", style = MaterialTheme.typography.labelLarge, color = if (selected) colors.onTertiary else colors.onSurface)
                        }
                    }
                }
                QuestionType.TEXT -> Unit
            }
        }
    }
}

/** Texte d'un pronostic, pour l'affichage après révélation. */
fun Guess.display(question: Question?, nameOf: (String) -> String): String = when {
    who != null -> nameOf(who)
    choice != null -> question?.options?.getOrNull(choice) ?: "—"
    scale != null -> "$scale / 10"
    else -> "—"
}

@Composable
private fun ChoiceOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerHigh, label = "c")
    val content by animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onSurface, label = "t")
    val radius by animateDpAsState(if (selected) 32.dp else 18.dp, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "r")
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(radius),
        color = container,
        contentColor = content,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .springPress(interaction, 0.97f)
            .selectable(selected = selected, interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
            AnimatedVisibility(selected, enter = scaleIn(spring(dampingRatio = 0.4f)), exit = scaleOut()) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.primary)
            }
        }
    }
}

@Composable
private fun ScaleInput(value: Float, minLabel: String, maxLabel: String, onChange: (Float) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        val bounce by animateFloatAsState(1f + (value - 5f) * 0.02f, spring(dampingRatio = 0.5f), label = "b")
        Box(Modifier.graphicsLayer { scaleX = bounce; scaleY = bounce }) {
            AnimatedCounter(
                value = value.roundToInt(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val slider = remember { SliderState(value, 8, 1f..10f) }
        Slider(
            state = slider,
            onValueChange = { v -> slider.value = v; onChange(v) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(minLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(maxLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun WhoOption(
    title: String,
    name: String,
    selected: Boolean,
    polygon: androidx.graphics.shapes.RoundedPolygon,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerHigh, label = "c")
    val avatarScale by animateFloatAsState(if (selected) 1.15f else 1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow), label = "s")
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = if (selected) MaterialShapes.Cookie12Sided.toShape() else MaterialTheme.shapes.extraLarge,
        color = container,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
        modifier = modifier
            .height(150.dp)
            .springPress(interaction)
            .selectable(selected = selected, interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(12.dp)) {
            Box(Modifier.graphicsLayer { scaleX = avatarScale; scaleY = avatarScale }) {
                ShapeAvatar(name, polygon, if (selected) colors.primary else colors.secondaryContainer, if (selected) colors.onPrimary else colors.onSecondaryContainer, 52.dp)
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    }
}

/** Rend une réponse lisible ("qui de nous deux" en noms absolus). */
fun AnswerPayload.display(question: Question?, nameOf: (String) -> String): String = when (type) {
    QuestionType.TEXT -> text.orEmpty()
    QuestionType.CHOICE -> question?.options?.getOrNull(choice ?: -1) ?: "—"
    QuestionType.SCALE -> "${scale ?: "?"} / 10"
    QuestionType.WHO -> who?.let(nameOf) ?: "—"
}

/** Petit verdict après la révélation, selon le format. */
fun matchVerdict(question: Question?, a: AnswerPayload, b: AnswerPayload, nameOf: (String) -> String): String? = when (a.type) {
    QuestionType.CHOICE -> if (a.choice == b.choice) "Même réponse ! 💜" else "Deux avis différents 👀"
    QuestionType.WHO -> if (a.who == b.who && a.who != null) "D'accord : c'est ${nameOf(a.who)} !" else "Vous n'êtes pas d'accord 😄"
    QuestionType.SCALE -> {
        val gap = kotlin.math.abs((a.scale ?: 0) - (b.scale ?: 0))
        when (gap) {
            0 -> "Parfaitement synchro ✨"
            1, 2 -> "Presque pareil (écart de $gap)"
            else -> "Écart de $gap : parlez-en !"
        }
    }
    QuestionType.TEXT -> if (a.text?.trim().equals(b.text?.trim(), ignoreCase = true)) "Mot pour mot ! 🤯" else null
}.takeIf { question != null }
