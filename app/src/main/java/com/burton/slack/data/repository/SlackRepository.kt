package com.burton.slack.data.repository

import com.burton.slack.data.parse.SlackCodec
import com.burton.slack.data.parse.TinyJson.obj
import com.burton.slack.data.parse.TinyJson.objList
import com.burton.slack.data.parse.TinyJson.str
import com.burton.slack.data.slack.SlackApi
import com.burton.slack.data.slack.SlackApiException
import com.burton.slack.data.slack.TokenHolder
import com.burton.slack.domain.ChannelHistory
import com.burton.slack.domain.SearchHit
import com.burton.slack.domain.SlackMessage
import com.burton.slack.domain.SlackSnapshot
import com.burton.slack.domain.SlackUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SlackRepository @Inject constructor(
    private val api: SlackApi,
    private val prefs: LocalPrefs,
    private val tokens: TokenHolder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val hydrateLock = Mutex()

    private val _state = MutableStateFlow(SlackSnapshot())
    val state: StateFlow<SlackSnapshot> = _state.asStateFlow()

    private val _histories = MutableStateFlow<Map<String, ChannelHistory>>(emptyMap())
    val histories: StateFlow<Map<String, ChannelHistory>> = _histories.asStateFlow()

    private val _threads = MutableStateFlow<Map<String, ChannelHistory>>(emptyMap())
    val threads: StateFlow<Map<String, ChannelHistory>> = _threads.asStateFlow()

    private var started = false
    private var pollJob: Job? = null
    private var channelPoll: Job? = null
    private var openChannelId: String? = null
    private var usersLoadedAt = 0L

    fun start() {
        if (started) return
        started = true
        scope.launch {
            prefs.token.distinctUntilChanged().collect { token ->
                tokens.token = token
                if (token.isBlank()) {
                    pollJob?.cancel()
                    channelPoll?.cancel()
                    _state.value = SlackSnapshot()
                    _histories.value = emptyMap()
                    _threads.value = emptyMap()
                } else {
                    _state.update { it.copy(tokenPresent = true, scanning = true, error = null) }
                    runCatching { hydrate(forceUsers = true) }
                        .onFailure { fail(it) }
                    startPolling()
                }
            }
        }
    }

    suspend fun signIn(token: String) {
        val trimmed = token.trim()
        require(trimmed.isNotBlank()) { "Token is blank" }
        tokens.token = trimmed
        _state.update { it.copy(tokenPresent = true, scanning = true, error = null) }
        try {
            hydrate(token = trimmed, forceUsers = true)
            prefs.setToken(trimmed)
        } catch (error: Exception) {
            tokens.token = prefs.token.first()
            fail(error)
            throw error
        }
    }

    suspend fun signOut() {
        pollJob?.cancel()
        channelPoll?.cancel()
        tokens.token = ""
        prefs.clearToken()
        _state.value = SlackSnapshot()
        _histories.value = emptyMap()
        _threads.value = emptyMap()
    }

    fun refresh() {
        scope.launch {
            runCatching { hydrate(forceUsers = false) }.onFailure { fail(it) }
        }
    }

    fun openChannel(channelId: String) {
        openChannelId = channelId
        scope.launch {
            runCatching { loadHistory(channelId, older = false) }.onFailure { failHistory(channelId, it) }
            runCatching { markRead(channelId) }
        }
        channelPoll?.cancel()
        channelPoll = scope.launch {
            while (isActive) {
                delay(CHANNEL_POLL_MS)
                val id = openChannelId ?: continue
                runCatching { loadHistory(id, older = false) }
            }
        }
    }

    fun closeChannel(channelId: String) {
        if (openChannelId == channelId) {
            openChannelId = null
            channelPoll?.cancel()
        }
    }

    fun loadOlder(channelId: String) {
        scope.launch {
            runCatching { loadHistory(channelId, older = true) }.onFailure { failHistory(channelId, it) }
        }
    }

    fun openThread(channelId: String, threadTs: String) {
        scope.launch {
            runCatching { loadThread(channelId, threadTs) }.onFailure { error ->
                putThread(
                    channelId,
                    threadTs,
                    currentThread(channelId, threadTs).copy(loading = false, error = friendly(error)),
                )
            }
        }
    }

    suspend fun send(channelId: String, text: String, threadTs: String? = null) {
        val token = requireToken()
        val params = mutableMapOf(
            "channel" to channelId,
            "text" to text,
        )
        if (!threadTs.isNullOrBlank()) params["thread_ts"] = threadTs
        api.call(token, "chat.postMessage", params)
        loadHistory(channelId, older = false)
        if (!threadTs.isNullOrBlank()) loadThread(channelId, threadTs)
    }

    suspend fun toggleReaction(channelId: String, ts: String, emoji: String) {
        val token = requireToken()
        val me = _state.value.workspace?.userId.orEmpty()
        val message = _histories.value[channelId]?.messages?.firstOrNull { it.ts == ts }
            ?: _threads.value.values.flatMap { it.messages }.firstOrNull { it.ts == ts }
        val mine = message?.reactions?.any { it.name == emoji && it.mine(me) } == true
        val method = if (mine) "reactions.remove" else "reactions.add"
        runCatching {
            api.call(
                token,
                method,
                mapOf("channel" to channelId, "timestamp" to ts, "name" to emoji),
            )
        }
        loadHistory(channelId, older = false)
        message?.threadTs?.takeIf { it.isNotBlank() }?.let { loadThread(channelId, it) }
    }

    suspend fun search(query: String): List<SearchHit> {
        val token = requireToken()
        if (query.isBlank()) return emptyList()
        val body = api.call(
            token,
            "search.messages",
            mapOf("query" to query, "count" to "40", "sort" to "timestamp"),
        )
        return SlackCodec.searchHits(body)
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(LIST_POLL_MS)
                runCatching { hydrate(forceUsers = false) }
            }
        }
    }

    private suspend fun hydrate(token: String = requireToken(), forceUsers: Boolean = false) {
        hydrateLock.withLock {
            _state.update { it.copy(scanning = it.workspace == null, tokenPresent = true) }
            val auth = api.call(token, "auth.test")
            val users = loadUsers(token, forceUsers)
            val team = runCatching { api.call(token, "team.info") }.getOrDefault(emptyMap())
            val me = users[auth.str("user_id")]
            val workspace = SlackCodec.workspace(auth, team, me)
            val conversations = loadConversations(token, users)
            _state.value = SlackSnapshot(
                tokenPresent = true,
                workspace = workspace,
                conversations = conversations,
                users = users,
                scanning = false,
                error = null,
            )
        }
    }

    private suspend fun loadUsers(token: String, force: Boolean): Map<String, SlackUser> {
        val cached = _state.value.users
        val fresh = force || cached.isEmpty() || System.currentTimeMillis() - usersLoadedAt > USERS_TTL_MS
        if (!fresh) return cached
        val members = api.paged(
            token,
            "users.list",
            "members",
            mapOf("limit" to "200"),
        )
        val users = members.mapNotNull(SlackCodec::user).associateBy { it.id }
        usersLoadedAt = System.currentTimeMillis()
        return users
    }

    private suspend fun loadConversations(
        token: String,
        users: Map<String, SlackUser>,
    ) = api.paged(
        token,
        "conversations.list",
        "channels",
        mapOf(
            "types" to "public_channel,private_channel,mpim,im",
            "exclude_archived" to "true",
            "limit" to "200",
        ),
    ).mapNotNull { SlackCodec.conversation(it, users) }
        .filter { it.isMember }
        .sortedWith(
            compareByDescending<com.burton.slack.domain.Conversation> { it.isStarred }
                .thenByDescending { it.unread }
                .thenBy { it.title(users).lowercase() },
        )

    private suspend fun loadHistory(channelId: String, older: Boolean) {
        val token = requireToken()
        val current = _histories.value[channelId] ?: ChannelHistory(
            channelId = channelId,
            messages = emptyList(),
            hasOlder = true,
            oldest = "",
            loading = true,
            error = null,
        )
        if (older && !current.hasOlder) return
        putHistory(current.copy(loading = true, error = null))
        val params = mutableMapOf(
            "channel" to channelId,
            "limit" to if (older) "40" else "80",
            "inclusive" to "true",
        )
        if (older && current.oldest.isNotBlank()) params["latest"] = current.oldest
        val body = api.call(token, "conversations.history", params)
        val incoming = body.objList("messages").mapNotNull(SlackCodec::message)
            .sortedBy { it.ts }
        val merged = if (older) {
            (incoming + current.messages).distinctBy { it.ts }.sortedBy { it.ts }
        } else {
            mergeNewer(current.messages, incoming)
        }
        val oldest = merged.firstOrNull()?.ts.orEmpty()
        putHistory(
            ChannelHistory(
                channelId = channelId,
                messages = merged,
                hasOlder = body["has_more"] == true,
                oldest = oldest,
                loading = false,
                error = null,
            ),
        )
    }

    private suspend fun loadThread(channelId: String, threadTs: String) {
        val token = requireToken()
        val key = threadKey(channelId, threadTs)
        putThread(
            channelId,
            threadTs,
            currentThread(channelId, threadTs).copy(loading = true, error = null),
        )
        val body = api.call(
            token,
            "conversations.replies",
            mapOf("channel" to channelId, "ts" to threadTs, "limit" to "80"),
        )
        val messages = body.objList("messages").mapNotNull(SlackCodec::message).sortedBy { it.ts }
        putThread(
            channelId,
            threadTs,
            ChannelHistory(
                channelId = key,
                messages = messages,
                hasOlder = body["has_more"] == true,
                oldest = messages.firstOrNull()?.ts.orEmpty(),
                loading = false,
                error = null,
            ),
        )
    }

    private suspend fun markRead(channelId: String) {
        val token = requireToken()
        val ts = _histories.value[channelId]?.messages?.lastOrNull()?.ts ?: return
        runCatching {
            api.call(token, "conversations.mark", mapOf("channel" to channelId, "ts" to ts))
        }
    }

    private fun mergeNewer(existing: List<SlackMessage>, incoming: List<SlackMessage>): List<SlackMessage> {
        if (existing.isEmpty()) return incoming
        val byTs = existing.associateBy { it.ts }.toMutableMap()
        incoming.forEach { byTs[it.ts] = it }
        return byTs.values.sortedBy { it.ts }
    }

    private fun putHistory(history: ChannelHistory) {
        _histories.update { it + (history.channelId to history) }
    }

    private fun putThread(channelId: String, threadTs: String, history: ChannelHistory) {
        _threads.update { it + (threadKey(channelId, threadTs) to history) }
    }

    private fun currentThread(channelId: String, threadTs: String) =
        _threads.value[threadKey(channelId, threadTs)] ?: ChannelHistory(
            channelId = threadKey(channelId, threadTs),
            messages = emptyList(),
            hasOlder = false,
            oldest = "",
            loading = true,
            error = null,
        )

    private fun failHistory(channelId: String, error: Throwable) {
        val current = _histories.value[channelId]
        putHistory(
            (current ?: ChannelHistory(channelId, emptyList(), false, "", false, null))
                .copy(loading = false, error = friendly(error)),
        )
    }

    private fun fail(error: Throwable) {
        _state.update {
            it.copy(scanning = false, error = friendly(error), tokenPresent = tokens.token.isNotBlank())
        }
    }

    private fun requireToken(): String {
        val token = tokens.token.ifBlank { _state.value.let { if (it.tokenPresent) tokens.token else "" } }
        check(token.isNotBlank()) { "not_authed" }
        return token
    }

    private fun friendly(error: Throwable): String {
        val code = (error as? SlackApiException)?.code ?: error.message.orEmpty()
        return when (code) {
            "invalid_auth", "not_authed", "token_revoked", "token_expired" ->
                "That token was rejected. Create a user token and try again."
            "missing_scope" -> "The token is missing a required Slack scope."
            "ratelimited" -> "Slack rate-limited this phone. Wait a moment and retry."
            "channel_not_found" -> "That conversation is gone or hidden from this token."
            else -> error.message ?: "Slack request failed."
        }
    }

    companion object {
        private const val LIST_POLL_MS = 4_000L
        private const val CHANNEL_POLL_MS = 3_000L
        private const val USERS_TTL_MS = 10 * 60 * 1000L

        fun threadKey(channelId: String, threadTs: String) = "$channelId|$threadTs"
    }
}
