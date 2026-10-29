package com.karelisio.miroir.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data class PairingRoute(val showInvite: Boolean = false, val recovery: Boolean = false)
@Serializable data class MainRoute(val tab: Int = 0)
@Serializable data class DayRoute(val day: String)
/** [month] au format "2026-10", vide pour le dernier mois avec des souvenirs. */
@Serializable data class RecapRoute(val month: String = "")
@Serializable data object CategoriesRoute
@Serializable data object PermissionsRoute

// De l'autre côté (jeux)
@Serializable data class GameRoute(val id: String)
@Serializable data object CanapeRoute
@Serializable data object EnviesRoute
@Serializable data object DefisRoute
@Serializable data object GameStatsRoute
@Serializable data object RevelationsRoute
@Serializable data object GameSettingsRoute

object Tabs {
    const val TODAY = 0
    const val HISTORY = 1
    const val GAMES = 2
    const val QUESTIONS = 3
    const val SETTINGS = 4
}
