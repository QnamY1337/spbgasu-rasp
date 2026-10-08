package com.example.gasuschedule.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gasuschedule.domain.model.SubjectFilter
import com.example.gasuschedule.domain.model.SubjectMode

/** Строка "Предметы" в настройках: сколько отключено, по тапу — диалог. */
@Composable
internal fun SubjectsRow(subjects: List<SubjectInfo>, filter: SubjectFilter, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val off = subjects.count { filter.mode(it.name) != SubjectMode.ON }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = subjects.isNotEmpty(), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Отключённые предметы", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            if (off == 0) "нет" else off.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (off == 0) scheme.onSurfaceVariant else scheme.primary,
        )
    }
}

/** Для каждого предмета: показывать все пары, все кроме лекций или отключить совсем. */
@Composable
internal fun SubjectsDialog(
    subjects: List<SubjectInfo>,
    filter: SubjectFilter,
    onMode: (String, SubjectMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        title = { Text("Предметы") },
        text = {
            Column {
                Text(
                    "Отключённые пары остаются в расписании серыми, но о них не приходят напоминания " +
                        "и к ним не считается дорога.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                LazyColumn(
                    Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(subjects, key = { it.name }) { subject ->
                        Column {
                            Text(
                                subject.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            SegmentedToggle(
                                options = buildList {
                                    add(SubjectMode.ON to "Все")
                                    if (subject.hasLectures) add(SubjectMode.NO_LECTURES to "Без лекций")
                                    add(SubjectMode.OFF to "Выкл")
                                },
                                selected = filter.mode(subject.name),
                                onChange = { onMode(subject.name, it) },
                            )
                        }
                    }
                }
            }
        },
    )
}
