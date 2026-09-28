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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.gasuschedule.domain.model.HomeworkPlanning
import com.example.gasuschedule.presentation.schedule.shortDate
import com.example.gasuschedule.presentation.schedule.shortDayName
import com.example.gasuschedule.presentation.theme.MonoStyles
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Черновик задания в редакторе. [id] == null — новое. */
data class HomeworkDraft(
    val id: String? = null,
    val lessonId: String? = null,
    val subject: String = "",
    val description: String = "",
    val dueDate: LocalDate? = null,
)

/** "ВТ 06.10". */
internal fun dayShort(date: LocalDate) = "${shortDayName(date)} ${shortDate(date)}"

/**
 * Редактор задания: предмет (с подсказками из расписания), что задали, срок.
 * Срок по умолчанию — к следующей паре по предмету ([nextLessonDate]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeworkEditorSheet(
    initial: HomeworkDraft,
    subjects: List<String>,
    today: LocalDate,
    nextLessonDate: (String) -> LocalDate?,
    onSave: (HomeworkDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var subject by rememberSaveable { mutableStateOf(initial.subject) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var due by remember { mutableStateOf(initial.dueDate) }
    var pickDate by remember { mutableStateOf(false) }
    var subjectsOpen by remember { mutableStateOf(false) }
    val next = remember(subject) { nextLessonDate(subject) }

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
            Spacer(Modifier.height(16.dp))

            val suggestions = subjects.filter { subject.isBlank() || it.contains(subject.trim(), ignoreCase = true) }
            ExposedDropdownMenuBox(expanded = subjectsOpen && suggestions.isNotEmpty(), onExpandedChange = { subjectsOpen = it }) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it; subjectsOpen = true },
                    label = { Text("Предмет") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                )
                ExposedDropdownMenu(
                    expanded = subjectsOpen && suggestions.isNotEmpty(),
                    onDismissRequest = { subjectsOpen = false },
                ) {
                    suggestions.take(8).forEach { s ->
                        DropdownMenuItem(text = { Text(s) }, onClick = { subject = s; subjectsOpen = false })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = buildList {
                    next?.let { add("К след. паре · ${dayShort(it)}" to it) }
                    add("Завтра" to today.plusDays(1))
                    add("Через неделю" to today.plusWeeks(1))
                }
                options.forEach { (label, date) -> DueChip(label, selected = due == date) { due = date } }
                val custom = due?.takeIf { d -> options.none { it.second == d } }
                DueChip(custom?.let(::dayShort) ?: "Выбрать дату…", selected = custom != null) { pickDate = true }
                DueChip("Без срока", selected = due == null) { due = null }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        onSave(initial.copy(subject = subject, description = description, dueDate = due))
                    },
                    enabled = subject.isNotBlank() && description.isNotBlank(),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("Сохранить") }
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (due ?: today.plusDays(1)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Отмена") } },
        ) { DatePicker(state) }
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
