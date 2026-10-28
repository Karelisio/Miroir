package com.karelisio.miroir.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.karelisio.miroir.R

// Fallback rose / violet quand les couleurs dynamiques (Android 12+) ne sont pas disponibles.
val MiroirLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFA23B67), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E3), onPrimaryContainer = Color(0xFF3E0022),
    secondary = Color(0xFF6B4FA0), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBDDFF), onSecondaryContainer = Color(0xFF250059),
    tertiary = Color(0xFF9C4337), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD4), onTertiaryContainer = Color(0xFF410000),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F9), onBackground = Color(0xFF22191C),
    surface = Color(0xFFFFF8F9), onSurface = Color(0xFF22191C),
    surfaceVariant = Color(0xFFF2DDE2), onSurfaceVariant = Color(0xFF514347),
    outline = Color(0xFF837377), outlineVariant = Color(0xFFD5C2C6),
    inverseSurface = Color(0xFF382E31), inverseOnSurface = Color(0xFFFEEDF0), inversePrimary = Color(0xFFFFB0CA),
    surfaceDim = Color(0xFFE6D6D9), surfaceBright = Color(0xFFFFF8F9),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF0F3),
    surfaceContainer = Color(0xFFFAEAED), surfaceContainerHigh = Color(0xFFF4E4E7),
    surfaceContainerHighest = Color(0xFFEEDEE1),
)

val MiroirDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB0CA), onPrimary = Color(0xFF631137),
    primaryContainer = Color(0xFF83234F), onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFD4BBFF), onSecondary = Color(0xFF3B1F6F),
    secondaryContainer = Color(0xFF533787), onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFFFFB4A8), onTertiary = Color(0xFF5F1510),
    tertiaryContainer = Color(0xFF7E2C22), onTertiaryContainer = Color(0xFFFFDAD4),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191114), onBackground = Color(0xFFEFDFE2),
    surface = Color(0xFF191114), onSurface = Color(0xFFEFDFE2),
    surfaceVariant = Color(0xFF514347), onSurfaceVariant = Color(0xFFD5C2C6),
    outline = Color(0xFF9E8C90), outlineVariant = Color(0xFF514347),
    inverseSurface = Color(0xFFEFDFE2), inverseOnSurface = Color(0xFF382E31), inversePrimary = Color(0xFFA23B67),
    surfaceDim = Color(0xFF191114), surfaceBright = Color(0xFF41373A),
    surfaceContainerLowest = Color(0xFF140C0F), surfaceContainerLow = Color(0xFF22191C),
    surfaceContainer = Color(0xFF261D20), surfaceContainerHigh = Color(0xFF31282A),
    surfaceContainerHighest = Color(0xFF3C3235),
)

/** Fraunces (variable, OFL) : serif douce et chaleureuse pour les questions et les titres. */
private fun fraunces(weight: Int, opsz: Float, soft: Float = 100f) = Font(
    R.font.fraunces,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opsz),
        FontVariation.Setting("SOFT", soft),
        FontVariation.Setting("WONK", 0f),
    ),
)

val DisplayFamily = FontFamily(
    fraunces(400, 72f),
    fraunces(500, 48f),
    fraunces(600, 36f),
    fraunces(700, 28f),
)

private val base = Typography()

private fun TextStyle.display(weight: FontWeight, tracking: Float = -0.01f) =
    copy(fontFamily = DisplayFamily, fontWeight = weight, letterSpacing = tracking.em)

val MiroirTypography = Typography(
    displayLarge = base.displayLarge.display(FontWeight(400), -0.02f),
    displayMedium = base.displayMedium.display(FontWeight(400), -0.02f),
    displaySmall = base.displaySmall.display(FontWeight(500)),
    headlineLarge = base.headlineLarge.display(FontWeight(500)),
    headlineMedium = base.headlineMedium.display(FontWeight(500)),
    headlineSmall = base.headlineSmall.display(FontWeight(600), 0f),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em),
    titleSmall = base.titleSmall,
    bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(lineHeight = 22.sp),
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium,
    labelSmall = base.labelSmall,
)

val MiroirShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun MiroirTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> MiroirDarkScheme
        else -> MiroirLightScheme
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = MiroirShapes,
        typography = MiroirTypography,
        content = content,
    )
}
