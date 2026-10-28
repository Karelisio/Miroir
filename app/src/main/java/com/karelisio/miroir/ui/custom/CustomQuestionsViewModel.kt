package com.karelisio.miroir.ui.custom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.data.model.CustomQuestionPayload
import com.karelisio.miroir.data.model.QuestionType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CustomItem(
    val id: String,
    val text: String,
    val type: QuestionType,
    val mine: Boolean,
    val authorName: String,
    val used: Boolean,
    val synced: Boolean,
)

data class CustomUi(val items: List<CustomItem> = emptyList())

class CustomQuestionsViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository

    val state: StateFlow<CustomUi> = combine(repo.dao.observeCustom(), repo.dao.observeDays(), c.prefs.data) { custom, days, prefs ->
        val used = days.flatMap { listOfNotNull(it.questionId, it.skippedQuestionId) }.toSet()
        CustomUi(
            custom.filter { !it.deleted }.mapNotNull { e ->
                val q = repo.customQuestion(e) ?: return@mapNotNull null
                val mine = e.author == prefs.member.key
                CustomItem(e.id, q.text, q.type, mine, if (mine) prefs.myName.ifBlank { "Moi" } else prefs.partnerDisplayName, e.id in used, e.synced)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomUi())

    fun add(text: String, type: QuestionType, options: List<String>, min: String?, max: String?) {
        viewModelScope.launch {
            val today = repo.today().toString()
            repo.addCustomQuestion(
                CustomQuestionPayload(
                    text = text,
                    type = type,
                    options = if (type == QuestionType.CHOICE) options else emptyList(),
                    minLabel = min.takeIf { type == QuestionType.SCALE },
                    maxLabel = max.takeIf { type == QuestionType.SCALE },
                    createdDay = today,
                ),
            )
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.deleteCustomQuestion(id) }
    }
}
