package com.karelisio.miroir.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.karelisio.miroir.permissions.AppPermission
import com.karelisio.miroir.permissions.SpecialPermissionLauncher
import com.karelisio.miroir.ui.components.rememberHaptics

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Vert "accordé", lisible en clair comme en sombre. */
@Composable
fun grantedGreen(): Color = if (isSystemInDarkTheme()) Color(0xFF7DDC8A) else Color(0xFF1E8E3E)

/**
 * État des autorisations, revérifié à chaque retour dans l'appli (ON_RESUME) : c'est ce qui
 * déclenche la coche verte après un passage par les réglages système.
 */
@Composable
fun rememberPermissionStates(): Map<AppPermission, Boolean> {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    return remember(tick) { AppPermission.all.associateWith { it.isGranted(context) } }
}

/** Demande une autorisation : boîte système (runtime) ou écran des réglages (spéciale). */
@Composable
fun rememberPermissionRequester(onRuntimeResult: (AppPermission, Boolean) -> Unit = { _, _ -> }): (AppPermission) -> Unit {
    val context = LocalContext.current
    val activity = context.findActivity()
    var pending by remember { mutableStateOf<AppPermission?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val p = pending ?: return@rememberLauncherForActivityResult
        pending = null
        // Refus définitif : la boîte ne s'affiche plus, on bascule vers les réglages de l'appli.
        if (!granted && activity != null && p.runtimePermission != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, p.runtimePermission!!)
        ) {
            SpecialPermissionLauncher.launch(activity, p)
        }
        onRuntimeResult(p, granted)
    }
    return remember(activity, launcher) {
        { p: AppPermission ->
            val runtime = p.runtimePermission
            when {
                activity == null -> Unit
                !p.special && runtime != null -> { pending = p; launcher.launch(runtime) }
                else -> SpecialPermissionLauncher.launch(activity, p)
            }
        }
    }
}

/** Coche verte animée : la forme gonfle en ressort, puis le trait de la coche se dessine. */
@Composable
fun AnimatedGrantedCheck(granted: Boolean, size: Dp = 40.dp, animate: Boolean = true) {
    val haptics = rememberHaptics()
    val scale = remember { Animatable(if (granted && !animate) 1f else 0f) }
    val stroke = remember { Animatable(if (granted && !animate) 1f else 0f) }
    LaunchedEffect(granted) {
        if (granted) {
            if (scale.value < 1f) {
                haptics.confirm()
                scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
            }
            stroke.animateTo(1f, tween(320))
        } else {
            stroke.snapTo(0f)
            scale.snapTo(0f)
        }
    }
    val green = grantedGreen()
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; rotationZ = (1f - scale.value) * -60f }
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(green),
        contentAlignment = Alignment.Center,
    ) {
        val path = remember { Path() }
        val segment = remember { Path() }
        val measure = remember { PathMeasure() }
        Canvas(Modifier.fillMaxSize().padding(size * 0.26f)) {
            path.reset()
            path.moveTo(0f, this.size.height * 0.52f)
            path.lineTo(this.size.width * 0.38f, this.size.height * 0.88f)
            path.lineTo(this.size.width, this.size.height * 0.12f)
            measure.setPath(path, false)
            segment.reset()
            measure.getSegment(0f, measure.length * stroke.value, segment, true)
            drawPath(segment, Color.White, style = Stroke(width = this.size.width * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
