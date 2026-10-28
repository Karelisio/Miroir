package com.karelisio.miroir.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.karelisio.miroir.data.model.AnswerPayload
import com.karelisio.miroir.data.model.Member
import com.karelisio.miroir.data.model.Question
import com.karelisio.miroir.data.model.QuestionType
import com.karelisio.miroir.ui.components.ShapeAvatar
import com.karelisio.miroir.ui.components.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Révélation "effet miroir" : une ligne de lumière se trace, puis les deux réponses se déplient
 * de part et d'autre comme un reflet (charnière sur la ligne), avant un éclat de cœurs.
 */
@Composable
fun MirrorReveal(
    question: Question?,
    myAnswer: AnswerPayload,
    partnerAnswer: AnswerPayload,
    me: Member,
    myName: String,
    partnerName: String,
    play: Boolean,
    onPlayed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val line = remember { Animatable(if (play) 0f else 1f) }
    val open = remember { Animatable(if (play) 0f else 1f) }
    val content = remember { Animatable(if (play) 0f else 1f) }
    val badge = remember { Animatable(if (play) 0f else 1f) }
    val sheen = remember { Animatable(if (play) 0f else 1f) }
    val burst = remember { Animatable(if (play) 0f else 1f) }

    LaunchedEffect(play) {
        if (!play) return@LaunchedEffect
        haptics.press()
        line.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        haptics.confirm()
        launch { sheen.animateTo(1f, tween(1500, easing = LinearOutSlowInEasing)) }
        launch { burst.animateTo(1f, tween(2200, easing = LinearOutSlowInEasing)) }
        val unfold = launch { open.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 140f)) }
        delay(380)
        haptics.tick()
        content.animateTo(1f, tween(420))
        badge.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
        unfold.join()
        onPlayed()
    }

    val nameOf: (String) -> String = { key -> if (key == me.key) myName.ifBlank { "Toi" } else partnerName }
    val colors = MaterialTheme.colorScheme
    val verdict = matchVerdict(question, myAnswer, partnerAnswer, nameOf)

    Box(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            RevealCard(
                title = myName.ifBlank { "Toi" },
                answer = myAnswer,
                question = question,
                nameOf = nameOf,
                polygon = MaterialShapes.Cookie9Sided,
                container = colors.primaryContainer,
                onContainer = colors.onPrimaryContainer,
                accent = colors.primary,
                hingeAtBottom = true,
                open = open.value,
                contentAlpha = content.value,
                sheen = sheen.value,
            )
            MirrorLine(progress = line.value, glow = 1f - open.value.coerceIn(0f, 1f) * 0.6f)
            RevealCard(
                title = partnerName,
                answer = partnerAnswer,
                question = question,
                nameOf = nameOf,
                polygon = MaterialShapes.Flower,
                container = colors.secondaryContainer,
                onContainer = colors.onSecondaryContainer,
                accent = colors.secondary,
                hingeAtBottom = false,
                open = open.value,
                contentAlpha = content.value,
                sheen = sheen.value,
            )
            if (verdict != null) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = CircleShape,
                    color = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    modifier = Modifier.graphicsLayer {
                        val s = badge.value
                        scaleX = s; scaleY = s; alpha = s.coerceIn(0f, 1f)
                    },
                ) {
                    Text(verdict, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
            val guesses = listOfNotNull(
                myAnswer.guess?.let { g -> g.matches(partnerAnswer)?.let { Triple(myName.ifBlank { "Toi" }, g, it) } },
                partnerAnswer.guess?.let { g -> g.matches(myAnswer)?.let { Triple(partnerName, g, it) } },
            )
            if (guesses.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().graphicsLayer {
                        val s = badge.value
                        alpha = s.coerceIn(0f, 1f); translationY = (1f - s) * 24f * density
                    },
                ) {
                    guesses.forEach { (name, g, right) -> GuessResult(name, g.display(question, nameOf), right) }
                }
            }
        }
        if (play && burst.value < 1f) HeartBurst(burst.value, listOf(colors.primary, colors.tertiary, colors.secondary), Modifier.matchParentSize())
    }
}

@Composable
private fun GuessResult(name: String, guess: String, right: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (right) colors.tertiaryContainer else colors.surfaceContainerHigh,
        contentColor = if (right) colors.onTertiaryContainer else colors.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🔮", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Text("$name avait deviné « $guess »", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(if (right) "Bien vu ✓" else "Raté", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MirrorLine(progress: Float, glow: Float) {
    val colors = MaterialTheme.colorScheme
    val brush = Brush.horizontalGradient(listOf(Color.Transparent, colors.primary, colors.tertiary, colors.secondary, Color.Transparent))
    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(10.dp)
                .blur(10.dp)
                .graphicsLayer { alpha = glow * progress }
                .background(brush, CircleShape),
        )
        Box(Modifier.fillMaxWidth(progress).height(2.dp).background(brush, CircleShape))
        Box(
            Modifier
                .size(14.dp)
                .graphicsLayer { scaleX = progress; scaleY = progress; rotationZ = 45f * progress }
                .clip(MaterialShapes.Diamond.toShape())
                .background(colors.primary),
        )
    }
}

@Composable
private fun RevealCard(
    title: String,
    answer: AnswerPayload,
    question: Question?,
    nameOf: (String) -> String,
    polygon: RoundedPolygon,
    container: Color,
    onContainer: Color,
    accent: Color,
    hingeAtBottom: Boolean,
    open: Float,
    contentAlpha: Float,
    sheen: Float,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = onContainer,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, if (hingeAtBottom) 1f else 0f)
                cameraDistance = 14f * density
                val angle = (1f - open) * 88f
                rotationX = if (hingeAtBottom) angle else -angle
                alpha = (open * 1.6f).coerceIn(0f, 1f)
                val s = 0.92f + 0.08f * open.coerceAtMost(1f)
                scaleX = s
            },
    ) {
        Box(
            Modifier.drawWithContent {
                drawContent()
                if (sheen in 0.001f..0.999f) {
                    val x = -size.width + sheen * size.width * 3f
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                            start = Offset(x, 0f),
                            end = Offset(x + size.width * 0.45f, size.height),
                        ),
                    )
                }
            },
        ) {
            Column(Modifier.padding(20.dp).graphicsLayer { alpha = contentAlpha }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShapeAvatar(title, polygon, accent, container, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall, color = onContainer.copy(alpha = 0.8f))
                }
                Spacer(Modifier.height(12.dp))
                AnswerContent(answer, question, nameOf, accent)
            }
        }
    }
}

@Composable
private fun AnswerContent(answer: AnswerPayload, question: Question?, nameOf: (String) -> String, accent: Color) {
    when (answer.type) {
        QuestionType.SCALE -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${answer.scale ?: "?"}", style = MaterialTheme.typography.displayMedium, color = accent)
            Spacer(Modifier.width(16.dp))
            ScaleDots(answer.scale ?: 0, accent, Modifier.weight(1f))
        }
        QuestionType.WHO -> {
            val name = answer.who?.let(nameOf) ?: "—"
            Text(name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        else -> Text(
            answer.display(question, nameOf),
            style = if (answer.type == QuestionType.TEXT && (answer.text?.length ?: 0) > 120) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun ScaleDots(value: Int, accent: Color, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..10) {
            Box(
                Modifier
                    .weight(1f)
                    .height(if (i == value) 14.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (i <= value) accent else accent.copy(alpha = 0.18f)),
            )
        }
    }
}

/** Petits cœurs (MaterialShapes.Heart) qui s'élèvent et s'évanouissent. */
@Composable
internal fun HeartBurst(progress: Float, palette: List<Color>, modifier: Modifier) {
    val heart = MaterialShapes.Heart.toPath()
    val particles = remember {
        val r = Random(7)
        List(16) { Triple(r.nextFloat(), 0.35f + r.nextFloat() * 0.65f, r.nextFloat()) }
    }
    val scratch = remember { Path() }
    val matrix = remember { Matrix() }
    Canvas(modifier) {
        particles.forEachIndexed { i, (x, speed, phase) ->
            val t = (progress * 1.2f - phase * 0.25f).coerceIn(0f, 1f)
            if (t <= 0f) return@forEachIndexed
            val sizePx = (10 + 14 * speed) * density
            val cx = size.width * (0.1f + 0.8f * x) + sin((t * 2 + phase) * PI.toFloat()) * 18 * density
            val cy = size.height * 0.55f - t * size.height * 0.6f * speed
            scratch.reset()
            scratch.addPath(heart)
            matrix.reset()
            matrix.scale(sizePx, sizePx)
            scratch.transform(matrix)
            translate(cx - sizePx / 2, cy - sizePx / 2) {
                drawPath(scratch, palette[i % palette.size].copy(alpha = (1f - t) * 0.85f))
            }
        }
    }
}
