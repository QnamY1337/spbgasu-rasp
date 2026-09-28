package com.example.gasuschedule.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Главная страница rasp.spbgasu.ru содержит всё, что нужно до запроса расписания:
 * CSRF-токен (`'bitrix_sessid':'...'`) и полный список групп (`window.GROUPS = [...]`),
 * по которому сайт делает клиентский автокомплит.
 */
object MainPageParser {
    private val SESSID = Regex("""'bitrix_sessid'\s*:\s*'([0-9a-f]+)'""")
    private val GROUPS = Regex("""window\.GROUPS\s*=\s*(\[.*?])\s*;""", RegexOption.DOT_MATCHES_ALL)

    fun sessid(html: String): String? = SESSID.find(html)?.groupValues?.get(1)

    fun groups(html: String): List<String> {
        val array = GROUPS.find(html)?.groupValues?.get(1) ?: return emptyList()
        return Json.parseToJsonElement(array).jsonArray.map { it.jsonPrimitive.content }
    }
}
