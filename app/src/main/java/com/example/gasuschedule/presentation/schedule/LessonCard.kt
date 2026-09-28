package com.example.gasuschedule.presentation.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.LessonType
import com.example.gasuschedule.domain.model.shortLabel
import com.example.gasuschedule.presentation.theme.GasuTheme
import com.example.gasuschedule.presentation.theme.MonoStyles
import java.time.LocalDateTime

/** Карточка пары из макета: слева номер и время моноширинным, справа предмет, аудитория, преподаватель. */
@Composable
fun LessonCard(
    lesson: Lesson,
    timing: LessonTiming,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val faint = GasuTheme.colors.textFaint
    val highlighted = timing == LessonTiming.CURRENT || timing == LessonTiming.NEXT

    val container = when (timing) {
        LessonTiming.CURRENT -> scheme.primaryContainer
        LessonTiming.PAST -> scheme.surface.copy(alpha = 0.55f)
        else -> scheme.surface
    }
    val border = when (timing) {
        LessonTiming.CURRENT, LessonTiming.NEXT -> BorderStroke(1.dp, scheme.primary)
        else -> BorderStroke(1.dp, scheme.outlineVariant)
    }
    val primaryText = if (timing == LessonTiming.PAST) faint else scheme.onSurface
    val secondaryText = when (timing) {
        LessonTiming.PAST -> faint
        LessonTiming.CURRENT -> scheme.onPrimaryContainer
        else -> scheme.onSurfaceVariant
    }
    val accentBar = scheme.primary

    Box(modifier.padding(top = if (highlighted) 10.dp else 0.dp)) {
        Surface(shape = MaterialTheme.shapes.medium, color = container, border = border) {
            Row(
                Modifier
                    .height(IntrinsicSize.Min)
                    .then(
                        if (timing == LessonTiming.CURRENT) Modifier.drawBehind {
                            drawRect(accentBar, size = Size(4.dp.toPx(), size.height))
                        } else Modifier,
                    )
                    .padding(start = if (timing == LessonTiming.CURRENT) 16.dp else 12.dp, end = 14.dp)
                    .padding(vertical = 14.dp),
            ) {
                Column(Modifier.width(62.dp)) {
                    Text("${lesson.lessonNumber} пара", style = MonoStyles.caption, color = secondaryText)
                    Text(lesson.startTime.toString(), style = MonoStyles.time, color = primaryText)
                    Text(lesson.endTime.toString(), style = MonoStyles.timeSecondary, color = secondaryText)
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(if (timing == LessonTiming.CURRENT) scheme.primary.copy(alpha = 0.4f) else scheme.outlineVariant),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                ) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = primaryText)) {
                                append(lesson.subject)
                            }
                            if (lesson.type != LessonType.OTHER) {
                                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = secondaryText)) {
                                    append(" (${lesson.type.shortLabel})")
                                }
                            }
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOf(lesson.room, lesson.teacher).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    lesson.note?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = secondaryText)
                    }
                }
            }
        }
        if (highlighted) {
            TimingChip(
                text = if (timing == LessonTiming.CURRENT) "СЕЙЧАС" else "ДАЛЕЕ · ${startsIn(now, lesson)}",
                filled = timing == LessonTiming.CURRENT,
                modifier = Modifier.offset(x = 16.dp, y = (-10).dp),
            )
        }
    }
}

@Composable
private fun TimingChip(text: String, filled: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (filled) scheme.primary else scheme.surface,
        border = if (filled) null else BorderStroke(1.dp, scheme.primary),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (filled) scheme.onPrimary else scheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Разделитель с подписью посередине: "Пар больше нет". */
@Composable
fun CenteredDivider(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(scheme.outlineVariant))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(scheme.outlineVariant))
    }
}
