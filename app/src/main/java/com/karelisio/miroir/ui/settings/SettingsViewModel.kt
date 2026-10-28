package com.karelisio.miroir.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.BuildConfig
import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.data.prefs.AppPrefs
import com.karelisio.miroir.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.karelisio.miroir.export.MemoriesPdf

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val prefs: StateFlow<AppPrefs> = c.prefs.data.stateIn(viewModelScope, SharingStarted.Eagerly, AppPrefs())
    val updateState: StateFlow<UpdateState> = c.updates.state
    val checking = MutableStateFlow(false)
    val version: String = BuildConfig.VERSION_NAME

    fun setReminderEnabled(on: Boolean) = edit { reminderEnabled = on }
    fun setReminderTime(hour: Int, minute: Int) = edit { reminder(hour, minute) }
    fun setNotifyPartner(on: Boolean) = edit { notifyPartner = on }
    fun setEveningReminder(on: Boolean) = edit { eveningReminder = on }
    fun setAppLock(on: Boolean) = edit { appLock = on }
    fun setFullScreen(on: Boolean) = edit { fullScreenReveal = on }
    fun setBypassDnd(on: Boolean) = edit { bypassDnd = on }
    fun setHaptics(on: Boolean) = edit { haptics = on }
    fun resetIgnoredVersion() = edit { ignoredVersion(null) }

    fun updateProfile(name: String, birthday: String?) {
        viewModelScope.launch { c.repository.updateProfile(name, birthday) }
    }

    fun setAnniversary(date: String?) {
        viewModelScope.launch { c.repository.updateShared { it.copy(anniversary = date) } }
    }

    fun setPacks(on: Boolean) {
        viewModelScope.launch { c.repository.updateShared { it.copy(packsEnabled = on) } }
    }

    /** Au moins une catégorie doit rester active. */
    fun toggleCategory(key: String): Boolean {
        val disabled = prefs.value.shared.disabledCategories
        val enabling = key in disabled
        if (!enabling && Category.drawable.count { it.key !in disabled } <= 1) return false
        viewModelScope.launch {
            c.repository.updateShared { s ->
                s.copy(disabledCategories = if (enabling) s.disabledCategories - key else s.disabledCategories + key)
            }
        }
        return true
    }

    fun checkUpdates(onResult: (String?) -> Unit) {
        if (checking.value) return
        viewModelScope.launch {
            checking.value = true
            c.updates.check(manual = true)
            checking.value = false
            when (val s = c.updates.state.value) {
                UpdateState.UpToDate -> onResult("Miroir est à jour ✨")
                is UpdateState.Failed -> onResult(s.message)
                else -> onResult(null)
            }
        }
    }

    val exporting = MutableStateFlow(false)

    /** Livre souvenir en PDF, écrit dans le fichier choisi par l'utilisateur. */
    fun exportPdf(context: android.content.Context, uri: android.net.Uri, onResult: (String) -> Unit) {
        if (exporting.value) return
        viewModelScope.launch {
            exporting.value = true
            val message = runCatching {
                val repo = c.repository
                val entries = repo.dao.days().map { MemoriesPdf.Entry(it, repo.resolveQuestion(it.questionId)) }
                val prefs = c.prefs.current()
                val today = repo.today()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { MemoriesPdf.write(context, it, prefs, entries, today) } ?: error("flux indisponible")
                }
            }.fold(
                onSuccess = { n -> if (n == 0) "PDF créé : vos souvenirs s'y ajouteront 💜" else "PDF enregistré : $n souvenir${if (n > 1) "s" else ""} ✨" },
                onFailure = { "Impossible d'écrire le PDF." },
            )
            exporting.value = false
            onResult(message)
        }
    }

    fun unpair(onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            if (c.repository.unpair()) onDone() else onError("Connexion nécessaire pour dissocier vos miroirs.")
        }
    }

    private fun edit(block: com.karelisio.miroir.data.prefs.Prefs.Editor.() -> Unit) {
        viewModelScope.launch { c.prefs.update(block) }
    }
}
