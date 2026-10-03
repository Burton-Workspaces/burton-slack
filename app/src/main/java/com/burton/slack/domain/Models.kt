package com.burton.slack.domain

enum class ConversationKind {
    CHANNEL,
    PRIVATE,
    IM,
    MPIM,
}

data class Workspace(
    val id: String,
    val name: String,
    val domain: String,
    val iconUrl: String,
    val userId: String,
    val userName: String,
)

data class SlackUser(
    val id: String,
    val name: String,
    val realName: String,
    val displayName: String,
    val imageUrl: String,
    val isBot: Boolean,
    val deleted: Boolean,
) {
    val label: String
        get() = displayName.ifBlank { realName.ifBlank { name.ifBlank { id } } }

    fun matchRank(query: String): Int {
        val needle = query.trim()
        if (needle.isBlank()) return Int.MAX_VALUE
        return minOf(fieldRank(displayName, needle), fieldRank(realName, needle), fieldRank(name, needle))
    }

    private fun fieldRank(value: String, query: String): Int {
        val haystack = value.trim()
        if (haystack.isBlank()) return Int.MAX_VALUE
        return when {
            haystack.equals(query, ignoreCase = true) -> 0
            haystack.startsWith(query, ignoreCase = true) -> 1
            haystack.contains(query, ignoreCase = true) -> 2
            else -> Int.MAX_VALUE
        }
    }
}

data class Conversation(
    val id: String,
    val name: String,
    val kind: ConversationKind,
    val topic: String,
    val unread: Int,
    val isMember: Boolean,
    val isStarred: Boolean,
    val latestText: String,
    val latestTs: String,
    val userId: String,
    val imageUrl: String,
) {
    val isDirect: Boolean get() = kind == ConversationKind.IM || kind == ConversationKind.MPIM

    fun title(users: Map<String, SlackUser>): String = when (kind) {
        ConversationKind.IM -> users[userId]?.label ?: name.ifBlank { "Direct message" }
        ConversationKind.MPIM -> name.ifBlank { "Group message" }
        ConversationKind.PRIVATE -> name
        ConversationKind.CHANNEL -> name
    }

    fun prefix(): String = when (kind) {
        ConversationKind.CHANNEL -> "#"
        ConversationKind.PRIVATE -> "🔒"
        ConversationKind.IM, ConversationKind.MPIM -> ""
    }
}

data class SlackFile(
    val id: String,
    val name: String,
    val title: String,
    val mimetype: String,
    val url: String,
    val thumbUrl: String,
) {
    val previewUrl: String get() = thumbUrl.ifBlank { url }
    val isImage: Boolean
        get() = mimetype.startsWith("image/") ||
            name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    companion object {
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "heic", "bmp")
    }
}

data class SlackReaction(
    val name: String,
    val count: Int,
    val userIds: List<String>,
) {
    fun mine(userId: String) = userId.isNotBlank() && userId in userIds
}

data class SlackMessage(
    val ts: String,
    val threadTs: String,
    val userId: String,
    val botId: String,
    val username: String,
    val text: String,
    val subtype: String,
    val replyCount: Int,
    val latestReply: String,
    val reactions: List<SlackReaction>,
    val files: List<SlackFile>,
    val edited: Boolean,
) {
    val isThreadParent: Boolean get() = replyCount > 0
    val isReply: Boolean get() = threadTs.isNotBlank() && threadTs != ts
    val isSystem: Boolean get() = subtype.isNotBlank() && subtype != "bot_message" && subtype != "file_share"
}

data class SearchHit(
    val channelId: String,
    val channelName: String,
    val message: SlackMessage,
)

data class ChannelHistory(
    val channelId: String,
    val messages: List<SlackMessage>,
    val hasOlder: Boolean,
    val oldest: String,
    val loading: Boolean,
    val error: String?,
)

data class SlackSnapshot(
    val tokenPresent: Boolean = false,
    val workspace: Workspace? = null,
    val conversations: List<Conversation> = emptyList(),
    val users: Map<String, SlackUser> = emptyMap(),
    val scanning: Boolean = false,
    val error: String? = null,
) {
    val starred: List<Conversation> get() = conversations.filter { it.isStarred }
    val channels: List<Conversation> get() = conversations.filter { !it.isDirect && !it.isStarred }
    val directs: List<Conversation> get() = conversations.filter { it.isDirect && !it.isStarred }

    fun conversation(id: String): Conversation? = conversations.firstOrNull { it.id == id }

    fun userLabel(userId: String): String =
        users[userId]?.label ?: userId.ifBlank { "Unknown" }

    fun matchingUsers(query: String, limit: Int = 8): List<SlackUser> {
        val needle = query.trim()
        if (needle.isBlank()) return emptyList()
        return users.values
            .asSequence()
            .filter { !it.deleted && !it.isBot }
            .map { it to it.matchRank(needle) }
            .filter { it.second != Int.MAX_VALUE }
            .sortedWith(compareBy({ it.second }, { it.first.label.lowercase() }))
            .take(limit)
            .map { it.first }
            .toList()
    }
}
