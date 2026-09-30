package com.burton.slack.data.slack

import com.burton.slack.data.parse.TinyJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlackAuthTest {
    @Test
    fun parsesUserTokenFromOauthV2Access() {
        val body = TinyJson.parseObject(
            """
            {
              "ok": true,
              "authed_user": {
                "id": "U123",
                "scope": "search:read,chat:write",
                "access_token": "xoxp-user",
                "token_type": "user",
                "refresh_token": "xoxe-1-refresh",
                "expires_in": 43200
              }
            }
            """.trimIndent(),
        )
        val auth = SlackAuth.fromOauth(body, nowMs = 1_000L)
        assertEquals("xoxp-user", auth.accessToken)
        assertEquals("xoxe-1-refresh", auth.refreshToken)
        assertEquals(1_000L + 43_200_000L, auth.expiresAtEpochMs)
        assertTrue(auth.isPresent)
    }

    @Test
    fun parsesTopLevelAccessTokenWhenAuthedUserIsMissing() {
        val body = TinyJson.parseObject(
            """{"ok":true,"access_token":"xoxp-legacy","refresh_token":"xoxe-legacy","expires_in":60}""",
        )
        val auth = SlackAuth.fromOauth(body, nowMs = 0L)
        assertEquals("xoxp-legacy", auth.accessToken)
        assertEquals("xoxe-legacy", auth.refreshToken)
        assertEquals(60_000L, auth.expiresAtEpochMs)
    }

    @Test
    fun storedAuthRoundTripAndRefreshWindow() {
        val stored = SlackAuth(
            accessToken = "xoxp-a",
            refreshToken = "xoxe-b",
            expiresAtEpochMs = 10_000_000L,
        )
        val restored = SlackAuth(
            accessToken = stored.accessToken,
            refreshToken = stored.refreshToken,
            expiresAtEpochMs = stored.expiresAtEpochMs,
        )
        assertEquals(stored, restored)
        val windowStart = 10_000_000L - SlackAuth.REFRESH_SKEW_MS
        assertFalse(restored.needsRefresh(nowMs = windowStart - 1))
        assertTrue(restored.needsRefresh(nowMs = windowStart))
        assertTrue(restored.needsRefresh(nowMs = 10_000_000L))
        assertFalse(SlackAuth(accessToken = "xoxp-a").needsRefresh(nowMs = 99_000L))
        assertTrue(
            SlackAuth(accessToken = "xoxp-a", refreshToken = "xoxe-b", expiresAtEpochMs = 0L)
                .needsRefresh(nowMs = 1L),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOauthResponseWithoutAccessToken() {
        SlackAuth.fromOauth(TinyJson.parseObject("""{"ok":true,"authed_user":{}}"""))
    }
}
