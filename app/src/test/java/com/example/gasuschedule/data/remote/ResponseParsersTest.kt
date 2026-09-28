package com.example.gasuschedule.data.remote

import com.example.gasuschedule.data.remote.dto.BitrixAjaxResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseParsersTest {

    @Test
    fun `успешный ответ - html`() {
        val r = BitrixAjaxResponse.parse(Fixtures.raspJson)
        assertTrue(r is BitrixAjaxResponse.Success)
        assertTrue((r as BitrixAjaxResponse.Success).html.contains("data-hash=\"week_1\""))
    }

    @Test
    fun `invalid_csrf - достаём свежий токен`() {
        val body = """{"status":"error","data":null,"errors":[{"message":"Invalid csrf token",
            "code":"invalid_csrf","customData":{"csrf":"c80cb77e034303f5b0745c0de121baee"}}]}"""
        assertEquals(
            BitrixAjaxResponse.InvalidCsrf("c80cb77e034303f5b0745c0de121baee"),
            BitrixAjaxResponse.parse(body),
        )
    }

    @Test
    fun `прочие ошибки и не-JSON`() {
        val body = """{"status":"error","data":null,"errors":[{"message":"Could not find value for parameter {search_params}","code":100}]}"""
        assertEquals(
            BitrixAjaxResponse.Error(listOf("Could not find value for parameter {search_params}")),
            BitrixAjaxResponse.parse(body),
        )
        assertTrue(BitrixAjaxResponse.parse("<html>502 Bad Gateway</html>") is BitrixAjaxResponse.Error)
    }

    @Test
    fun `главная страница - токен и список групп`() {
        assertEquals("438f4f3c2e94f4fe97fce25782b4c2de", MainPageParser.sessid(Fixtures.indexPage))
        val groups = MainPageParser.groups(Fixtures.indexPage)
        assertEquals(614, groups.size)
        assertTrue("3-ТТП-26" in groups)
        assertEquals("1-А-26", groups.first())
    }
}
