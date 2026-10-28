package com.karelisio.miroir.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data class PairingRoute(val showInvite: Boolean = false)
@Serializable data class MainRoute(val tab: Int = 0)
@Serializable data class DayRoute(val day: String)
@Serializable data object CategoriesRoute
@Serializable data object PermissionsRoute

object Tabs {
    const val TODAY = 0
    const val HISTORY = 1
    const val QUESTIONS = 2
    const val SETTINGS = 3
}
