package com.burton.slack.data.parse

import com.burton.slack.domain.ConversationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlackCodecTest {
    @Test
    fun parsesUserAndConversation() {
        val user = SlackCodec.user(
            TinyJson.parseObject(
                """{"id":"U1","name":"ada","real_name":"Ada Lovelace","profile":{"display_name":"Ada","image_72":"https://img"}}""",
            ),
        )!!
        assertEquals("Ada", user.label)
        val channel = SlackCodec.conversation(
            TinyJson.parseObject(
                """{"id":"C1","name":"general","is_channel":true,"is_member":true,"unread_count_display":3,"topic":{"value":"hi"},"latest":{"text":"hello","ts":"1.0"}}""",
            ),
            mapOf(user.id to user),
        )!!
        assertEquals(ConversationKind.CHANNEL, channel.kind)
        assertEquals("general", channel.name)
        assertEquals(3, channel.unread)
        assertEquals("hello", channel.latestText)
    }

    @Test
    fun parsesDirectMessageNameFromUser() {
        val user = SlackCodec.user(
            TinyJson.parseObject("""{"id":"U2","name":"bob","profile":{"display_name":"Bob"}}"""),
        )!!
        val dm = SlackCodec.conversation(
            TinyJson.parseObject("""{"id":"D1","is_im":true,"user":"U2"}"""),
            mapOf(user.id to user),
        )!!
        assertEquals(ConversationKind.IM, dm.kind)
        assertEquals("Bob", dm.name)
        assertTrue(dm.isDirect)
    }

    @Test
    fun parsesMessageReactionsAndFiles() {
        val message = SlackCodec.message(
            TinyJson.parseObject(
                """{"ts":"10.1","user":"U1","text":"hello","reply_count":2,"thread_ts":"10.1","reactions":[{"name":"thumbsup","count":2,"users":["U1","U2"]}],"files":[{"id":"F1","name":"a.png","title":"Shot","mimetype":"image/png","url_private":"https://files.slack.com/a"}]}""",
            ),
        )!!
        assertEquals("10.1", message.ts)
        assertTrue(message.isThreadParent)
        assertFalse(message.isReply)
        assertEquals(1, message.reactions.size)
        assertTrue(message.reactions[0].mine("U1"))
        assertFalse(message.reactions[0].mine("U9"))
        assertEquals("Shot", message.files.single().title)
    }

    @Test
    fun parsesSearchHits() {
        val hits = SlackCodec.searchHits(
            TinyJson.parseObject(
                """{"ok":true,"messages":{"matches":[{"ts":"1.0","text":"found","user":"U1","channel":{"id":"C1","name":"general"}}]}}""",
            ),
        )
        assertEquals(1, hits.size)
        assertEquals("C1", hits[0].channelId)
        assertEquals("general", hits[0].channelName)
        assertEquals("found", hits[0].message.text)
    }

    @Test
    fun parsesWorkspace() {
        val workspace = SlackCodec.workspace(
            TinyJson.parseObject("""{"user_id":"U1","user":"ada","team":"Burton","team_id":"T1"}"""),
            TinyJson.parseObject("""{"team":{"id":"T1","name":"Burton","domain":"burton","icon":{"image_68":"https://icon"}}}"""),
            null,
        )
        assertEquals("T1", workspace.id)
        assertEquals("Burton", workspace.name)
        assertEquals("burton", workspace.domain)
        assertEquals("https://icon", workspace.iconUrl)
        assertEquals("ada", workspace.userName)
    }
}
