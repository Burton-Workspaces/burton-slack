package com.burton.slack.data.parse

import com.burton.slack.data.parse.TinyJson.bool
import com.burton.slack.data.parse.TinyJson.int
import com.burton.slack.data.parse.TinyJson.obj
import com.burton.slack.data.parse.TinyJson.objList
import com.burton.slack.data.parse.TinyJson.str
import com.burton.slack.data.parse.TinyJson.strList
import com.burton.slack.domain.Conversation
import com.burton.slack.domain.ConversationKind
import com.burton.slack.domain.SearchHit
import com.burton.slack.domain.SlackFile
import com.burton.slack.domain.SlackMessage
import com.burton.slack.domain.SlackReaction
import com.burton.slack.domain.SlackUser
import com.burton.slack.domain.Workspace

object SlackCodec {
    fun workspace(auth: Map<String, Any?>, team: Map<String, Any?>, user: SlackUser?): Workspace {
        val teamObj = team.obj("team").ifEmpty { team }
        val icon = teamObj.obj("icon")
        return Workspace(
            id = teamObj.str("id").ifBlank { auth.str("team_id") },
            name = teamObj.str("name").ifBlank { auth.str("team") },
            domain = teamObj.str("domain"),
            iconUrl = icon.str("image_68").ifBlank { icon.str("image_44") },
            userId = auth.str("user_id"),
            userName = user?.label ?: auth.str("user"),
        )
    }

    fun user(raw: Map<String, Any?>): SlackUser? {
        val id = raw.str("id")
        if (id.isBlank()) return null
        val profile = raw.obj("profile")
        return SlackUser(
            id = id,
            name = raw.str("name"),
            realName = raw.str("real_name").ifBlank { profile.str("real_name") },
            displayName = profile.str("display_name_normalized").ifBlank { profile.str("display_name") },
            imageUrl = profile.str("image_72").ifBlank { profile.str("image_48") },
            isBot = raw.bool("is_bot") || raw.bool("is_app_user"),
            deleted = raw.bool("deleted"),
        )
    }

    fun conversation(raw: Map<String, Any?>, users: Map<String, SlackUser>): Conversation? {
        val id = raw.str("id")
        if (id.isBlank() || raw.bool("is_archived")) return null
        val kind = when {
            raw.bool("is_im") -> ConversationKind.IM
            raw.bool("is_mpim") -> ConversationKind.MPIM
            raw.bool("is_group") || raw.bool("is_private") -> ConversationKind.PRIVATE
            else -> ConversationKind.CHANNEL
        }
        val userId = raw.str("user")
        val latest = raw.obj("latest")
        val name = when (kind) {
            ConversationKind.IM -> users[userId]?.label ?: userId
            else -> raw.str("name").ifBlank { raw.str("name_normalized") }
        }
        return Conversation(
            id = id,
            name = name,
            kind = kind,
            topic = raw.obj("topic").str("value").ifBlank { raw.obj("purpose").str("value") },
            unread = raw.int("unread_count_display").takeIf { it > 0 } ?: raw.int("unread_count"),
            isMember = if (kind == ConversationKind.IM || kind == ConversationKind.MPIM) {
                true
            } else {
                raw.bool("is_member")
            },
            isStarred = raw.bool("is_starred"),
            latestText = latest.str("text"),
            latestTs = latest.str("ts"),
            userId = userId,
            imageUrl = users[userId]?.imageUrl.orEmpty(),
        )
    }

    fun message(raw: Map<String, Any?>): SlackMessage? {
        val ts = raw.str("ts")
        if (ts.isBlank()) return null
        val files = raw.objList("files").mapNotNull(::file)
        val reactions = raw.objList("reactions").mapNotNull(::reaction)
        return SlackMessage(
            ts = ts,
            threadTs = raw.str("thread_ts"),
            userId = raw.str("user"),
            botId = raw.str("bot_id"),
            username = raw.str("username"),
            text = raw.str("text"),
            subtype = raw.str("subtype"),
            replyCount = raw.int("reply_count"),
            latestReply = raw.str("latest_reply"),
            reactions = reactions,
            files = files,
            edited = raw["edited"] != null,
        )
    }

    fun searchHits(raw: Map<String, Any?>): List<SearchHit> {
        val messages = raw.obj("messages")
        return messages.objList("matches").mapNotNull { match ->
            val msg = message(match) ?: return@mapNotNull null
            val channel = match.obj("channel")
            SearchHit(
                channelId = channel.str("id").ifBlank { match.str("channel") },
                channelName = channel.str("name"),
                message = msg,
            )
        }
    }

    private fun file(raw: Map<String, Any?>): SlackFile? {
        val id = raw.str("id")
        if (id.isBlank()) return null
        return SlackFile(
            id = id,
            name = raw.str("name"),
            title = raw.str("title").ifBlank { raw.str("name") },
            mimetype = raw.str("mimetype"),
            url = raw.str("url_private").ifBlank { raw.str("url_private_download") },
            thumbUrl = raw.str("thumb_64").ifBlank { raw.str("thumb_80") },
        )
    }

    private fun reaction(raw: Map<String, Any?>): SlackReaction? {
        val name = raw.str("name")
        if (name.isBlank()) return null
        val users = raw.strList("users")
        return SlackReaction(
            name = name,
            count = raw.int("count", 1).coerceAtLeast(users.size),
            userIds = users,
        )
    }
}
