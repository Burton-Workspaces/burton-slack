package com.burton.slack.data.slack

import com.burton.slack.data.parse.TinyJson
import com.burton.slack.data.parse.TinyJson.bool
import com.burton.slack.data.parse.TinyJson.cursor
import com.burton.slack.data.parse.TinyJson.objList
import com.burton.slack.data.parse.TinyJson.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

class SlackApiException(val method: String, val code: String) : RuntimeException("$method: $code")

@Singleton
class SlackApi @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun call(
        token: String,
        method: String,
        params: Map<String, String> = emptyMap(),
    ): Map<String, Any?> = post(method, params, token)

    suspend fun callAnonymous(
        method: String,
        params: Map<String, String>,
    ): Map<String, Any?> = post(method, params, token = null)

    suspend fun exchangeOauth(params: Map<String, String>): SlackAuth =
        SlackAuth.fromOauth(callAnonymous("oauth.v2.access", params))

    suspend fun paged(
        token: String,
        method: String,
        listKey: String,
        params: Map<String, String>,
        limitPages: Int = 8,
    ): List<Map<String, Any?>> {
        val out = ArrayList<Map<String, Any?>>()
        var cursor = ""
        repeat(limitPages) {
            val page = call(
                token,
                method,
                params + if (cursor.isBlank()) emptyMap() else mapOf("cursor" to cursor),
            )
            out += page.objList(listKey)
            cursor = page.cursor()
            if (cursor.isBlank()) return out
        }
        return out
    }

    private suspend fun post(
        method: String,
        params: Map<String, String>,
        token: String?,
    ): Map<String, Any?> = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply {
            params.forEach { (key, value) -> add(key, value) }
        }.build()
        val builder = Request.Builder()
            .url("$HOST/$method")
            .post(body)
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful && text.isBlank()) {
                throw SlackApiException(method, "http_${response.code}")
            }
            val parsed = TinyJson.parseObject(text)
            if (!parsed.bool("ok")) {
                throw SlackApiException(method, parsed.str("error").ifBlank { "unknown_error" })
            }
            parsed
        }
    }

    companion object {
        const val HOST = "https://slack.com/api"
    }
}
