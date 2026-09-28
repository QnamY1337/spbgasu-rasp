package com.example.gasuschedule.data.remote

/** Реальные ответы rasp.spbgasu.ru, сохранённые 28.09.2026. */
object Fixtures {
    fun text(name: String): String =
        requireNotNull(javaClass.classLoader!!.getResource(name)) { "нет фикстуры $name" }
            .readText(Charsets.UTF_8)

    /** Полный JSON-ответ getRasp для группы 3-ТТП-26 (8 недель осеннего семестра). */
    val raspJson get() = text("rasp_3-TTP-26.json")

    /** Ответ getRasp для несуществующей группы: success, но без недель. */
    val notFoundJson get() = text("rasp_not_found.json")

    /** Главная страница: bitrix_sessid + window.GROUPS. */
    val indexPage get() = text("index_page.html")
}
