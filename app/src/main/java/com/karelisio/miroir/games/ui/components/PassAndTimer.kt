package com.karelisio.miroir.games.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.miroir.games.ui.theme.LocalReduceMotion
import com.karelisio.miroir.ui.components.MorphingBlob
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.components.staggeredAppear

/** Écran de passage : le téléphone change de mains, rien n'est visible tant que l'autre n'a pas confirmé. */
@Composable
fun PassScreen(name: String, hint: String = "Sans regarder 🙈", onReady: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val haptics = rememberHaptics()
    val swing = if (reduce) 0f else rememberInfiniteTransition(label = "pass")
        .animateFloat(-14f, 14f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "s").value
    LaunchedEffect(name) { haptics.confirm() }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(200.dp).staggeredAppear(0, "pass-$name"), contentAlignment = Alignment.Center) {
            MorphingBlob(
                Modifier.fillMaxSize(),
                colors.primaryContainer,
                shapes = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Heart, MaterialShapes.SoftBurst, MaterialShapes.Heart),
                periodMs = 3600,
            )
            Text("📱", style = MaterialTheme.typography.displayMedium, modifier = Modifier.graphicsLayer { rotationZ = swing; translationX = swing * 2f })
        }
        Spacer(Modifier.height(28.dp))
        Text("Passe le téléphone à", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, modifier = Modifier.staggeredAppear(1, "pass-t-$name"))
        Text(name, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.staggeredAppear(2, "pass-n-$name"))
        Spacer(Modifier.height(8.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Button(onClick = { haptics.press(); onReady() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(56.dp).staggeredAppear(3, "pass-b-$name")) {
            Text("C'est moi, $name", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Chrono optionnel : anneau qui se vide, vibration à la fin. [key] relance le compte. */
@Composable
fun CountdownRing(seconds: Int, key: Any, modifier: Modifier = Modifier, onTimeout: () -> Unit = {}) {
    if (seconds <= 0) return
    val haptics = rememberHaptics()
    val left = remember(key) { Animatable(1f) }
    LaunchedEffect(key) {
        left.snapTo(1f)
        left.animateTo(0f, tween(seconds * 1000, easing = LinearEasing))
        haptics.confirm()
        onTimeout()
    }
    val colors = MaterialTheme.colorScheme
    val urgent = left.value < 0.2f
    Box(modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            drawArc(colors.surfaceContainerHighest, 0f, 360f, false, style = stroke)
            drawArc(if (urgent) colors.error else colors.primary, -90f, 360f * left.value, false, style = stroke)
        }
        Text(
            "${(left.value * seconds).toInt() + if (left.value > 0f) 1 else 0}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (urgent) colors.error else colors.onSurface,
        )
    }
}
