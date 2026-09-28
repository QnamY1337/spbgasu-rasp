package com.example.gasuschedule.presentation.homework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gasuschedule.domain.model.HomeworkGroup
import com.example.gasuschedule.domain.model.HomeworkItem
import com.example.gasuschedule.domain.model.HomeworkPlanning
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.LessonType
import com.example.gasuschedule.domain.repository.HomeworkRepository
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.domain.usecase.ManageHomeworkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class HomeworkUiState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    val items: List<HomeworkItem> = emptyList(),
    /** Пары группы от сегодня и на семестр вперёд: подсказки предметов и срок "к следующей паре". */
    val lessons: List<Lesson> = emptyList(),
) {
    val groups: Map<HomeworkGroup, List<HomeworkItem>> get() = HomeworkPlanning.grouped(items, today)
    val urgentCount: Int get() = HomeworkPlanning.urgentCount(items, today)
    val subjects: List<String> get() = lessons.map { it.subject }.distinct().sorted()

    fun nextLessonDate(subject: String, after: LocalDate = today, type: LessonType? = null): LocalDate? =
        HomeworkPlanning.nextLessonDate(subject, after, lessons, type)

    fun typesOf(subject: String): List<LessonType> = HomeworkPlanning.typesOf(subject, lessons)

    /**
     * Задания пары: заданные на ней и те, что нужно сдать к ней. Задание к практике
     * не показывается под лекцией того же дня (и наоборот).
     */
    fun forLesson(lesson: Lesson): List<HomeworkItem> = items.filter {
        it.lessonId == lesson.id ||
            (it.dueDate == lesson.date &&
                HomeworkPlanning.sameSubject(it.subject, lesson.subject) &&
                (it.lessonType == null || it.lessonType == lesson.type))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeworkViewModel @Inject constructor(
    repository: HomeworkRepository,
    schedule: ScheduleRepository,
    preferences: UserPreferencesRepository,
    private val manage: ManageHomeworkUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val lessons = preferences.groupName.flatMapLatest { group ->
        if (group == null) flowOf(emptyList())
        else LocalDate.now(clock).let { schedule.observeLessons(group, it.minusDays(7), it.plusDays(LOOKAHEAD_DAYS)) }
    }

    val state: StateFlow<HomeworkUiState> = combine(repository.observeAll(), lessons) { items, lessons ->
        HomeworkUiState(loaded = true, today = LocalDate.now(clock), items = items, lessons = lessons)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeworkUiState())

    fun toggle(item: HomeworkItem) = viewModelScope.launch { manage.setDone(item.id, !item.isDone) }

    fun save(draft: HomeworkDraft) = viewModelScope.launch {
        manage.save(draft.id, draft.lessonId, draft.subject, draft.description, draft.dueDate, draft.lessonType)
    }

    fun delete(id: String) = viewModelScope.launch { manage.delete(id) }

    private companion object {
        const val LOOKAHEAD_DAYS = 150L
    }
}
