package com.burton.slack.domain

object Mrkdwn {
    private val mention = Regex("<@([UW][A-Z0-9]+)(?:\\|([^>]+))?>")
    private val channel = Regex("<#([CG][A-Z0-9]+)(?:\\|([^>]+))?>")
    private val link = Regex("<([^>|]+)(?:\\|([^>]+))?>")
    private val bold = Regex("\\*([^*]+)\\*")
    private val italic = Regex("(?<![a-zA-Z0-9])_([^_]+)_(?![a-zA-Z0-9])")
    private val strike = Regex("~([^~]+)~")
    private val code = Regex("`([^`]+)`")

    fun display(
        text: String,
        users: Map<String, SlackUser> = emptyMap(),
        conversations: List<Conversation> = emptyList(),
    ): String {
        if (text.isBlank()) return text
        val byId = conversations.associateBy { it.id }
        var out = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        out = mention.replace(out) { match ->
            val id = match.groupValues[1]
            val label = match.groupValues[2].ifBlank { users[id]?.label ?: id }
            "@$label"
        }
        out = channel.replace(out) { match ->
            val id = match.groupValues[1]
            val label = match.groupValues[2].ifBlank { byId[id]?.name ?: id }
            "#$label"
        }
        out = link.replace(out) { match ->
            match.groupValues[2].ifBlank { match.groupValues[1] }
        }
        out = bold.replace(out) { it.groupValues[1] }
        out = italic.replace(out) { it.groupValues[1] }
        out = strike.replace(out) { it.groupValues[1] }
        out = code.replace(out) { it.groupValues[1] }
        return out.trim()
    }
}
