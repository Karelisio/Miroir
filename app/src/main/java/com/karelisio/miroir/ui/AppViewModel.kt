package com.karelisio.miroir.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.data.prefs.AppPrefs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(val container: AppContainer) : ViewModel() {
    val prefs: StateFlow<AppPrefs?> = container.prefs.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val updates = container.updates

    init {
        // Vérification au lancement (la vérification périodique de 6 h est faite par WorkManager).
        viewModelScope.launch {
            val p = container.prefs.current()
            if (System.currentTimeMillis() - p.lastUpdateCheck > 30 * 60_000L) updates.check(manual = false)
        }
    }
}
