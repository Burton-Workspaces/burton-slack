package com.burton.slack.data.slack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class PkceTest {
    @Test
    fun challengeMatchesSlackExample() {
        assertEquals(
            "ldMBaaWcQYtSATMV_IG8mf3wp7A6EW80arYoSW80ntU",
            Pkce.challengeS256("secretpassword"),
        )
    }

    @Test
    fun authorizeUrlRequestsUserScopesAndPkce() {
        val challenge = Pkce.Challenge(
            verifier = "secretpassword",
            challenge = Pkce.challengeS256("secretpassword"),
            state = "abc",
        )
        val url = URI(Pkce.authorizeUrl("123.456", challenge))
        val query = url.rawQuery.split("&").associate { part ->
            val (key, value) = part.split("=", limit = 2)
            key to java.net.URLDecoder.decode(value, Charsets.UTF_8)
        }
        assertEquals("123.456", query["client_id"])
        assertEquals("", query["scope"])
        assertEquals(Pkce.USER_SCOPES.joinToString(","), query["user_scope"])
        assertEquals(Pkce.REDIRECT_URI, query["redirect_uri"])
        assertEquals(challenge.challenge, query["code_challenge"])
        assertEquals("S256", query["code_challenge_method"])
        assertEquals("abc", query["state"])
        assertTrue(query["user_scope"].orEmpty().contains("chat:write"))
        assertTrue(query["user_scope"].orEmpty().contains("files:read"))
        assertFalse(query.containsKey("client_secret"))
    }

    @Test
    fun generateProducesUrlSafeVerifierAndMatchingChallenge() {
        val generated = Pkce.generate()
        assertEquals(Pkce.challengeS256(generated.verifier), generated.challenge)
        assertTrue(generated.verifier.length >= 43)
        assertTrue(generated.state.isNotBlank())
    }
}
