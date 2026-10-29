package com.karelisio.miroir.games.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.miroir.games.ui.components.GameFrame
import com.karelisio.miroir.games.ui.components.MirrorTraversal
import com.karelisio.miroir.games.ui.components.SecureScreen
import com.karelisio.miroir.games.ui.extras.DefisContent
import com.karelisio.miroir.games.ui.extras.GameSettingsActions
import com.karelisio.miroir.games.ui.extras.GameSettingsContent
import com.karelisio.miroir.games.ui.extras.RevelationsContent
import com.karelisio.miroir.games.ui.extras.StatsContent
import com.karelisio.miroir.games.ui.home.GamesActions
import com.karelisio.miroir.games.ui.home.GamesHome
import com.karelisio.miroir.games.ui.home.GamesOnboarding

/** Destinations de la section, fournies par la navigation de Miroir. */
class GamesNavigator(
    val session: (String) -> Unit,
    val canape: () -> Unit,
    val envies: () -> Unit,
    val defis: () -> Unit,
    val stats: () -> Unit,
    val revelations: () -> Unit,
    val settings: () -> Unit,
)

/** Onglet « Jeux » : traversée du miroir à chaque entrée, présentation à la première ouverture. */
@Composable
fun GamesTab(vm: GamesViewModel, contentPadding: PaddingValues, nav: GamesNavigator, onMessage: (String) -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsState()
    LaunchedEffect(Unit) { vm.events.collect(onMessage) }
    LaunchedEffect(ui.stats) {
        ui.stats?.let { s -> vm.takeNewBadges(s).forEach { onMessage("Badge débloqué : ${it.emoji} ${it.title}") } }
    }
    MirrorTraversal {
        SecureScreen()
        if (!ui.loading && !ui.onboardingDone) {
            GamesOnboarding(contentPadding) { intensity, lock -> vm.finishOnboarding(intensity, lock) }
        } else {
            GamesHome(
                ui, refreshing, contentPadding,
                GamesActions(
                    refresh = { vm.refresh() },
                    openSession = nav.session,
                    openCanape = nav.canape,
                    openEnvies = nav.envies,
                    openDefis = nav.defis,
                    openStats = nav.stats,
                    openRevelations = nav.revelations,
                    openSettings = nav.settings,
                    markDefi = vm::markDefi,
                    challenge = { kind, count, iAnswer, question -> vm.challenge(kind, count, iAnswer, question) },
                ),
            )
        }
    }
}

@Composable
fun DefisScreen(vm: GamesViewModel, onBack: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    GameFrame("🏅 Défis de la semaine", onBack) { padding -> DefisContent(ui, padding, vm::markDefi) }
}

@Composable
fun StatsScreen(vm: GamesViewModel, onBack: () -> Unit, onRevelations: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    GameFrame("Stats et badges", onBack) { padding -> StatsContent(ui, padding, onRevelations) }
}

@Composable
fun RevelationsScreen(vm: GamesViewModel, onBack: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    GameFrame("📌 Nos révélations", onBack) { padding -> RevelationsContent(ui, padding, vm::unpin) }
}

@Composable
fun GameSettingsScreen(vm: GamesViewModel, onBack: () -> Unit, onMessage: (String) -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.events.collect(onMessage) }
    GameFrame("Réglages de jeu", onBack) { padding ->
        GameSettingsContent(
            ui, padding,
            GameSettingsActions(
                setIntensity = { vm.setIntensity(it) },
                toggleCategory = { vm.toggleCategory(it) },
                setLock = { vm.setLockSpicy(it) },
                setTimer = { vm.setTimer(it) },
                addCustom = { vm.addCustom(it) },
                deleteCustom = { vm.deleteCustom(it) },
            ),
        )
    }
}
