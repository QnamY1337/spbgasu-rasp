package com.example.gasuschedule.data.remote

import com.example.gasuschedule.data.remote.dto.BitrixAjaxResponse
import com.example.gasuschedule.domain.model.ScheduleNetworkException
import com.example.gasuschedule.domain.model.SemesterSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Клиент AJAX-компонента `gasu:raspisanie.csv`.
 *
 * Эндпоинт требует CSRF: заголовок `X-Bitrix-Csrf-Token` + сессионная cookie PHPSESSID.
 * Токен берём с главной страницы (`bitrix_sessid`); если он протух, Bitrix возвращает
 * `invalid_csrf` со свежим токеном в customData — повторяем запрос один раз.
 * Тело — только form-urlencoded (`search_params[SEARCH]=...`); JSON-тело Bitrix не разбирает.
 */
class BitrixScheduleSource(
    baseUrl: String = DEFAULT_BASE_URL,
    client: OkHttpClient? = null,
    private val parser: ScheduleHtmlParser = ScheduleHtmlParser(),
) : ScheduleRemoteSource {

    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val http: OkHttpClient = (client?.newBuilder() ?: OkHttpClient.Builder())
        .cookieJar(InMemoryCookieJar())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mutex = Mutex()
    private var csrfToken: String? = null
    private var cachedGroups: List<String>? = null

    override suspend fun fetchSchedule(groupName: String): SemesterSchedule = withContext(Dispatchers.IO) {
        val html = requestSchedule(groupName)
        parser.parse(html, groupName)
    }

    override suspend fun fetchGroups(): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            cachedGroups ?: loadMainPage().let { cachedGroups }.orEmpty()
        }
    }

    private suspend fun requestSchedule(groupName: String): String {
        var token = mutex.withLock { csrfToken ?: loadMainPage() }
        repeat(2) { attempt ->
            when (val resp = BitrixAjaxResponse.parse(postGetRasp(groupName, token))) {
                is BitrixAjaxResponse.Success -> return resp.html
                is BitrixAjaxResponse.InvalidCsrf -> {
                    if (attempt == 1) throw ScheduleNetworkException("Сайт отклонил CSRF-токен")
                    token = mutex.withLock {
                        csrfToken = resp.freshToken ?: loadMainPage()
                        csrfToken!!
                    }
                }
                is BitrixAjaxResponse.Error ->
                    throw ScheduleNetworkException("Ошибка сайта: ${resp.messages.first().lineSequence().first()}")
            }
        }
        error("unreachable")
    }

    /** GET главной страницы: инициализирует сессию, достаёт токен и список групп. Возвращает токен. */
    private fun loadMainPage(): String {
        val body = execute(Request.Builder().url(base).header("User-Agent", USER_AGENT).get().build())
        cachedGroups = MainPageParser.groups(body).ifEmpty { cachedGroups }
        return MainPageParser.sessid(body)?.also { csrfToken = it }
            ?: throw ScheduleNetworkException("На главной странице нет bitrix_sessid")
    }

    private fun postGetRasp(groupName: String, token: String): String {
        val url = base.newBuilder()
            .addPathSegments("bitrix/services/main/ajax.php")
            .addQueryParameter("mode", "class")
            .addQueryParameter("c", "gasu:raspisanie.csv")
            .addQueryParameter("action", "getRasp")
            .build()
        val form = FormBody.Builder()
            .add("search_params[SEARCH]", groupName)
            .add("search_params[FILTER]", "GROUPS")
            .add("search_params[GROUP]", "")
            .add("search_params[SELECT]", "*")
            .add("search_params[ONLY_SESSIA]", "N")
            .build()
        return execute(
            Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("X-Bitrix-Csrf-Token", token)
                .post(form)
                .build()
        )
    }

    private fun execute(request: Request): String = try {
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw ScheduleNetworkException("HTTP ${resp.code} от ${request.url.host}")
            resp.body.string()
        }
    } catch (e: IOException) {
        throw ScheduleNetworkException("Нет связи с сайтом расписания", e)
    }

    private class InMemoryCookieJar : CookieJar {
        private val cookies = mutableMapOf<String, Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { this.cookies["${it.domain}|${it.path}|${it.name}"] = it }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            cookies.values.removeAll { it.expiresAt < now }
            return cookies.values.filter { it.matches(url) }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://rasp.spbgasu.ru/"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36"
    }
}
