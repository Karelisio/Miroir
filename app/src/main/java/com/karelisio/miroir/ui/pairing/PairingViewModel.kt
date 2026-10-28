package com.karelisio.miroir.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.BuildConfig
import com.karelisio.miroir.data.remote.ApiException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PairStep { NAME, CHOOSE, WORKING, SHOW_QR, SCAN, DONE }

data class PairingUi(
    val step: PairStep = PairStep.NAME,
    val name: String = "",
    val serverUrl: String = BuildConfig.WORKER_URL,
    val invite: String? = null,
    val error: String? = null,
)

class PairingViewModel(private val c: AppContainer, showInvite: Boolean) : ViewModel() {
    private val repo = c.repository
    private val _state = MutableStateFlow(PairingUi())
    val state = _state.asStateFlow()
    private var polling: Job? = null

    init {
        viewModelScope.launch {
            val p = c.prefs.current()
            val invite = if (showInvite || (p.paired && !p.partnerJoined)) repo.currentInvite()?.uri else null
            _state.update {
                it.copy(
                    name = p.myName,
                    step = when {
                        invite != null -> PairStep.SHOW_QR
                        p.myName.isBlank() -> PairStep.NAME
                        else -> PairStep.CHOOSE
                    },
                    invite = invite,
                )
            }
            if (invite != null) waitForPartner()
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name.take(30)) }
    fun setServer(url: String) = _state.update { it.copy(serverUrl = url.trim()) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun confirmName() {
        val name = _state.value.name.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            val p = c.prefs.current()
            c.prefs.update { myProfile(name, p.myBirthday) }
            _state.update { it.copy(step = PairStep.CHOOSE) }
        }
    }

    fun back() = _state.update {
        when (it.step) {
            PairStep.SCAN -> it.copy(step = PairStep.CHOOSE)
            PairStep.CHOOSE -> it.copy(step = PairStep.NAME)
            else -> it
        }
    }

    fun create() {
        val url = _state.value.serverUrl
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            _state.update { it.copy(error = "Renseigne l'adresse de ton Worker (https://…).") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(step = PairStep.WORKING, error = null) }
            try {
                val invite = repo.createPair(url)
                _state.update { it.copy(step = PairStep.SHOW_QR, invite = invite.uri) }
                waitForPartner()
            } catch (e: Exception) {
                _state.update { it.copy(step = PairStep.CHOOSE, error = errorText(e)) }
            }
        }
    }

    fun scan() = _state.update { it.copy(step = PairStep.SCAN, error = null) }

    fun onScanned(raw: String) {
        val creds = repo.parseInvite(raw)
        if (creds == null) {
            _state.update { it.copy(error = "Ce QR code n'est pas un QR Miroir.") }
            return
        }
        if (creds.serverUrl.isBlank()) {
            _state.update { it.copy(error = "Adresse du serveur absente du QR code.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(step = PairStep.WORKING, error = null) }
            try {
                repo.joinPair(creds)
                _state.update { it.copy(step = PairStep.DONE) }
            } catch (e: Exception) {
                _state.update { it.copy(step = PairStep.SCAN, error = errorText(e)) }
            }
        }
    }

    /** Côté créateur : on attend l'arrivée de l'autre (FCM "paired" ou sondage léger). */
    private fun waitForPartner() {
        polling?.cancel()
        polling = viewModelScope.launch {
            launch {
                c.prefs.data.first { it.partnerJoined }
                _state.update { it.copy(step = PairStep.DONE) }
                polling?.cancel()
            }
            while (isActive) {
                runCatching { repo.sync() }
                delay(3_000)
            }
        }
    }

    private fun errorText(e: Exception): String = when (e) {
        is ApiException -> when (e.error.error) {
            "pair_not_found" -> "Ce miroir n'existe plus. Demande un nouveau QR code."
            "unauthorized" -> "QR code invalide."
            else -> "Le serveur a refusé la demande (${e.status})."
        }
        else -> "Impossible de joindre le serveur. Vérifie ta connexion."
    }
}
