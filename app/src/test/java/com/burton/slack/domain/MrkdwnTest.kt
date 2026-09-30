package com.burton.slack.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MrkdwnTest {
    @Test
    fun replacesMentionsChannelsAndLinks() {
        val users = mapOf(
            "U1" to SlackUser("U1", "ada", "Ada", "Ada", "", false, false),
        )
        val channels = listOf(
            Conversation(
                id = "C1",
                name = "general",
                kind = ConversationKind.CHANNEL,
                topic = "",
                unread = 0,
                isMember = true,
                isStarred = false,
                latestText = "",
                latestTs = "",
                userId = "",
                imageUrl = "",
            ),
        )
        val raw = "<@U1> see <#C1|general> and <https://burton.work|Burton> *now* `code`"
        assertEquals("@Ada see #general and Burton now code", Mrkdwn.display(raw, users, channels))
    }

    @Test
    fun unescapesHtmlEntities() {
        assertEquals("a < b & c", Mrkdwn.display("a &lt; b &amp; c"))
    }
}
