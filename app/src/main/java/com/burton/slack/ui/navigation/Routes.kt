package com.burton.slack.ui.navigation

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val CHANNEL = "channel/{channelId}"
    const val THREAD = "thread/{channelId}/{threadTs}"

    fun channel(channelId: String) = "channel/${enc(channelId)}"
    fun thread(channelId: String, threadTs: String) =
        "thread/${enc(channelId)}/${enc(threadTs)}"

    private fun enc(value: String) = android.net.Uri.encode(value)
}
