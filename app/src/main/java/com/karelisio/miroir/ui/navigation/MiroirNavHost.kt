package com.karelisio.miroir.ui.navigation

import com.karelisio.miroir.ui.components.miroirSafe
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.ui.components.rememberHaptics
import com.karelisio.miroir.ui.custom.CustomQuestionsScreen
import com.karelisio.miroir.ui.custom.CustomQuestionsViewModel
import com.karelisio.miroir.ui.history.DayDetailScreen
import com.karelisio.miroir.ui.history.HistoryScreen
import com.karelisio.miroir.ui.history.HistoryViewModel
import com.karelisio.miroir.ui.onboarding.OnboardingScreen
import com.karelisio.miroir.ui.pairing.PairingScreen
import com.karelisio.miroir.ui.settings.CategoriesScreen
import com.karelisio.miroir.ui.settings.PermissionsScreen
import com.karelisio.miroir.ui.settings.SettingsScreen
import com.karelisio.miroir.ui.settings.SettingsViewModel
import com.karelisio.miroir.ui.today.TodayScreen
import com.karelisio.miroir.ui.today.TodayViewModel
import kotlinx.coroutines.launch

// Transitions "shared axis" pilotées par le MotionScheme expressif de M3.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(m: MotionScheme): EnterTransition =
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, m.defaultSpatialSpec()) { it / 4 } + fadeIn(m.defaultEffectsSpec())

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(m: MotionScheme): ExitTransition =
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, m.defaultSpatialSpec()) { it / 8 } + fadeOut(m.fastEffectsSpec())

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(m: MotionScheme): EnterTransition =
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, m.defaultSpatialSpec()) { it / 8 } + fadeIn(m.defaultEffectsSpec())

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(m: MotionScheme): ExitTransition =
    // Retour prédictif : l'écran rétrécit légèrement en suivant le geste.
    scaleOut(m.defaultSpatialSpec(), targetScale = 0.9f) + fadeOut(m.fastEffectsSpec()) +
        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, m.defaultSpatialSpec()) { it / 4 }

@Composable
fun MiroirNavHost(
    container: AppContainer,
    navController: NavHostController,
    startDestination: Any,
    snackbar: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val message: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text) } }
    val motion = MaterialTheme.motionScheme

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                enterTransition = { enter(motion) },
                exitTransition = { exit(motion) },
                popEnterTransition = { popEnter(motion) },
                popExitTransition = { popExit(motion) },
            ) {
                composable<OnboardingRoute> {
                    OnboardingScreen(
                        container = container,
                        onFinished = {
                            navController.navigate(PairingRoute()) { popUpTo<OnboardingRoute> { inclusive = true } }
                        },
                    )
                }
                composable<PairingRoute> { entry ->
                    val route = entry.toRoute<PairingRoute>()
                    PairingScreen(
                        container = container,
                        showInvite = route.showInvite,
                        onPaired = {
                            if (!navController.popBackStack(MainRoute(), inclusive = false)) {
                                navController.navigate(MainRoute()) { popUpTo(navController.graph.id) { inclusive = true } }
                            }
                        },
                        onBack = if (route.showInvite) ({ navController.popBackStack() }) else null,
                        onMessage = message,
                    )
                }
                composable<MainRoute> { entry ->
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        MainScaffold(
                            container = container,
                            initialTab = entry.toRoute<MainRoute>().tab,
                            snackbar = snackbar,
                            navController = navController,
                            onMessage = message,
                        )
                    }
                }
                composable<DayRoute> { entry ->
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        val vm: HistoryViewModel = viewModel { HistoryViewModel(container) }
                        DayDetailScreen(vm, entry.toRoute<DayRoute>().day, onBack = { navController.popBackStack() })
                    }
                }
                composable<CategoriesRoute> {
                    CategoriesScreen(container, onBack = { navController.popBackStack() })
                }
                composable<PermissionsRoute> {
                    PermissionsScreen(container, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

private data class TabItem(val label: String, val selected: ImageVector, val unselected: ImageVector)

private val tabs = listOf(
    TabItem("Aujourd'hui", Icons.Rounded.WbSunny, Icons.Outlined.WbSunny),
    TabItem("Historique", Icons.Rounded.AutoStories, Icons.Outlined.AutoStories),
    TabItem("Questions", Icons.Rounded.EditNote, Icons.Outlined.EditNote),
    TabItem("Réglages", Icons.Rounded.Settings, Icons.Outlined.Settings),
)

@Composable
private fun MainScaffold(
    container: AppContainer,
    initialTab: Int,
    snackbar: SnackbarHostState,
    navController: NavHostController,
    onMessage: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    LaunchedEffect(initialTab) { tab = initialTab }
    val haptics = rememberHaptics()
    val motion = MaterialTheme.motionScheme
    val todayVm: TodayViewModel = viewModel { TodayViewModel(container) }
    val historyVm: HistoryViewModel = viewModel { HistoryViewModel(container) }
    val customVm: CustomQuestionsViewModel = viewModel { CustomQuestionsViewModel(container) }
    val settingsVm: SettingsViewModel = viewModel { SettingsViewModel(container) }

    Scaffold(
        contentWindowInsets = WindowInsets.miroirSafe,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            ShortNavigationBar {
                tabs.forEachIndexed { i, item ->
                    ShortNavigationBarItem(
                        selected = tab == i,
                        onClick = {
                            if (tab != i) haptics.tick()
                            tab = i
                        },
                        icon = { Icon(if (tab == i) item.selected else item.unselected, contentDescription = null) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                // "Fade through" Material : disparition rapide, apparition avec léger zoom.
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.96f)) togetherWith
                    fadeOut(motion.fastEffectsSpec())
            },
            label = "tabs",
        ) { current ->
            Box(Modifier.fillMaxSize()) {
                when (current) {
                    Tabs.TODAY -> TodayScreen(
                        vm = todayVm,
                        contentPadding = padding,
                        onShowInvite = { navController.navigate(PairingRoute(showInvite = true)) },
                        onOpenPermissions = { navController.navigate(PermissionsRoute) },
                        onMessage = onMessage,
                    )
                    Tabs.HISTORY -> HistoryScreen(historyVm, padding, onOpenDay = { navController.navigate(DayRoute(it)) })
                    Tabs.QUESTIONS -> CustomQuestionsScreen(customVm, padding, onMessage)
                    else -> SettingsScreen(
                        vm = settingsVm,
                        contentPadding = padding,
                        onOpenCategories = { navController.navigate(CategoriesRoute) },
                        onOpenPermissions = { navController.navigate(PermissionsRoute) },
                        onShowInvite = { navController.navigate(PairingRoute(showInvite = true)) },
                        onUnpaired = { navController.navigate(PairingRoute()) { popUpTo(navController.graph.id) { inclusive = true } } },
                        onMessage = onMessage,
                    )
                }
            }
        }
    }
}
