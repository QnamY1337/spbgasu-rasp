package com.example.gasuschedule.presentation.homework

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gasuschedule.domain.model.HomeworkGroup
import com.example.gasuschedule.domain.model.HomeworkItem
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.shortLabel
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

    // Задания добавляются только с карточки пары (меню по тапу) — здесь список и правка.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
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
                    item(key = "title-$group") { GroupTitle(group.title, items.size, urgent = group == HomeworkGroup.URGENT) }
                }
                items(items, key = { it.id }) { item ->
                    HomeworkRow(item, state.today, onToggle = { viewModel.toggle(item) }, onClick = { editing = draftOf(item) })
                }
            }
        }
    }

    editing?.let { draft ->
        val lesson = draft.lessonId?.let { id -> state.lessons.firstOrNull { it.id == id } }
        HomeworkEditorSheet(
            initial = draft,
            lesson = lesson,
            nextSameType = lesson?.let(state::nextSameType),
            onSave = { viewModel.save(it); editing = null },
            onDelete = draft.id?.let { id -> { viewModel.delete(id); editing = null } },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun GroupTitle(title: String, count: Int, urgent: Boolean = false) {
    val color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.padding(top = 14.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MonoStyles.label, color = color, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MonoStyles.label, color = color)
    }
}

/** Срок плашкой: просрочено — красная, сегодня — бренд, завтра — тон бренда, дальше — серая. */
@Composable
private fun DueChip(text: String, due: LocalDate, today: LocalDate, done: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when {
        done -> Color.Transparent to GasuTheme.colors.textFaint
        due.isBefore(today) -> scheme.error to scheme.onError
        due == today -> scheme.primary to scheme.onPrimary
        due == today.plusDays(1) -> scheme.primaryContainer to scheme.onPrimaryContainer
        else -> scheme.outlineVariant to scheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Text(text, style = MonoStyles.label, color = fg, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
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
                        item.subject + (item.lessonType?.let { " (${it.shortLabel})" } ?: ""),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (item.isDone) faint else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    item.dueDate?.let { due -> DueChip(dueText(due, today), due, today, item.isDone) }
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
            "Нажмите на пару в расписании или на главной — задание можно добавить на эту пару или к следующей такой же (лекция, практика, лаба).",
            style = MaterialTheme.typography.bodyMedium,
            color = GasuTheme.colors.textFaint,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Меню под карточкой пары: задания этой пары (правка), "ДЗ на эту пару" и "ДЗ к след. <вид>" —
 * следующая пара того же предмета и вида (лекция, практика, лаба). Других способов добавить нет.
 */
@Composable
fun LessonHomeworkMenu(
    lesson: Lesson,
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: HomeworkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<HomeworkDraft?>(null) }
    val next = state.nextSameType(lesson)
    val scheme = MaterialTheme.colorScheme

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.medium,
        containerColor = scheme.surface,
        offset = DpOffset(12.dp, 4.dp),
    ) {
        state.forLesson(lesson).forEach { item ->
            DropdownMenuItem(
                text = {
                    Text(
                        "ДЗ: ${item.description.lineSequence().first()}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                        color = if (item.isDone) GasuTheme.colors.textFaint else scheme.onSurface,
                    )
                },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = scheme.onSurfaceVariant) },
                onClick = { onDismiss(); editing = draftOf(item) },
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
        DropdownMenuItem(
            text = { Text("На эту пару · ${dayShort(lesson.date)}", color = scheme.primary, fontWeight = FontWeight.SemiBold) },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = scheme.primary) },
            onClick = { onDismiss(); editing = newDraft(lesson, lesson.date) },
        )
        next?.let { date ->
            DropdownMenuItem(
                text = { Text("К след. ${nextOfType(lesson.type)} · ${dayShort(date)}", color = scheme.primary) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = scheme.primary) },
                onClick = { onDismiss(); editing = newDraft(lesson, date) },
            )
        }
    }

    editing?.let { draft ->
        HomeworkEditorSheet(
            initial = draft,
            lesson = lesson,
            nextSameType = next,
            onSave = { viewModel.save(it); editing = null },
            onDelete = draft.id?.let { id -> { viewModel.delete(id); editing = null } },
            onDismiss = { editing = null },
        )
    }
}
