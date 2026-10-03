package com.burton.slack.data.slack

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Pkce {
    const val REDIRECT_SCHEME = "burtonslack"
    const val REDIRECT_HOST = "oauth"
    const val REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST"
    const val AUTHORIZE_URL = "https://slack.com/oauth/v2/authorize"

    val USER_SCOPES = listOf(
        "channels:history",
        "channels:read",
        "groups:history",
        "groups:read",
        "im:history",
        "im:read",
        "im:write",
        "mpim:history",
        "mpim:read",
        "chat:write",
        "users:read",
        "search:read",
        "team:read",
        "reactions:read",
        "reactions:write",
        "stars:read",
        "files:read",
    )

    data class Challenge(
        val verifier: String,
        val challenge: String,
        val state: String,
    )

    fun generate(random: SecureRandom = SecureRandom()): Challenge {
        val verifier = randomUrlSafe(64, random)
        return Challenge(
            verifier = verifier,
            challenge = challengeS256(verifier),
            state = randomUrlSafe(32, random),
        )
    }

    fun challengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun authorizeUrl(clientId: String, challenge: Challenge): String {
        val query = listOf(
            "client_id" to clientId,
            "scope" to "",
            "user_scope" to USER_SCOPES.joinToString(","),
            "redirect_uri" to REDIRECT_URI,
            "code_challenge" to challenge.challenge,
            "code_challenge_method" to "S256",
            "state" to challenge.state,
        ).joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "$AUTHORIZE_URL?$query"
    }

    private fun randomUrlSafe(bytes: Int, random: SecureRandom): String {
        val buffer = ByteArray(bytes)
        random.nextBytes(buffer)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer)
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
