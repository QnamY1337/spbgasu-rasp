package com.example.gasuschedule.domain.model

import com.example.gasuschedule.presentation.schedule.LessonTiming
import com.example.gasuschedule.presentation.schedule.lessonTimings
import com.example.gasuschedule.testutil.d
import com.example.gasuschedule.testutil.lesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class SubjectFilterTest {

    private val lecture = lesson(d(28), 1, subject = "Физика", type = LessonType.LECTURE)
    private val practice = lesson(d(28), 2, subject = "Физика", type = LessonType.PRACTICE)
    private val other = lesson(d(28), 3, subject = "История", type = LessonType.LECTURE)

    @Test
    fun `без лекций - отключены только лекции этого предмета`() {
        val filter = SubjectFilter().with("Физика", SubjectMode.NO_LECTURES)
        assertTrue(filter.isDisabled(lecture))
        assertFalse(filter.isDisabled(practice))
        assertFalse(filter.isDisabled(other))
    }

    @Test
    fun `выкл - отключены все пары предмета, вкл - фильтр снимается`() {
        val off = SubjectFilter().with("Физика", SubjectMode.OFF)
        assertEquals(listOf(other), off.enabled(listOf(lecture, practice, other)))
        assertEquals(SubjectFilter(), off.with("Физика", SubjectMode.ON))
    }

    @Test
    fun `отключённая пара не бывает текущей и следующей`() {
        val day = listOf(lecture, practice, other)
        val filter = SubjectFilter().with("Физика", SubjectMode.NO_LECTURES)
        val morning = lessonTimings(day, LocalDateTime.of(d(28), LocalTime.of(7, 30)), filter::isDisabled)
        assertEquals(LessonTiming.UPCOMING, morning[lecture.id])
        assertEquals(LessonTiming.NEXT, morning[practice.id])
        val during = lessonTimings(day, LocalDateTime.of(d(28), LocalTime.of(9, 30)), filter::isDisabled)
        assertEquals(LessonTiming.UPCOMING, during[lecture.id])
        assertEquals(LessonTiming.NEXT, during[practice.id])
    }
}
