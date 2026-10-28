package com.karelisio.miroir

import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.karelisio.miroir.notifications.Notifier
import com.karelisio.miroir.permissions.SpecialPermissionLauncher
import com.karelisio.miroir.ui.AppViewModel
import com.karelisio.miroir.ui.components.LocalHapticsEnabled
import com.karelisio.miroir.ui.navigation.DayRoute
import com.karelisio.miroir.ui.navigation.MainRoute
import com.karelisio.miroir.ui.navigation.MiroirNavHost
import com.karelisio.miroir.ui.navigation.OnboardingRoute
import com.karelisio.miroir.ui.navigation.PairingRoute
import com.karelisio.miroir.ui.navigation.Tabs
import com.karelisio.miroir.ui.theme.MiroirRoot
import com.karelisio.miroir.ui.update.UpdateSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pendingIntent = MutableStateFlow<Intent?>(null)
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !ready }
        splash.setOnExitAnimationListener { provider ->
            // Sortie du splash : l'icône s'envole en grandissant pendant que le fond s'efface.
            val icon: View = provider.iconView
            val scaleX = ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.6f)
            val scaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.6f)
            val fade = ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f)
            AnimatorSet().apply {
                playTogether(scaleX, scaleY, fade)
                duration = 420
                interpolator = AnticipateInterpolator(1.4f)
                doOnEnd { provider.remove() }
                start()
            }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        hideSystemBars()
        handleFullScreen(intent)
        pendingIntent.value = intent

        setContent {
            val appVm: AppViewModel = viewModel { AppViewModel(container) }
            val prefs by appVm.prefs.collectAsStateWithLifecycle()
            val p = prefs ?: return@setContent
            LaunchedEffect(Unit) { ready = true }

            val start = remember {
                when {
                    !p.onboardingDone -> OnboardingRoute
                    !p.paired -> PairingRoute()
                    else -> MainRoute()
                }
            }
            MiroirRoot {
                CompositionLocalProvider(LocalHapticsEnabled provides p.haptics) {
                    val nav = rememberNavController()
                    val snackbar = remember { SnackbarHostState() }
                    MiroirNavHost(container, nav, start, snackbar)
                    UpdateSheet(appVm.updates)

                    // Dissociation (depuis l'autre téléphone ou le serveur) : retour à l'appairage.
                    var wasPaired by remember { mutableStateOf(p.paired) }
                    LaunchedEffect(p.paired) {
                        if (wasPaired && !p.paired) nav.navigate(PairingRoute()) { popUpTo(nav.graph.id) { inclusive = true } }
                        wasPaired = p.paired
                    }

                    val incoming by pendingIntent.collectAsStateWithLifecycle()
                    LaunchedEffect(incoming) {
                        val i = incoming ?: return@LaunchedEffect
                        pendingIntent.value = null
                        val destination = i.getStringExtra(Notifier.EXTRA_DESTINATION)
                        if (destination == "update") {
                            appVm.updates.check(manual = true)
                            return@LaunchedEffect
                        }
                        if (!p.paired || !p.partnerJoined) return@LaunchedEffect
                        when (destination) {
                            "day" -> i.getStringExtra(Notifier.EXTRA_DAY)?.let { nav.navigate(DayRoute(it)) }
                            "custom" -> nav.navigate(MainRoute(Tabs.QUESTIONS)) { popUpTo(nav.graph.id) { inclusive = true } }
                            "today" -> nav.navigate(MainRoute(Tabs.TODAY)) { popUpTo(nav.graph.id) { inclusive = true } }
                        }
                    }
                }
            }
        }
    }

    /** Mode immersif : ni barre d'état ni barre de navigation ; un glissement depuis un bord les affiche temporairement. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Les dialogues, les réglages système ou certains constructeurs les réaffichent : on les remasque.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleFullScreen(intent)
        pendingIntent.value = intent
    }

    override fun onResume() {
        super.onResume()
        SpecialPermissionLauncher.stopWatching()
        // Les canaux "prioritaires" n'existent qu'une fois l'accès Ne pas déranger accordé.
        Notifier.createChannels(this)
        lifecycleScope.launch { runCatching { container.repository.sync() } }
    }

    /** Révélation en plein écran : s'affiche par-dessus l'écran verrouillé. */
    private fun handleFullScreen(intent: Intent?) {
        if (intent?.getBooleanExtra(Notifier.EXTRA_FULL_SCREEN, false) == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }
}
