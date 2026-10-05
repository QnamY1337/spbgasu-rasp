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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gasuschedule.domain.model.HomeworkItem
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.LessonType
import com.example.gasuschedule.presentation.theme.GasuTheme
import com.example.gasuschedule.presentation.theme.MonoStyles
import java.time.LocalDateTime

/**
 * Меню под карточкой пары по тапу (добавить ДЗ); задаётся экраном. Параметры: пара, раскрыто ли,
 * закрыть. null — карточка не нажимается.
 */
val LocalLessonMenu = staticCompositionLocalOf<(@Composable (Lesson, Boolean, () -> Unit) -> Unit)?> { null }

/** Карточка пары из макета: слева номер и время моноширинным, справа предмет, аудитория, преподаватель. */
@Composable
fun LessonCard(
    lesson: Lesson,
    timing: LessonTiming,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
    /** Задания, которые сдавать на этой паре. */
    homework: List<HomeworkItem> = emptyList(),
    /** false — время вынесено наружу (таймлайн на главной), в карточке остаётся только содержимое. */
    showTime: Boolean = true,
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
        val menu = LocalLessonMenu.current
        var menuOpen by remember { mutableStateOf(false) }
        Surface(
            onClick = { menuOpen = true },
            enabled = menu != null,
            shape = MaterialTheme.shapes.medium,
            color = container,
            border = border,
        ) {
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
                if (showTime) {
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
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = if (showTime) 14.dp else 0.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            lesson.subject,
                            style = MaterialTheme.typography.titleSmall,
                            color = primaryText,
                            modifier = Modifier.weight(1f),
                        )
                        if (lesson.type != LessonType.OTHER) {
                            Spacer(Modifier.width(8.dp))
                            TypeChip(lesson.type, past = timing == LessonTiming.PAST)
                        }
                    }
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
                    if (homework.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        HomeworkLine(homework, if (timing == LessonTiming.PAST) faint else scheme.primary, secondaryText)
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
        // Меню раскрывается под карточкой (якорь — этот Box).
        menu?.invoke(lesson, menuOpen) { menuOpen = false }
    }
}

/** "ДЗ · Типовик 3 · +1": первое невыполненное задание; все сделаны — "ДЗ сделано ✓". */
@Composable
private fun HomeworkLine(homework: List<HomeworkItem>, accent: Color, muted: Color) {
    val open = homework.filter { !it.isDone }
    Text(
        buildAnnotatedString {
            if (open.isEmpty()) {
                withStyle(SpanStyle(color = muted)) { append("ДЗ сделано ✓") }
            } else {
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append("ДЗ · ") }
                withStyle(SpanStyle(color = accent)) { append(open.first().description.lineSequence().first()) }
                if (open.size > 1) withStyle(SpanStyle(color = muted)) { append(" · +${open.size - 1}") }
            }
        },
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "ЛЕК" / "ПР" / "ЛАБ" — вид занятия плашкой справа от названия. */
@Composable
private fun TypeChip(type: LessonType, past: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val text = when (type) {
        LessonType.LECTURE -> "ЛЕК"
        LessonType.PRACTICE -> "ПР"
        LessonType.LAB -> "ЛАБ"
        LessonType.OTHER -> return
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (past) scheme.outlineVariant.copy(alpha = 0.5f) else scheme.primaryContainer,
    ) {
        Text(
            text,
            style = MonoStyles.label.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp),
            color = if (past) GasuTheme.colors.textFaint else scheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
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
