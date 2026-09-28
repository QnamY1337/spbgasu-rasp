package com.example.gasuschedule.presentation.homework

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gasuschedule.domain.model.HomeworkGroup
import com.example.gasuschedule.domain.model.HomeworkItem
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.presentation.common.StatusBarIcons
import com.example.gasuschedule.presentation.schedule.pluralRu
import com.example.gasuschedule.presentation.theme.GasuTheme
import com.example.gasuschedule.presentation.theme.MonoStyles
import java.time.LocalDate

/** Вкладка "Задания": список с группами по сроку, отметка выполнения, добавление и правка. */
@Composable
fun HomeworkRoute(viewModel: HomeworkViewModel = hiltViewModel()) {
    StatusBarIcons(onBrickHeader = false)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<HomeworkDraft?>(null) }
    var showDone by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = HomeworkDraft() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Задание") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Задания", style = MaterialTheme.typography.headlineMedium)
                if (state.urgentCount > 0) {
                    Text(
                        "Горят: ${pluralRu(state.urgentCount, "задание", "задания", "заданий")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (state.loaded && state.items.isEmpty()) {
                item { EmptyHomework() }
            }
            for ((group, items) in state.groups) {
                if (group == HomeworkGroup.DONE) {
                    item(key = "done-toggle") {
                        TextButton(onClick = { showDone = !showDone }, contentPadding = PaddingValues(0.dp)) {
                            Text(if (showDone) "Скрыть выполненные" else "Выполненные (${items.size})")
                        }
                    }
                    if (!showDone) continue
                } else {
                    item(key = "title-$group") { GroupTitle(group.title, items.size) }
                }
                items(items, key = { it.id }) { item ->
                    HomeworkRow(item, state.today, onToggle = { viewModel.toggle(item) }, onClick = {
                        editing = HomeworkDraft(item.id, item.lessonId, item.subject, item.description, item.dueDate)
                    })
                }
            }
        }
    }

    editing?.let { draft ->
        HomeworkEditorSheet(
            initial = draft,
            subjects = state.subjects,
            today = state.today,
            nextLessonDate = { state.nextLessonDate(it) },
            onSave = { viewModel.save(it); editing = null },
            onDelete = draft.id?.let { id -> { viewModel.delete(id); editing = null } },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun GroupTitle(title: String, count: Int) {
    Row(Modifier.padding(top = 14.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MonoStyles.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MonoStyles.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun HomeworkRow(
    item: HomeworkItem,
    today: LocalDate,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val faint = GasuTheme.colors.textFaint
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (item.isDone) scheme.surface.copy(alpha = 0.6f) else scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = item.isDone,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = scheme.primary),
            )
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.subject,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (item.isDone) faint else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    item.dueDate?.let { due ->
                        val text = dueText(due, today)
                        val overdueOrSoon = !item.isDone && !due.isAfter(today.plusDays(1))
                        Text(
                            text,
                            style = MonoStyles.label,
                            color = when {
                                item.isDone -> faint
                                due.isBefore(today) -> scheme.error
                                overdueOrSoon -> scheme.primary
                                else -> scheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.isDone) faint else scheme.onSurfaceVariant,
                    textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyHomework() {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Заданий нет", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            "Добавьте задание кнопкой «Задание» или из карточки пары в расписании — срок подставится к следующей паре.",
            style = MaterialTheme.typography.bodyMedium,
            color = GasuTheme.colors.textFaint,
            textAlign = TextAlign.Center,
        )
    }
}

/** Блок "Домашнее задание" в карточке пары: что задали на ней и что сдавать к ней. */
@Composable
fun LessonHomeworkBlock(lesson: Lesson, viewModel: HomeworkViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<HomeworkDraft?>(null) }
    val items = state.forLesson(lesson)

    Column(Modifier.fillMaxWidth()) {
        Text("ДОМАШНЕЕ ЗАДАНИЕ", style = MonoStyles.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            HomeworkRow(
                item,
                state.today,
                onToggle = { viewModel.toggle(item) },
                onClick = { editing = HomeworkDraft(item.id, item.lessonId, item.subject, item.description, item.dueDate) },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        OutlinedButton(
            onClick = {
                editing = HomeworkDraft(
                    lessonId = lesson.id,
                    subject = lesson.subject,
                    dueDate = state.nextLessonDate(lesson.subject, after = lesson.date),
                )
            },
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.padding(start = 6.dp))
            Text(if (items.isEmpty()) "Добавить задание" else "Ещё задание", fontWeight = FontWeight.SemiBold)
        }
    }

    editing?.let { draft ->
        HomeworkEditorSheet(
            initial = draft,
            subjects = state.subjects,
            today = state.today,
            nextLessonDate = { state.nextLessonDate(it, after = lesson.date) },
            onSave = { viewModel.save(it); editing = null },
            onDelete = draft.id?.let { id -> { viewModel.delete(id); editing = null } },
            onDismiss = { editing = null },
        )
    }
}
