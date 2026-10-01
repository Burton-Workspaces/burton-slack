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
        assertTrue(message.files.single().isImage)
        assertEquals("https://files.slack.com/a", message.files.single().previewUrl)
    }

    @Test
    fun prefersLargerFileThumbs() {
        val message = SlackCodec.message(
            TinyJson.parseObject(
                """{"ts":"1.0","text":"pic","files":[{"id":"F1","name":"a.jpg","mimetype":"image/jpeg","url_private":"https://files.slack.com/full","thumb_64":"https://files.slack.com/64","thumb_360":"https://files.slack.com/360"}]}""",
            ),
        )!!
        assertEquals("https://files.slack.com/360", message.files.single().previewUrl)
    }

    @Test
    fun readsBlockKitBackblastDetailsAndImages() {
        val message = SlackCodec.message(
            TinyJson.parseObject(
                """
                {
                  "ts":"20.1",
                  "user":"U1",
                  "username":"Slackblast",
                  "text":"Backblast posted",
                  "blocks":[
                    {"type":"header","text":{"type":"plain_text","text":"Backblast: The Dark Side"}},
                    {"type":"section","fields":[
                      {"type":"mrkdwn","text":"*When:*\n2024-01-15"},
                      {"type":"mrkdwn","text":"*Q:*\n<@U1>"}
                    ]},
                    {"type":"section","text":{"type":"mrkdwn","text":"PAX grinded the coupons."}},
                    {"type":"image","image_url":"https://i.imgur.com/pax.jpg","alt_text":"PAX"}
                  ]
                }
                """.trimIndent(),
            ),
        )!!
        assertTrue(message.text.contains("Backblast: The Dark Side"))
        assertTrue(message.text.contains("When:"))
        assertTrue(message.text.contains("<@U1>"))
        assertTrue(message.text.contains("PAX grinded the coupons."))
        assertFalse(message.text.contains("Backblast posted"))
        assertEquals("https://i.imgur.com/pax.jpg", message.files.single().previewUrl)
        assertTrue(message.files.single().isImage)
    }

    @Test
    fun readsAttachmentUnfurlsAndPostPreview() {
        val message = SlackCodec.message(
            TinyJson.parseObject(
                """
                {
                  "ts":"21.0",
                  "text":"check this",
                  "attachments":[{"title":"Strava","text":"5.2 miles","image_url":"https://slack-imgs.com/run.png"}],
                  "files":[{"id":"F9","name":"notes","filetype":"post","title":"Preblast","preview":"Meet at the flag at 0530"}]
                }
                """.trimIndent(),
            ),
        )!!
        assertTrue(message.text.contains("Strava"))
        assertTrue(message.text.contains("5.2 miles"))
        assertTrue(message.text.contains("Meet at the flag at 0530"))
        assertEquals("https://slack-imgs.com/run.png", message.files.single { it.isImage }.previewUrl)
    }

    @Test
    fun conversationLatestUsesBlockText() {
        val channel = SlackCodec.conversation(
            TinyJson.parseObject(
                """{"id":"C9","name":"backblasts","is_channel":true,"is_member":true,"latest":{"ts":"3.0","text":"posted","blocks":[{"type":"header","text":{"type":"plain_text","text":"Backblast: Asylum"}}]}}""",
            ),
            emptyMap(),
        )!!
        assertEquals("Backblast: Asylum", channel.latestText)
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
