package com.example.gasuschedule.data.remote

import com.example.gasuschedule.domain.model.StudyGroup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Главная страница rasp.spbgasu.ru содержит всё, что нужно до запроса расписания:
 * - CSRF-токен: `'bitrix_sessid':'...'`;
 * - список всех групп: `window.GROUPS = [...]` (по нему сайт делает клиентский автокомплит);
 * - каталог: `FACULTY` {факультет: имя}, `FORMAT` {факультет: {уровень: имя}},
 *   `COURSE` {уровень: {курс: имя}}, `F_GROUP` {курс: {id: группа}} — все ключи md5-хэши.
 */
object MainPageParser {
    private val SESSID = Regex("""'bitrix_sessid'\s*:\s*'([0-9a-f]+)'""")

    fun sessid(html: String): String? = SESSID.find(html)?.groupValues?.get(1)

    fun groups(html: String): List<StudyGroup> {
        val names = jsValue(html, "GROUPS")?.jsonArray?.map { it.jsonPrimitive.content } ?: return emptyList()

        // Разворачиваем каталог в "группа -> (факультет, уровень, курс)". Если каталог не разобрался,
        // остаются просто имена — автокомплит от этого не ломается.
        val info = mutableMapOf<String, StudyGroup>()
        runCatching {
            val faculties = jsValue(html, "FACULTY").asStringMap()
            val formats = jsValue(html, "FORMAT").asNestedMap()
            val courses = jsValue(html, "COURSE").asNestedMap()
            val groups = jsValue(html, "F_GROUP").asNestedMap()
            for ((facultyId, faculty) in faculties) {
                for ((levelId, level) in formats[facultyId].orEmpty()) {
                    for ((courseId, course) in courses[levelId].orEmpty()) {
                        for (name in groups[courseId].orEmpty().values) {
                            info.putIfAbsent(name, StudyGroup(name, faculty, level, course))
                        }
                    }
                }
            }
        }
        return names.map { info[it] ?: StudyGroup(it) }
    }

    /** Значение `window.NAME = <json>;` — вырезаем по балансу скобок, объекты бывают вложенными. */
    private fun jsValue(html: String, name: String): JsonElement? {
        val m = Regex("""window\.$name\s*=\s*""").find(html) ?: return null
        var i = m.range.last + 1
        val open = html.getOrNull(i) ?: return null
        val close = when (open) { '[' -> ']'; '{' -> '}'; else -> return null }
        var depth = 0
        var inString = false
        val start = i
        while (i < html.length) {
            val c = html[i]
            if (inString) {
                if (c == '\\') i++ else if (c == '"') inString = false
            } else when (c) {
                '"' -> inString = true
                open -> depth++
                close -> if (--depth == 0) return Json.parseToJsonElement(html.substring(start, i + 1))
            }
            i++
        }
        return null
    }

    private fun JsonElement?.asStringMap(): Map<String, String> =
        (this as? JsonObject)?.mapValues { it.value.jsonPrimitive.content }.orEmpty()

    private fun JsonElement?.asNestedMap(): Map<String, Map<String, String>> =
        (this as? JsonObject)?.mapValues { it.value.asStringMap() }.orEmpty()
}
