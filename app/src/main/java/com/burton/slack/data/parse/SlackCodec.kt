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
            latestText = message(latest)?.text ?: latest.str("text"),
            latestTs = latest.str("ts"),
            userId = userId,
            imageUrl = users[userId]?.imageUrl.orEmpty(),
        )
    }

    fun message(raw: Map<String, Any?>): SlackMessage? {
        val ts = raw.str("ts")
        if (ts.isBlank()) return null
        val fileObjs = raw.objList("files")
        val files = mutableListOf<SlackFile>()
        val seenUrls = mutableSetOf<String>()
        fun addFiles(incoming: List<SlackFile>) {
            incoming.forEach { file ->
                val key = file.previewUrl.ifBlank { file.id }
                if (seenUrls.add(key)) files += file
            }
        }
        addFiles(fileObjs.mapNotNull(::file))
        val blocks = flattenBlocks(raw.objList("blocks"))
        addFiles(blocks.images)
        val attachments = flattenAttachments(raw.objList("attachments"))
        addFiles(attachments.images)
        val postSnippets = fileObjs.map { file ->
            file.str("preview").ifBlank { file.str("plain_text") }
        }
        return SlackMessage(
            ts = ts,
            threadTs = raw.str("thread_ts"),
            userId = raw.str("user"),
            botId = raw.str("bot_id"),
            username = raw.str("username"),
            text = combineText(
                if (blocks.text.isNotBlank()) {
                    listOf(blocks.text, attachments.text) + postSnippets
                } else {
                    listOf(raw.str("text"), attachments.text) + postSnippets
                },
            ),
            subtype = raw.str("subtype"),
            replyCount = raw.int("reply_count"),
            latestReply = raw.str("latest_reply"),
            reactions = raw.objList("reactions").mapNotNull(::reaction),
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
        if (id.isBlank() || raw.str("mode") == "tombstone") return null
        val url = raw.str("url_private").ifBlank { raw.str("url_private_download") }
        val thumb = THUMB_KEYS.firstNotNullOfOrNull { key -> raw.str(key).takeIf { it.isNotBlank() } }.orEmpty()
        return SlackFile(
            id = id,
            name = raw.str("name"),
            title = raw.str("title").ifBlank { raw.str("name") },
            mimetype = raw.str("mimetype"),
            url = url,
            thumbUrl = thumb.ifBlank { url },
        )
    }

    private data class ExtractedContent(
        val text: String,
        val images: List<SlackFile>,
    )

    private fun flattenBlocks(blocks: List<Map<String, Any?>>): ExtractedContent {
        val texts = ArrayList<String>()
        val images = ArrayList<SlackFile>()
        blocks.forEach { block ->
            when (block.str("type")) {
                "header", "section", "markdown" -> {
                    texts += blockText(block)
                    block.objList("fields").forEach { field -> texts += blockText(field) }
                    imageFile(block.obj("accessory"))?.let { images += it }
                    imageFile(block)?.let { images += it }
                }
                "image" -> imageFile(block)?.let { images += it }
                "context" -> {
                    block.objList("elements").forEach { element ->
                        texts += blockText(element)
                        imageFile(element)?.let { images += it }
                    }
                }
                "rich_text" -> texts += flattenRichText(block)
                "video" -> {
                    texts += block.obj("title").str("text").ifBlank { block.str("title") }
                    imageFromUrl(block.str("thumbnail_url"), block.str("alt_text"))?.let { images += it }
                }
            }
        }
        return ExtractedContent(combineText(texts), images)
    }

    private fun flattenAttachments(attachments: List<Map<String, Any?>>): ExtractedContent {
        val texts = ArrayList<String>()
        val images = ArrayList<SlackFile>()
        attachments.forEach { attachment ->
            texts += attachment.str("title")
            texts += attachment.str("author_name")
            texts += attachment.str("pretext")
            texts += attachment.str("text")
            attachment.objList("fields").forEach { field ->
                val title = field.str("title")
                val value = field.str("value")
                texts += if (title.isNotBlank() && value.isNotBlank()) "*$title*\n$value" else value.ifBlank { title }
            }
            imageFromUrl(attachment.str("image_url"), attachment.str("title"))?.let { images += it }
            if (attachment.str("image_url").isBlank()) {
                imageFromUrl(attachment.str("thumb_url"), attachment.str("title"))?.let { images += it }
            }
        }
        return ExtractedContent(combineText(texts), images)
    }

    private fun blockText(raw: Map<String, Any?>): String {
        val text = raw["text"]
        return when (text) {
            is String -> text
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                (text as Map<String, Any?>).str("text")
            }
            else -> ""
        }
    }

    private fun flattenRichText(node: Map<String, Any?>): String {
        val out = StringBuilder()
        fun walk(value: Any?) {
            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val map = value as Map<String, Any?>
                    when (map.str("type")) {
                        "text" -> out.append(map.str("text"))
                        "user" -> out.append("<@").append(map.str("user_id")).append(">")
                        "channel" -> out.append("<#").append(map.str("channel_id")).append(">")
                        "link" -> out.append(map.str("text").ifBlank { map.str("url") })
                        "emoji" -> out.append(':').append(map.str("name")).append(':')
                        "broadcast" -> out.append('@').append(map.str("range").ifBlank { "channel" })
                        "rich_text_list" -> {
                            map.objList("elements").forEach { item ->
                                out.append("• ")
                                walk(item)
                                if (!out.endsWith('\n')) out.append('\n')
                            }
                        }
                        else -> {
                            walk(map["elements"])
                            walk(map["element"])
                        }
                    }
                }
                is List<*> -> value.forEach(::walk)
            }
        }
        walk(node)
        return out.toString().trim()
    }

    private fun imageFile(raw: Map<String, Any?>): SlackFile? {
        if (raw.isEmpty()) return null
        val type = raw.str("type")
        if (type.isNotBlank() && type != "image") return null
        val url = raw.str("image_url")
            .ifBlank { raw.obj("slack_file").str("url") }
            .ifBlank { raw.str("thumb_url") }
        val title = raw.obj("title").str("text").ifBlank { raw.str("alt_text") }.ifBlank { raw.str("title") }
        return imageFromUrl(url, title)
    }

    private fun imageFromUrl(url: String, title: String): SlackFile? {
        if (url.isBlank()) return null
        return SlackFile(
            id = url,
            name = title.ifBlank { "image" },
            title = title,
            mimetype = "image/*",
            url = url,
            thumbUrl = url,
        )
    }

    private fun combineText(parts: List<String>): String {
        val cleaned = parts.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        return cleaned
            .filter { part -> cleaned.none { other -> other != part && other.contains(part) } }
            .joinToString("\n\n")
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

    private val THUMB_KEYS = listOf("thumb_480", "thumb_360", "thumb_720", "thumb_160", "thumb_80", "thumb_64")
}
