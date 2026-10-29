package com.karelisio.miroir.games.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

/** Vrai si l'utilisateur a désactivé les animations (réglage système « Supprimer les animations »). */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

// Cibles chaudes : framboise, braise et ambre en clair ; corail, ambre et vin profond en sombre.
private object Warm {
    val lightPrimary = Color(0xFFC2185B)
    val lightSecondary = Color(0xFFC6501F)
    val lightTertiary = Color(0xFFB0306A)
    val lightSurface = Color(0xFFFFF1EC)
    val lightContainer = Color(0xFFFFDCD2)
    val darkPrimary = Color(0xFFFF8FA3)
    val darkSecondary = Color(0xFFFFB074)
    val darkTertiary = Color(0xFFF48FB1)
    val darkSurface = Color(0xFF1C0B10)
    val darkContainer = Color(0xFF5A1A2C)
}

/**
 * Palette « De l'autre côté » : la palette dynamique de Miroir, glissée vers des tons plus chauds et
 * intimes. [t] de 0 (couleurs de Miroir) à 1 (palette chaude), pour l'animation de traversée.
 */
fun warmScheme(base: ColorScheme, dark: Boolean, t: Float = 1f): ColorScheme {
    val a = 0.45f * t // accents
    val s = (if (dark) 0.6f else 0.45f) * t // surfaces
    fun mix(c: Color, target: Color, f: Float) = lerp(c, target, f)
    val surface = if (dark) Warm.darkSurface else Warm.lightSurface
    val container = if (dark) Warm.darkContainer else Warm.lightContainer
    return base.copy(
        primary = mix(base.primary, if (dark) Warm.darkPrimary else Warm.lightPrimary, a),
        primaryContainer = mix(base.primaryContainer, container, a),
        onPrimaryContainer = mix(base.onPrimaryContainer, if (dark) Color(0xFFFFE3E8) else Color(0xFF3F0018), a),
        secondary = mix(base.secondary, if (dark) Warm.darkSecondary else Warm.lightSecondary, a),
        secondaryContainer = mix(base.secondaryContainer, if (dark) Color(0xFF5C2A12) else Color(0xFFFFDBCB), a),
        onSecondaryContainer = mix(base.onSecondaryContainer, if (dark) Color(0xFFFFDBCB) else Color(0xFF3A0B00), a),
        tertiary = mix(base.tertiary, if (dark) Warm.darkTertiary else Warm.lightTertiary, a),
        tertiaryContainer = mix(base.tertiaryContainer, if (dark) Color(0xFF6E1F45) else Color(0xFFFFD8E6), a),
        onTertiaryContainer = mix(base.onTertiaryContainer, if (dark) Color(0xFFFFD8E6) else Color(0xFF3D0021), a),
        background = mix(base.background, surface, s),
        surface = mix(base.surface, surface, s),
        surfaceDim = mix(base.surfaceDim, surface, s),
        surfaceBright = mix(base.surfaceBright, surface, s * 0.8f),
        surfaceContainerLowest = mix(base.surfaceContainerLowest, surface, s),
        surfaceContainerLow = mix(base.surfaceContainerLow, container, s * 0.25f),
        surfaceContainer = mix(base.surfaceContainer, container, s * 0.35f),
        surfaceContainerHigh = mix(base.surfaceContainerHigh, container, s * 0.45f),
        surfaceContainerHighest = mix(base.surfaceContainerHighest, container, s * 0.55f),
        surfaceVariant = mix(base.surfaceVariant, container, s * 0.4f),
        outlineVariant = mix(base.outlineVariant, container, s * 0.4f),
        inversePrimary = mix(base.inversePrimary, if (dark) Warm.lightPrimary else Warm.darkPrimary, a),
    )
}

/** Thème de la section : mêmes formes, typographie et mouvements expressifs que Miroir, palette réchauffée. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GamesTheme(progress: Float = 1f, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val base = MaterialTheme.colorScheme
    val scheme = remember(base, dark, progress) { warmScheme(base, dark, progress) }
    val reduce = rememberReduceMotion()
    CompositionLocalProvider(LocalReduceMotion provides reduce) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}
