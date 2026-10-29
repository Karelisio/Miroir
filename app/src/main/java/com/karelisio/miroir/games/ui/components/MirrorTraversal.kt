package com.karelisio.miroir.games.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.MaterialTheme
import com.karelisio.miroir.games.ui.theme.GamesTheme
import com.karelisio.miroir.games.ui.theme.rememberReduceMotion
import com.karelisio.miroir.ui.components.rememberHaptics
import kotlin.math.max

// Onde de reflet (AGSL, Android 13+) : le contenu ondule depuis le centre, puis se stabilise.
private const val RIPPLE = """
uniform shader content;
uniform float2 size;
uniform float time;
uniform float amp;
half4 main(float2 p) {
    float2 c = size * 0.5;
    float d = distance(p, c);
    float reach = max(size.x, size.y);
    float wave = sin(d * 0.045 - time * 10.0) * amp * (1.0 - smoothstep(0.0, reach, d));
    float2 dir = (p - c) / max(d, 1.0);
    return content.eval(p + dir * wave);
}
"""

/**
 * Traversée du miroir à l'entrée de la section : la surface ondule comme un reflet qu'on touche,
 * des cercles de lumière s'élargissent, et la palette glisse vers les tons chauds de la section.
 * Respecte « Supprimer les animations » : tout est alors immédiat.
 */
@Composable
fun MirrorTraversal(content: @Composable () -> Unit) {
    val reduce = rememberReduceMotion()
    val haptics = rememberHaptics()
    val t = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        haptics.press()
        t.animateTo(1f, tween(1150, easing = FastOutSlowInEasing))
        haptics.tick()
    }
    val p = t.value
    // Palette : de Miroir vers la section, pendant la deuxième moitié.
    GamesTheme(progress = ((p - 0.25f) / 0.6f).coerceIn(0f, 1f)) {
        val colors = MaterialTheme.colorScheme
        Box(Modifier.fillMaxSize()) {
            val ripple = if (Build.VERSION.SDK_INT >= 33 && p < 1f) rippleModifier(p) else Modifier
            Box(Modifier.fillMaxSize().then(ripple).graphicsLayer { alpha = ((p - 0.15f) / 0.45f).coerceIn(0f, 1f) }) { content() }
            if (p < 1f) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val reach = max(size.width, size.height)
                    // Voile du miroir qui s'efface.
                    drawRect(
                        Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.35f), Color.White.copy(alpha = 0.18f), colors.tertiary.copy(alpha = 0.3f))),
                        alpha = (1f - p / 0.55f).coerceIn(0f, 1f),
                    )
                    // Cercles de lumière qui s'élargissent.
                    for (i in 0 until 4) {
                        val local = (p * 1.4f - i * 0.12f).coerceIn(0f, 1f)
                        if (local <= 0f || local >= 1f) continue
                        drawCircle(
                            color = (if (i % 2 == 0) colors.primary else colors.tertiary).copy(alpha = (1f - local) * 0.5f),
                            radius = reach * 0.75f * local,
                            center = center,
                            style = Stroke(width = (18f - i * 3f) * density * (1f - local) + 1f),
                        )
                    }
                    // Lueur chaude au centre.
                    drawCircle(
                        Brush.radialGradient(listOf(colors.primary.copy(alpha = 0.45f * (1f - p)), Color.Transparent), center, reach * 0.6f * (0.3f + p)),
                        radius = reach * 0.6f * (0.3f + p),
                        center = center,
                    )
                }
            }
        }
    }
}

@RequiresApi(33)
@Composable
private fun rippleModifier(p: Float): Modifier {
    val shader = remember { RuntimeShader(RIPPLE) }
    return Modifier.graphicsLayer {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("time", p * 2.5f)
        shader.setFloatUniform("amp", 26f * density / 2.6f * (1f - p))
        renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}
