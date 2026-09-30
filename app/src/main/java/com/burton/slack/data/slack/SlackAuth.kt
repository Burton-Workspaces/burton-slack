package com.burton.slack.data.slack

import com.burton.slack.data.parse.TinyJson.int
import com.burton.slack.data.parse.TinyJson.obj
import com.burton.slack.data.parse.TinyJson.str

data class SlackAuth(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAtEpochMs: Long = 0L,
) {
    val isPresent: Boolean get() = accessToken.isNotBlank()

    fun needsRefresh(nowMs: Long, skewMs: Long = REFRESH_SKEW_MS): Boolean {
        if (refreshToken.isBlank()) return false
        if (expiresAtEpochMs <= 0L) return true
        return nowMs >= expiresAtEpochMs - skewMs
    }

    companion object {
        const val REFRESH_SKEW_MS = 5 * 60 * 1000L

        fun fromOauth(body: Map<String, Any?>, nowMs: Long = System.currentTimeMillis()): SlackAuth {
            val user = body.obj("authed_user")
            val access = user.str("access_token").ifBlank { body.str("access_token") }
            require(access.isNotBlank()) { "oauth_missing_access_token" }
            val refresh = user.str("refresh_token").ifBlank { body.str("refresh_token") }
            val expiresIn = user.int("expires_in").let { seconds ->
                if (seconds > 0) seconds else body.int("expires_in")
            }
            val expiresAt = if (expiresIn > 0) nowMs + expiresIn * 1000L else 0L
            return SlackAuth(
                accessToken = access,
                refreshToken = refresh,
                expiresAtEpochMs = expiresAt,
            )
        }
    }
}
