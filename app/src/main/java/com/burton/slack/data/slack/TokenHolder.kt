package com.burton.slack.data.slack

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenHolder @Inject constructor() {
    @Volatile
    var token: String = ""

    @Volatile
    var refreshToken: String = ""

    @Volatile
    var expiresAtEpochMs: Long = 0L

    fun snapshot(): SlackAuth = SlackAuth(
        accessToken = token,
        refreshToken = refreshToken,
        expiresAtEpochMs = expiresAtEpochMs,
    )

    fun apply(auth: SlackAuth) {
        token = auth.accessToken
        refreshToken = auth.refreshToken
        expiresAtEpochMs = auth.expiresAtEpochMs
    }

    fun clear() = apply(SlackAuth())
}
