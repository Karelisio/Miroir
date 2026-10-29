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
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.karelisio.miroir.diagnostics.Diagnostics
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
import com.karelisio.miroir.security.AppLock
import com.karelisio.miroir.ui.lock.LockScreen
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

// FragmentActivity (et non ComponentActivity) : requis par BiometricPrompt pour le verrouillage.
class MainActivity : FragmentActivity() {

    private val pendingIntent = MutableStateFlow<Intent?>(null)
    private var ready = false

    /** Verrou demandé (démarrage, retour après [AppLock.TIMEOUT_MS] en arrière-plan) ; effectif si activé. */
    private val lockRequested = MutableStateFlow(true)
    @Volatile private var lockEnabled = false
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Recréation (rotation, mort du processus) : on garde l'état, sauf si l'appli est restée trop longtemps en arrière-plan.
        savedInstanceState?.let {
            stoppedAt = it.getLong(KEY_STOPPED_AT)
            val tooLong = stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > AppLock.TIMEOUT_MS
            lockRequested.value = it.getBoolean(KEY_LOCKED, true) || tooLong
        }
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

                    // Après un plantage : proposer d'envoyer le rapport.
                    LaunchedEffect(Unit) {
                        if (Diagnostics.consumeCrash()) {
                            val result = snackbar.showSnackbar(
                                "Miroir s'est fermé de façon inattendue.",
                                actionLabel = "Signaler",
                                withDismissAction = true,
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) Diagnostics.share(this@MainActivity)
                        }
                    }

                    LaunchedEffect(p.appLock) {
                        lockEnabled = p.appLock
                        if (!p.appLock) lockRequested.value = false
                        // Pas d'aperçu du contenu dans les applis récentes quand Miroir est verrouillable.
                        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!p.appLock)
                    }
                    val lockAsked by lockRequested.collectAsStateWithLifecycle()
                    AnimatedVisibility(p.appLock && lockAsked, enter = fadeIn(), exit = fadeOut() + scaleOut(targetScale = 1.08f)) {
                        val unlock = { AppLock.authenticate(this@MainActivity, "Déverrouiller Miroir") { ok -> if (ok) lockRequested.value = false } }
                        LaunchedEffect(Unit) { unlock() }
                        // Retour : on quitte sans naviguer dans l'appli cachée derrière.
                        BackHandler { moveTaskToBack(true) }
                        LockScreen(onUnlock = unlock)
                    }

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
                            "history" -> nav.navigate(MainRoute(Tabs.HISTORY)) { popUpTo(nav.graph.id) { inclusive = true } }
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

    override fun onStart() {
        super.onStart()
        if (lockEnabled && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > AppLock.TIMEOUT_MS) lockRequested.value = true
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, lockRequested.value)
        outState.putLong(KEY_STOPPED_AT, stoppedAt)
    }

    override fun onResume() {
        super.onResume()
        SpecialPermissionLauncher.stopWatching()
        // Les canaux "prioritaires" n'existent qu'une fois l'accès Ne pas déranger accordé.
        Notifier.createChannels(this)
        lifecycleScope.launch {
            runCatching { container.repository.sync() }.onFailure { e ->
                // Hors ligne : normal ; le reste mérite d'être noté.
                if (e !is java.io.IOException || e is com.karelisio.miroir.data.remote.ApiException) Diagnostics.record("synchro", "au retour dans l'appli", e)
            }
        }
    }

    private companion object {
        const val KEY_LOCKED = "miroir.locked"
        const val KEY_STOPPED_AT = "miroir.stopped_at"
    }

    /** Révélation en plein écran : s'affiche par-dessus l'écran verrouillé. */
    private fun handleFullScreen(intent: Intent?) {
        if (intent?.getBooleanExtra(Notifier.EXTRA_FULL_SCREEN, false) == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }
}
