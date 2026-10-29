package com.karelisio.miroir.games.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.miroir.games.model.GameItem
import com.karelisio.miroir.games.model.GameValue
import com.karelisio.miroir.games.model.ItemFormat
import com.karelisio.miroir.games.ui.theme.GamesTheme
import com.karelisio.miroir.ui.components.AnimatedCounter
import com.karelisio.miroir.ui.components.miroirSafe
import com.karelisio.miroir.ui.components.miroirTopBar
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.springPress
import kotlin.math.roundToInt

/** Fond de la section : surface chaude, avec un halo du conteneur primaire. */
@Composable
fun Modifier.gamesBackground(): Modifier {
    val colors = MaterialTheme.colorScheme
    return background(Brush.verticalGradient(listOf(colors.surface, colors.primaryContainer.copy(alpha = 0.28f), colors.surface)))
}

/** Écran plein de la section : palette chaude, FLAG_SECURE, barre du haut transparente. */
@Composable
fun GameFrame(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    GamesTheme {
        SecureScreen()
        Scaffold(
            contentWindowInsets = WindowInsets.miroirSafe,
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { snackbar?.let { SnackbarHost(it) } },
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets.miroirTopBar,
                    title = { Text(title, maxLines = 1) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().gamesBackground()) { content(padding) }
        }
    }
}

@Composable
fun CategoryPill(item: GameItem, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = CircleShape, color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
            Text(
                "${item.category.emoji} ${item.category.label}" + if (item.custom) " · perso" else "",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Peppers(item.intensity, size = 14.dp)
    }
}

/** Face d'une carte de question : catégorie, piments, texte en grand. */
@Composable
fun QuestionFace(item: GameItem, other: String, modifier: Modifier = Modifier, header: String? = null) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = 220.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CategoryPill(item)
            header?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = colors.primary) }
            Text(item.render(other), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
        }
    }
}

/** Saisie d'une réponse selon le format de la question (choix, échelle, texte). */
@Composable
fun ValueInput(item: GameItem, value: GameValue?, onValue: (GameValue?) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Ta réponse…") {
    val haptics = rememberHaptics()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (item.format) {
            ItemFormat.CHOICE -> item.options.forEachIndexed { i, option ->
                OptionCard(option, selected = value?.choice == i) { haptics.tick(); onValue(GameValue(choice = i)) }
            }
            ItemFormat.SCALE -> ScaleInput(value?.scale, item.min ?: "1", item.max ?: "10") { v ->
                if (v != value?.scale) haptics.tick()
                onValue(GameValue(scale = v))
            }
            else -> OutlinedTextField(
                value = value?.text.orEmpty(),
                onValueChange = { if (it.length <= 300) onValue(GameValue(text = it)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                placeholder = { Text(placeholder) },
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }
}

fun GameValue?.isComplete(): Boolean = this != null && (choice != null || scale != null || !text.isNullOrBlank())

@Composable
fun OptionCard(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerHigh, label = "c")
    val radius by animateDpAsState(if (selected) 30.dp else 18.dp, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "r")
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(radius),
        color = container,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .springPress(interaction, 0.97f)
            .selectable(selected = selected, interactionSource = interaction, indication = ripple(), onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun ScaleInput(value: Int?, minLabel: String, maxLabel: String, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        AnimatedCounter(value ?: 5, MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary.copy(alpha = if (value == null) 0.4f else 1f))
        val slider = remember { SliderState((value ?: 5).toFloat(), 8, 1f..10f) }
        Slider(state = slider, onValueChange = { v -> slider.value = v; onChange(v.roundToInt()) }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth()) {
            Text(minLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(maxLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

/** Grille de choix compacte pour les écrans de mode (lancer une partie, choisir action ou vérité). */
@Composable
fun ModeTile(emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier, highlight: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (highlight) colors.primaryContainer else colors.surfaceContainer,
        contentColor = if (highlight) colors.onPrimaryContainer else colors.onSurface,
        modifier = modifier.springPress(interaction, 0.96f),
    ) {
        Column(Modifier.padding(16.dp).heightIn(min = 104.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(shape = MaterialShapes.Cookie7Sided.toShape(), color = colors.primary.copy(alpha = 0.14f)) {
                Text(emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(10.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = (if (highlight) colors.onPrimaryContainer else colors.onSurfaceVariant).copy(alpha = 0.85f), maxLines = 2)
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}
