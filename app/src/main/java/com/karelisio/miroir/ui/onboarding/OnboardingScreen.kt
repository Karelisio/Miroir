package com.karelisio.miroir.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.permissions.AppPermission
import com.karelisio.miroir.ui.components.MorphingBlob
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.settings.AnimatedGrantedCheck
import com.karelisio.miroir.ui.settings.rememberPermissionRequester
import com.karelisio.miroir.ui.settings.rememberPermissionStates
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sin

private data class Intro(val title: String, val body: String)

private val intros = listOf(
    Intro("Bienvenue dans Miroir", "Chaque jour, une question rien que pour vous deux."),
    Intro("Chacun de son côté", "Tu réponds sur ton téléphone, l'autre sur le sien. Ta réponse reste secrète tant que l'autre n'a pas répondu."),
    Intro("Puis le miroir se révèle", "Vos deux réponses apparaissent ensemble, comme un reflet. Réagissez, commentez, souriez."),
    Intro("Rien que pour vous", "Tout est chiffré de bout en bout. La clé passe d'un téléphone à l'autre par QR code, jamais par le serveur."),
)

@Composable
fun OnboardingScreen(container: AppContainer, initialPage: Int = 0, onFinished: () -> Unit) {
    val permissions = remember { AppPermission.all }
    val total = intros.size + permissions.size + 1
    val pager = rememberPagerState(initialPage) { total }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val states = rememberPermissionStates()
    val request = rememberPermissionRequester()

    val page = pager.currentPage
    val permission = permissions.getOrNull(page - intros.size)
    val granted = permission?.let { states[it] == true } ?: false

    fun next() {
        haptics.tick()
        scope.launch { pager.animateScrollToPage((page + 1).coerceAtMost(total - 1), animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)) }
    }

    fun finish() {
        haptics.confirm()
        scope.launch {
            val skipped = permissions.filter { states[it] != true }.map { it.name }.toSet()
            container.prefs.update {
                onboardingDone = true
                skippedPermissions(skipped)
            }
            onFinished()
        }
    }

    // Autorisation accordée : coche verte puis page suivante automatiquement.
    LaunchedEffect(page, granted) {
        if (permission != null && granted) {
            delay(1100)
            if (pager.currentPage == page) next()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding()) {
        val progress by animateFloatAsState((page + 1f) / total, spring(stiffness = Spring.StiffnessLow), label = "progress")
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text("${page + 1}/$total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HorizontalPager(pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { index ->
            val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction)
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val o = offset.absoluteValue.coerceIn(0f, 1f)
                    alpha = 1f - o * 0.6f
                    scaleX = 1f - o * 0.08f
                    scaleY = 1f - o * 0.08f
                },
            ) {
                when {
                    index < intros.size -> IntroPage(index, intros[index], offset)
                    index < intros.size + permissions.size -> {
                        val p = permissions[index - intros.size]
                        PermissionPage(p, states[p] == true, offset)
                    }
                    else -> FinalPage()
                }
            }
        }

        PageDots(pager, total, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp))

        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val label = when {
                page == total - 1 -> "C'est parti"
                permission != null && !granted -> "Autoriser"
                else -> "Continuer"
            }
            Button(
                onClick = {
                    when {
                        page == total - 1 -> finish()
                        permission != null && !granted -> { haptics.press(); request(permission) }
                        else -> next()
                    }
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                AnimatedContent(label, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "cta") {
                    Text(it, style = MaterialTheme.typography.titleMedium)
                }
            }
            val secondary = when {
                permission != null && !granted -> "Plus tard"
                page < intros.size -> "Passer l'introduction"
                else -> null
            }
            TextButton(
                onClick = {
                    if (permission != null) next()
                    else scope.launch { pager.animateScrollToPage(intros.size) }
                },
                enabled = secondary != null,
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (secondary != null) 1f else 0f },
            ) { Text(secondary ?: " ") }
        }
    }
}

@Composable
private fun PageDots(pager: PagerState, total: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val selected = pager.currentPage == i
            val width by animateDpAsState(if (selected) 22.dp else 8.dp, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "w")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "c",
            )
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun PageText(title: String, body: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IntroPage(index: Int, intro: Intro, offset: Float) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(280.dp).graphicsLayer { translationX = offset * 180f },
            contentAlignment = Alignment.Center,
        ) {
            when (index) {
                0 -> MirrorBubbles()
                1 -> SecretCards()
                2 -> RevealIllustration()
                else -> PrivacyIllustration()
            }
        }
        Spacer(Modifier.height(32.dp))
        PageText(intro.title, intro.body)
    }
}

/** Deux bulles symétriques qui se reflètent de part et d'autre d'une ligne de lumière. */
@Composable
fun MirrorBubbles(modifier: Modifier = Modifier, size: Float = 1f) {
    val t = rememberInfiniteTransition(label = "bubbles")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(3600)), label = "p")
    val colors = MaterialTheme.colorScheme
    Box(modifier.size((280 * size).dp), contentAlignment = Alignment.Center) {
        MorphingBlob(Modifier.size((240 * size).dp).graphicsLayer { alpha = 0.18f }, colors.primary, periodMs = 7000)
        val dy = (sin(phase) * 10 * size).roundToInt()
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = colors.primary,
            modifier = Modifier.size((96 * size).dp).offset { IntOffset((-60 * size * density).roundToInt(), dy) },
        ) {}
        Box(Modifier.width(3.dp).height((170 * size).dp).clip(CircleShape).background(colors.tertiary.copy(alpha = 0.6f)))
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = colors.secondary.copy(alpha = 0.75f),
            modifier = Modifier.size((96 * size).dp).offset { IntOffset((60 * size * density).roundToInt(), -dy) }.graphicsLayer { scaleX = -1f },
        ) {}
    }
}

@Composable
private fun SecretCards() {
    val t = rememberInfiniteTransition(label = "secret")
    val tilt by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "tilt")
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        listOf(colors.primaryContainer to tilt, colors.secondaryContainer to -tilt).forEach { (c, r) ->
            Surface(shape = MaterialTheme.shapes.extraLarge, color = c, modifier = Modifier.size(110.dp, 160.dp).graphicsLayer { rotationZ = r }) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Lock, null, Modifier.size(40.dp)) }
            }
        }
    }
}

@Composable
private fun RevealIllustration() {
    val colors = MaterialTheme.colorScheme
    Box(contentAlignment = Alignment.Center) {
        MorphingBlob(
            Modifier.size(220.dp),
            colors.primary,
            shapes = listOf(MaterialShapes.Cookie12Sided, MaterialShapes.Heart, MaterialShapes.SoftBurst, MaterialShapes.Heart),
            periodMs = 2600,
            rotate = false,
        )
        Text("✨", style = MaterialTheme.typography.displayLarge)
    }
}

@Composable
private fun PrivacyIllustration() {
    val t = rememberInfiniteTransition(label = "key")
    val rot by t.animateFloat(-20f, 20f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "r")
    val colors = MaterialTheme.colorScheme
    Box(contentAlignment = Alignment.Center) {
        Surface(shape = MaterialShapes.Gem.toShape(), color = colors.tertiaryContainer, modifier = Modifier.size(200.dp)) {}
        Icon(Icons.Rounded.Key, null, tint = colors.onTertiaryContainer, modifier = Modifier.size(84.dp).graphicsLayer { rotationZ = rot })
    }
}

@Composable
private fun PermissionPage(permission: AppPermission, granted: Boolean, offset: Float) {
    val t = rememberInfiniteTransition(label = "perm")
    val bounce by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "b")
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(240.dp).graphicsLayer { translationX = offset * 180f }, contentAlignment = Alignment.Center) {
            MorphingBlob(Modifier.size(220.dp), if (granted) colors.tertiaryContainer else colors.primaryContainer, periodMs = 6000)
            AnimatedContent(granted, transitionSpec = { scaleIn(spring(dampingRatio = 0.45f)) togetherWith fadeOut() }, label = "granted") { ok ->
                if (ok) {
                    AnimatedGrantedCheck(true, 96.dp)
                } else {
                    Icon(
                        permission.icon,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(88.dp).graphicsLayer {
                            translationY = -12f * bounce
                            rotationZ = (bounce - 0.5f) * 10f
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        PageText(permission.title + if (permission.optional) " · optionnel" else "", permission.why)
        if (granted) {
            Spacer(Modifier.height(12.dp))
            Text("Accordé ✓", style = MaterialTheme.typography.titleMedium, color = com.karelisio.miroir.ui.settings.grantedGreen())
        }
    }
}

@Composable
private fun FinalPage() {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(if (appeared) 1f else 0.6f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow), label = "s")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { MirrorBubbles() }
        Spacer(Modifier.height(32.dp))
        PageText("Prêts à vous refléter ?", "Choisis ton prénom, puis relie ton téléphone à celui de l'autre en scannant un QR code.")
    }
}
