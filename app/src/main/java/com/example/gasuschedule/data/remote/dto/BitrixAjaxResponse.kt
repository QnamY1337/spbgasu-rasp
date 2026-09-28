package com.example.gasuschedule.data.remote.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Ответ Bitrix `ajax.php?mode=class`:
 * успех — `{"status":"success","data":{"html":"..."}}`,
 * ошибка — `{"status":"error","errors":[{"message":..,"code":..,"customData":{"csrf":".."}}]}`.
 */
sealed interface BitrixAjaxResponse {
    data class Success(val html: String) : BitrixAjaxResponse

    /** Сессия/токен устарели. Bitrix присылает свежий токен — можно повторить запрос с ним. */
    data class InvalidCsrf(val freshToken: String?) : BitrixAjaxResponse

    data class Error(val messages: List<String>) : BitrixAjaxResponse

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String): BitrixAjaxResponse {
            val root = try {
                json.parseToJsonElement(body).jsonObject
            } catch (e: Exception) {
                return Error(listOf("Ответ не JSON: ${body.take(120)}"))
            }
            val status = root["status"]?.jsonPrimitive?.contentOrNull
            if (status == "success") {
                val html = (root["data"] as? JsonObject)?.get("html")?.jsonPrimitive?.contentOrNull
                return if (html != null) Success(html) else Error(listOf("Нет data.html в ответе"))
            }
            val errors = (root["errors"]?.jsonArray ?: emptyList()).mapNotNull { it as? JsonObject }
            errors.firstOrNull { it["code"]?.jsonPrimitive?.contentOrNull == "invalid_csrf" }?.let { err ->
                val token = (err["customData"] as? JsonObject)?.get("csrf")?.jsonPrimitive?.contentOrNull
                return InvalidCsrf(token)
            }
            return Error(errors.mapNotNull { it["message"]?.jsonPrimitive?.contentOrNull }
                .ifEmpty { listOf("status=$status") })
        }
    }
}
