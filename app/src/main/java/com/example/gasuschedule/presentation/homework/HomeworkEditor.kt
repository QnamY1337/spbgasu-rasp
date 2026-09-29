package com.example.gasuschedule.presentation.homework

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.gasuschedule.domain.model.HomeworkItem
import com.example.gasuschedule.domain.model.HomeworkPlanning
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.LessonType
import com.example.gasuschedule.domain.model.shortLabel
import com.example.gasuschedule.presentation.schedule.shortDate
import com.example.gasuschedule.presentation.schedule.shortDayName
import com.example.gasuschedule.presentation.theme.MonoStyles
import java.time.LocalDate

/** Черновик задания в редакторе. [id] == null — новое. */
data class HomeworkDraft(
    val id: String? = null,
    /** Пара, с которой задание добавили. */
    val lessonId: String? = null,
    val subject: String = "",
    val description: String = "",
    val dueDate: LocalDate? = null,
    /** Лекция, практика или лаба — задание сдаётся на паре этого типа. */
    val lessonType: LessonType? = null,
)

/** "ВТ 06.10". */
internal fun dayShort(date: LocalDate) = "${shortDayName(date)} ${shortDate(date)}"

/** "лекции", "практике", "лабораторной", иначе "паре" — для "К след. …". */
internal fun nextOfType(type: LessonType?) = when (type) {
    LessonType.LECTURE -> "лекции"
    LessonType.PRACTICE -> "практике"
    LessonType.LAB -> "лабораторной"
    else -> "паре"
}

/** Черновик нового задания для пары со сроком [due] (эта пара или следующая того же типа). */
internal fun newDraft(lesson: Lesson, due: LocalDate) = HomeworkDraft(
    lessonId = lesson.id,
    subject = lesson.subject,
    dueDate = due,
    lessonType = lesson.type.takeIf { it != LessonType.OTHER },
)

internal fun draftOf(item: HomeworkItem) =
    HomeworkDraft(item.id, item.lessonId, item.subject, item.description, item.dueDate, item.lessonType)

/**
 * Редактор задания: что задали и срок — только "На эту пару" или "К след. <тип>" (пара того же
 * предмета и вида после [lesson]). [lesson] == null — пара задания уже не в расписании:
 * срок тогда не меняется, можно поправить текст или удалить.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeworkEditorSheet(
    initial: HomeworkDraft,
    lesson: Lesson?,
    nextSameType: LocalDate?,
    onSave: (HomeworkDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var due by remember { mutableStateOf(initial.dueDate) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(
                if (initial.id == null) "Новое задание" else "Задание",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(initial.subject) }
                    val type = lesson?.type ?: initial.lessonType
                    if (type != null && type != LessonType.OTHER) {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" (${type.shortLabel})") }
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Что задали") },
                minLines = 3,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Text("СРОК", style = MonoStyles.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (lesson != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DueChip("На эту пару · ${dayShort(lesson.date)}", selected = due == lesson.date) { due = lesson.date }
                    nextSameType?.let { next ->
                        DueChip("К след. ${nextOfType(lesson.type)} · ${dayShort(next)}", selected = due == next) { due = next }
                    }
                }
            } else {
                Text(
                    due?.let { "до ${dayShort(it)}" } ?: "без срока",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSave(initial.copy(description = description, dueDate = due)) },
                    enabled = description.isNotBlank(),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("Сохранить") }
            }
        }
    }
}

@Composable
private fun DueChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

/** Подпись срока в списке: "сегодня", "завтра", "до ВТ 06.10", "просрочено". */
internal fun dueText(due: LocalDate, today: LocalDate) = HomeworkPlanning.dueLabel(due, today, ::dayShort)
