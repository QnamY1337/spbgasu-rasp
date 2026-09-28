package com.example.gasuschedule.presentation.onboarding

import com.example.gasuschedule.presentation.common.networkMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gasuschedule.domain.model.ScheduleNetworkException
import com.example.gasuschedule.domain.model.StudyGroup
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.domain.usecase.SyncResult
import com.example.gasuschedule.domain.usecase.SyncScheduleUseCase
import com.example.gasuschedule.presentation.common.errorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface GroupsState {
    data object Loading : GroupsState
    data class Loaded(val all: List<StudyGroup>) : GroupsState
    data class Error(val message: String) : GroupsState
}

data class OnboardingUiState(
    val query: String = "",
    val groups: GroupsState = GroupsState.Loading,
    val matches: List<StudyGroup> = emptyList(),
    val selected: String? = null,
    val submitting: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
) {
    /**
     * Продолжить можно с выбранной из списка группой; если список не загрузился —
     * с тем, что введено вручную (сайт сам скажет, есть ли такая группа).
     */
    val candidate: String?
        get() = selected ?: query.trim().takeIf { groups is GroupsState.Error && it.isNotEmpty() }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val preferences: UserPreferencesRepository,
    private val sync: SyncScheduleUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state

    init {
        viewModelScope.launch {
            // При смене группы подставляем текущую.
            preferences.groupName.first()?.let { current ->
                _state.update { it.copy(query = current, selected = current) }
            }
            loadGroups()
        }
    }

    fun loadGroups() {
        _state.update { it.copy(groups = GroupsState.Loading) }
        viewModelScope.launch {
            val groups = try {
                GroupsState.Loaded(repository.fetchGroups())
            } catch (e: ScheduleNetworkException) {
                GroupsState.Error("${networkMessage(e.problem, "сайт расписания")} Список групп не загрузился — можно ввести название точно, как на сайте.")
            }
            _state.update { it.copy(groups = groups).withMatches() }
        }
    }

    fun onQueryChange(query: String) {
        _state.update {
            it.copy(query = query, selected = null, error = null).withMatches()
        }
    }

    fun select(group: StudyGroup) {
        _state.update { it.copy(query = group.name, selected = group.name, error = null).withMatches() }
    }

    fun submit() {
        val group = _state.value.candidate ?: return
        if (_state.value.submitting) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = sync(group)
            if (result is SyncResult.Success) {
                preferences.setGroupName(group)
                _state.update { it.copy(submitting = false, done = true) }
            } else {
                _state.update { it.copy(submitting = false, error = result.errorMessage()) }
            }
        }
    }

    private fun OnboardingUiState.withMatches(): OnboardingUiState {
        val all = (groups as? GroupsState.Loaded)?.all ?: return copy(matches = emptyList())
        return copy(matches = filterGroups(all, query))
    }

    companion object {
        const val MAX_MATCHES = 30

        /**
         * Регистр и пробелы не важны, латиница понимается как транслит ("3-ttp-26" найдёт "3-ТТП-26" —
         * частая ситуация, когда забыл переключить раскладку). Сначала точное совпадение,
         * потом начинающиеся с запроса.
         */
        fun filterGroups(all: List<StudyGroup>, query: String): List<StudyGroup> {
            val q = query.trim().lowercase().replace(" ", "")
            if (q.isEmpty()) return emptyList()
            fun forms(name: String) = name.lowercase().let { listOf(it, translit(it)) }
            return all.asSequence()
                .filter { g -> forms(g.name).any { q in it } }
                .sortedWith(
                    compareBy(
                        { g -> forms(g.name).none { it == q } },
                        { g -> forms(g.name).none { it.startsWith(q) } },
                        { it.name },
                    )
                )
                .take(MAX_MATCHES)
                .toList()
        }

        private val TRANSLIT = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
            'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
            'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
            'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "",
            'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
        )

        private fun translit(s: String): String = buildString { s.forEach { append(TRANSLIT[it] ?: it) } }
    }
}
