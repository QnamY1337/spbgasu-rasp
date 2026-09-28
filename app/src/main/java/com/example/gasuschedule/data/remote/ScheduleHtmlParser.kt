package com.example.gasuschedule.data.remote

import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.LessonType
import com.example.gasuschedule.domain.model.ScheduleWeek
import com.example.gasuschedule.domain.model.SemesterSchedule
import com.example.gasuschedule.domain.model.WeekParity
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ScheduleParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Разбирает HTML-фрагмент из ответа getRasp в расписание на семестр.
 *
 * Структура (проверено на реальном ответе, сентябрь 2026):
 * div.item[data-hash=week_N] > .time ("Неделя №N: Числитель с dd.MM.yyyy по dd.MM.yyyy")
 *   > div.days > .week_day (.date) + .lessons > div.lesson
 *     > .day_name ("<b>3 пара</b> 12:30-14:00", .ibfo_para)
 *     > .lesson_block > div(.lesson-name), div(группа), div(аудитории), div(преподаватели)
 * Подгруппы разделяются <br>: "421(1)/Г<br>421(2)/Г".
 */
class ScheduleHtmlParser {

    fun parse(html: String, groupName: String): SemesterSchedule {
        val doc = Jsoup.parseBodyFragment(html)
        val weeks = mutableListOf<ScheduleWeek>()
        val lessons = mutableListOf<Lesson>()

        for (item in doc.select("div.item[data-hash^=week_]")) {
            val week = parseWeekHeader(item)
            weeks += week
            for (day in item.select("div.days")) {
                val dateText = day.selectFirst(".week_day .date")?.text()
                    ?: throw ScheduleParseException("Неделя ${week.number}: день без даты")
                val date = parseDate(dateText)
                val dayLessons = day.select("div.lesson").map { parseLesson(it, date, week, groupName) }
                lessons += assignIds(dayLessons)
            }
        }

        if (weeks.isEmpty() && doc.selectFirst("div.owl-carousel") == null) {
            // Ни недель, ни контейнера карусели — вероятно, разметка сайта изменилась.
            throw ScheduleParseException("В ответе нет блока расписания (div.owl-carousel)")
        }
        return SemesterSchedule(groupName, weeks, lessons)
    }

    private fun parseWeekHeader(item: Element): ScheduleWeek {
        val text = item.selectFirst(".time")?.text()
            ?: throw ScheduleParseException("Нет заголовка недели в ${item.attr("data-hash")}")
        val m = WEEK_HEADER.find(text)
            ?: throw ScheduleParseException("Не распознан заголовок недели: \"$text\"")
        val (number, parityText, start, end) = m.destructured
        return ScheduleWeek(
            number = number.toInt(),
            parity = parseParity(parityText),
            startDate = parseDate(start),
            endDate = parseDate(end),
        )
    }

    private fun parseLesson(el: Element, date: LocalDate, week: ScheduleWeek, groupName: String): Lesson {
        val dayName = el.selectFirst(".day_name")
            ?: throw ScheduleParseException("$date: у пары нет блока .day_name")
        val numberText = dayName.selectFirst("b")?.text().orEmpty()
        val number = LESSON_NUMBER.find(numberText)?.groupValues?.get(1)?.toInt()
            ?: throw ScheduleParseException("$date: не распознан номер пары \"$numberText\"")
        val time = TIME_RANGE.find(dayName.ownText())
            ?: throw ScheduleParseException("$date, $number пара: не распознано время \"${dayName.ownText()}\"")
        val note = dayName.selectFirst(".ibfo_para")?.text()?.trim()?.ifEmpty { null }

        val block = el.selectFirst(".lesson_block")
            ?: throw ScheduleParseException("$date, $number пара: нет .lesson_block")
        val rawSubject = block.selectFirst(".lesson-name")?.text()?.trim()
            ?: throw ScheduleParseException("$date, $number пара: нет названия предмета")
        val (subject, type) = splitSubject(rawSubject)

        // Первый div — название, дальше по порядку: группа, аудитории, преподаватели.
        val fields = block.children().filter { it.tagName() == "div" }.drop(1)
        val rooms = fields.getOrNull(1)?.let { splitLines(it) }.orEmpty()
        val teachers = fields.getOrNull(2)?.let { splitLines(it) }
            .orEmpty()
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return Lesson(
            id = "",
            subject = subject,
            type = type,
            teacher = teachers.joinToString(", "),
            room = rooms.joinToString(", "),
            building = rooms.firstOrNull()?.let(::buildingCode),
            lessonNumber = number,
            startTime = parseTime(time.groupValues[1]),
            endTime = parseTime(time.groupValues[2]),
            dayOfWeek = date.dayOfWeek,
            date = date,
            weekNumber = week.number,
            weekParity = week.parity,
            groupName = groupName,
            rooms = rooms,
            teachers = teachers,
            note = note,
        )
    }

    /** В одном слоте может быть несколько занятий (разные подгруппы) — различаем их индексом. */
    private fun assignIds(dayLessons: List<Lesson>): List<Lesson> =
        dayLessons.groupBy { it.lessonNumber }.values.flatMap { slot ->
            slot.mapIndexed { i, l ->
                val base = "${l.groupName}|${l.date}|${l.lessonNumber}"
                l.copy(id = if (i == 0) base else "$base#$i")
            }
        }.sortedWith(compareBy({ it.lessonNumber }, { it.id }))

    /** Текст элемента, разбитый по <br> и переводам строк. */
    private fun splitLines(el: Element): List<String> {
        val sb = StringBuilder()
        el.traverse { node, _ ->
            when {
                node is TextNode -> sb.append(node.wholeText)
                node is Element && node.tagName() == "br" -> sb.append('\n')
            }
        }
        return sb.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    companion object {
        private val WEEK_HEADER =
            Regex("""Неделя\s*№\s*(\d+)\s*:\s*(\S+)\s+с\s+(\d{2}\.\d{2}\.\d{4})\s+по\s+(\d{2}\.\d{2}\.\d{4})""")
        private val LESSON_NUMBER = Regex("""(\d+)\s*пара""")
        private val TIME_RANGE = Regex("""(\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})""")
        private val TYPE_SUFFIX = Regex("""\s*\((л|пр|лаб)\.\)\s*$""")
        private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private val TIME = DateTimeFormatter.ofPattern("H:mm")

        fun parseParity(text: String): WeekParity = when {
            text.startsWith("Числ", ignoreCase = true) -> WeekParity.NUMERATOR
            text.startsWith("Знам", ignoreCase = true) -> WeekParity.DENOMINATOR
            else -> WeekParity.EVERY
        }

        fun splitSubject(raw: String): Pair<String, LessonType> {
            val m = TYPE_SUFFIX.find(raw) ?: return raw to LessonType.OTHER
            val type = when (m.groupValues[1]) {
                "л" -> LessonType.LECTURE
                "пр" -> LessonType.PRACTICE
                "лаб" -> LessonType.LAB
                else -> LessonType.OTHER
            }
            return raw.substring(0, m.range.first).trim() to type
        }

        // "712/С" -> "С", "406*/К" -> "К", "Актовый зал/Г" -> "Г"; без "/" — null.
        fun buildingCode(room: String): String? =
            room.substringAfterLast('/', "").trim().ifEmpty { null }

        private fun parseDate(s: String): LocalDate = try {
            LocalDate.parse(s.trim(), DATE)
        } catch (e: Exception) {
            throw ScheduleParseException("Не распознана дата \"$s\"", e)
        }

        private fun parseTime(s: String): LocalTime = LocalTime.parse(s, TIME)
    }
}
