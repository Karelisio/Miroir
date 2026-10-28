package com.karelisio.miroir.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Retours haptiques subtils, désactivables dans les réglages. */
val LocalHapticsEnabled = compositionLocalOf { true }

class Haptics(private val feedback: HapticFeedback, private val enabled: Boolean) {
    fun tick() = perform(HapticFeedbackType.SegmentTick)
    fun toggle(on: Boolean) = perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    fun confirm() = perform(HapticFeedbackType.Confirm)
    fun reject() = perform(HapticFeedbackType.Reject)
    fun press() = perform(HapticFeedbackType.ContextClick)
    fun heavy() = perform(HapticFeedbackType.LongPress)
    private fun perform(type: HapticFeedbackType) { if (enabled) feedback.performHapticFeedback(type) }
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(feedback, enabled) { Haptics(feedback, enabled) }
}

/** Effet ressort à l'appui : légère compression puis rebond. */
fun Modifier.springPress(interactionSource: InteractionSource, pressedScale: Float = 0.94f): Modifier = this.then(
    Modifier.graphicsLayerPress(interactionSource, pressedScale),
)

@Composable
private fun pressScale(interactionSource: InteractionSource, pressedScale: Float): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    return scale
}

private fun Modifier.graphicsLayerPress(interactionSource: InteractionSource, pressedScale: Float): Modifier =
    composed {
        val scale = pressScale(interactionSource, pressedScale)
        graphicsLayer { scaleX = scale; scaleY = scale }
    }

/**
 * Apparition en cascade : chaque élément glisse et apparaît avec un léger décalage selon son index.
 * Ne joue qu'une fois par élément (survit au défilement grâce à rememberSaveable).
 */
fun Modifier.staggeredAppear(index: Int, key: Any? = index, baseDelayMs: Int = 45): Modifier = composed {
    var played by rememberSaveable(key) { mutableStateOf(false) }
    val progress = remember(key) { Animatable(if (played) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(key) {
        if (!played) {
            delay((index.coerceAtMost(12) * baseDelayMs).toLong())
            progress.animateTo(1f, spec)
            played = true
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 48.dp.toPx()
        scaleX = 0.96f + 0.04f * p
        scaleY = 0.96f + 0.04f * p
    }
}

/** Compteur animé : chaque chiffre défile verticalement quand la valeur change. */
@Composable
fun AnimatedCounter(value: Int, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val text = value.toString()
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { if (up) -it else it } + fadeOut())
                },
                label = "digit$i",
            ) { c -> Text(c.toString(), style = style, color = color) }
        }
    }
}

/** Forme M3 Expressive qui se transforme en continu d'un polygone à l'autre. */
@Composable
fun MorphingBlob(
    modifier: Modifier,
    color: Color,
    shapes: List<RoundedPolygon> = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Flower, MaterialShapes.SoftBurst, MaterialShapes.Puffy),
    periodMs: Int = 9000,
    rotate: Boolean = true,
) {
    val morphs = remember(shapes) { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    val transition = rememberInfiniteTransition(label = "blob")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = shapes.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(periodMs * shapes.size / 2, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val rotation by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(periodMs * 4, easing = LinearEasing)), label = "rot",
    )
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    Canvas(modifier.graphicsLayer { if (rotate) rotationZ = rotation }) {
        val index = t.toInt().coerceIn(0, morphs.lastIndex)
        val local = t - index
        val eased = (1 - kotlin.math.cos(local * Math.PI).toFloat()) / 2f
        path.rewind()
        val p = morphs[index].toPath(eased, path)
        matrix.reset()
        matrix.scale(size.width, size.height)
        p.transform(matrix)
        drawPath(p, color)
    }
}

/** Avatar : initiale dans une forme M3 Expressive. */
@Composable
fun ShapeAvatar(name: String, polygon: RoundedPolygon, container: Color, content: Color, size: Dp = 40.dp) {
    Box(
        modifier = Modifier.size(size).clip(polygon.toShape()).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            color = content,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

/** Petit utilitaire : lance un effet suspendu sans bloquer la composition. */
@Composable
fun rememberLauncher(): (suspend () -> Unit) -> Unit {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember(scope) { { block -> scope.launch { block() } } }
}
