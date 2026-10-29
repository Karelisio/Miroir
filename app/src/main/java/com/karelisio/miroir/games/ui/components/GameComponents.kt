package com.karelisio.miroir.games.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.miroir.games.ui.theme.LocalReduceMotion
import com.karelisio.miroir.ui.components.rememberHaptics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Piments d'intensité : apparaissent un à un en ressort et frémissent doucement. */
@Composable
fun Peppers(intensity: Int, modifier: Modifier = Modifier, size: Dp = 18.dp, max: Int = 3, animated: Boolean = true) {
    val reduce = LocalReduceMotion.current
    val wobble = if (animated && !reduce) {
        val t = rememberInfiniteTransition(label = "pepper")
        t.animateFloat(-8f, 8f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "w").value
    } else 0f
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..max) {
            val on = i <= intensity
            val scale by animateFloatAsState(
                if (on) 1f else 0.78f,
                spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium),
                label = "p$i",
            )
            Text(
                "🌶",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * (size.value / 18f)),
                modifier = Modifier.graphicsLayer {
                    scaleX = scale; scaleY = scale
                    alpha = if (on) 1f else 0.25f
                    rotationZ = if (on) wobble * (if (i % 2 == 0) -1 else 1) else 0f
                },
            )
        }
    }
}

val INTENSITY_LABELS = listOf("Doux", "Coquin", "Brûlant")

/** Curseur de piments en trois crans, avec haptique à chaque cran. */
@Composable
fun IntensityPicker(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (level in 1..3) {
            val selected = level == value
            val scale by animateFloatAsState(if (selected) 1.04f else 1f, spring(dampingRatio = 0.5f), label = "lvl$level")
            Surface(
                onClick = { if (!selected) { haptics.tick(); onChange(level) } },
                shape = if (selected) MaterialShapes.Cookie9Sided.toShape() else MaterialTheme.shapes.large,
                color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
                contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
                border = if (selected) BorderStroke(2.dp, colors.primary) else null,
                modifier = Modifier.weight(1f).heightIn(min = 96.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            ) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Peppers(level, max = level, animated = selected)
                    Spacer(Modifier.height(6.dp))
                    Text(INTENSITY_LABELS[level - 1], style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * Carte qui se retourne comme un reflet : le dos (motif miroir) d'abord, puis la face avec la question.
 * Sans animation si l'utilisateur a réduit les animations.
 */
@Composable
fun FlipCard(
    flipped: Boolean,
    modifier: Modifier = Modifier,
    back: @Composable () -> Unit = { MirrorBack() },
    front: @Composable () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = if (reduce) tween(0) else spring(dampingRatio = 0.72f, stiffness = 180f),
        label = "flip",
        finishedListener = { if (flipped) haptics.tick() },
    )
    Box(
        modifier.graphicsLayer {
            rotationY = rotation
            cameraDistance = 16f * density
        },
    ) {
        if (rotation <= 90f) {
            back()
        } else {
            Box(Modifier.graphicsLayer { rotationY = 180f }) { front() }
        }
    }
}

/** Dos de carte : surface argentée qui reflète la palette, avec deux bulles en miroir. */
@Composable
fun MirrorBack(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer, colors.secondaryContainer))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp, 40.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(colors.primary.copy(alpha = 0.85f)))
            Box(Modifier.width(140.dp).height(2.dp).background(colors.onPrimaryContainer.copy(alpha = 0.3f)))
            Box(Modifier.size(64.dp, 40.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(colors.primary.copy(alpha = 0.35f)))
        }
    }
}

/**
 * Révélation des deux réponses : elles glissent l'une vers l'autre ; si elles concordent, elles
 * fusionnent en reflet, sinon elles se repoussent en ressort.
 */
@Composable
fun RevealPair(
    leftName: String,
    left: String,
    rightName: String,
    right: String,
    match: Boolean?,
    modifier: Modifier = Modifier,
    play: Boolean = true,
    onPlayed: () -> Unit = {},
) {
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val approach = remember { Animatable(if (play && !reduce) 0f else 1f) }
    val merge = remember { Animatable(if (play && !reduce) 0f else 1f) }
    LaunchedEffect(play) {
        if (!play || reduce) { onPlayed(); return@LaunchedEffect }
        approach.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        haptics.confirm()
        merge.animateTo(1f, spring(dampingRatio = if (match == true) 0.7f else 0.3f, stiffness = 220f))
        onPlayed()
    }
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = constraints.maxWidth.toFloat()
        val half = w / 2f
        // Départ hors du centre, puis rapprochement ; fusion (match) ou choc puis rebond vers les bords.
        // Au repos, les bulles restent dans l'écran (pas de texte coupé).
        val m = merge.value
        val inward = when (match) {
            true -> half * 0.5f * m
            else -> w * 0.06f * (1f - m)
        }
        val enter = 1f - approach.value
        val fade = if (match == true) (1f - m).coerceIn(0f, 1f) else 1f
        Box(Modifier.fillMaxWidth().heightIn(min = 150.dp), contentAlignment = Alignment.Center) {
            AnswerBubble(
                leftName, left, colors.primaryContainer, colors.onPrimaryContainer,
                Modifier.fillMaxWidth(0.48f).align(Alignment.CenterStart).graphicsLayer {
                    translationX = -half * enter + inward
                    alpha = fade
                    rotationZ = if (match == false) -4f * m else 0f
                },
            )
            AnswerBubble(
                rightName, right, colors.secondaryContainer, colors.onSecondaryContainer,
                Modifier.fillMaxWidth(0.48f).align(Alignment.CenterEnd).graphicsLayer {
                    translationX = half * enter - inward
                    alpha = fade
                    rotationZ = if (match == false) 4f * m else 0f
                },
            )
            if (match == true) {
                // Le reflet fusionné : une seule bulle, à la forme de cœur ondulé.
                Surface(
                    shape = MaterialShapes.Cookie12Sided.toShape(),
                    color = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    modifier = Modifier.size(150.dp).graphicsLayer {
                        val s = merge.value
                        scaleX = 0.4f + 0.6f * s; scaleY = 0.4f + 0.6f * s; alpha = s.coerceIn(0f, 1f)
                    },
                ) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("Pareil !", style = MaterialTheme.typography.labelLarge)
                        Text(left, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 3)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerBubble(name: String, value: String, container: Color, content: Color, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = container, contentColor = content, modifier = modifier.padding(4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(name, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.75f), maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 5)
        }
    }
}

/** Confettis discrets (petits losanges et cœurs) pour un match. */
@Composable
fun Confetti(trigger: Any?, modifier: Modifier = Modifier, count: Int = 26) {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == null || reduce) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1800, easing = LinearOutSlowInEasing))
    }
    if (progress.value >= 1f) return
    val colors = MaterialTheme.colorScheme
    val palette = listOf(colors.primary, colors.tertiary, colors.secondary, colors.primaryContainer)
    val particles = remember(trigger) {
        val r = Random(trigger.hashCode())
        List(count) { Triple(r.nextFloat() * 2f * PI.toFloat(), 0.4f + r.nextFloat() * 0.6f, r.nextFloat() * 360f) }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        val center = Offset(size.width / 2f, size.height * 0.45f)
        particles.forEachIndexed { i, (angle, speed, spin) ->
            val dist = t * speed * size.minDimension * 0.55f
            val p = center + Offset(cos(angle) * dist, sin(angle) * dist + t * t * size.height * 0.25f)
            rotate(spin + t * 540f, p) {
                val piece = Size(8.dp.toPx(), 5.dp.toPx())
                drawRect(palette[i % palette.size].copy(alpha = (1f - t) * 0.9f), p - Offset(piece.width / 2, piece.height / 2), piece)
            }
        }
    }
}

/** Pastille d'état d'une partie : à toi, en attente de l'autre, révélation prête… */
@Composable
fun StatusChip(label: String, emphasis: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val pulse = if (emphasis && !reduce) {
        rememberInfiniteTransition(label = "chip").animateFloat(1f, 1.06f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "c").value
    } else 1f
    Surface(
        shape = CircleShape,
        color = if (emphasis) colors.primary else colors.surfaceContainerHighest,
        contentColor = if (emphasis) colors.onPrimary else colors.onSurfaceVariant,
        modifier = modifier.graphicsLayer { scaleX = pulse; scaleY = pulse },
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}
