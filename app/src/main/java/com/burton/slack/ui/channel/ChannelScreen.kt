package com.burton.slack.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burton.slack.domain.SlackMessage
import com.burton.slack.ui.components.BurtonModalSheet
import com.burton.slack.ui.components.ComposeBar
import com.burton.slack.ui.components.MessageRow
import com.burton.slack.ui.components.MessagesSkeleton
import com.burton.slack.ui.components.compactWith
import com.burton.slack.ui.theme.BurtonIvory
import com.burton.slack.ui.theme.BurtonMute
import com.burton.slack.ui.theme.BurtonSand
import com.burton.slack.ui.theme.BurtonVoid

val QuickReactions = listOf("thumbsup", "heart", "eyes", "white_check_mark", "tada")

@Composable
fun ChannelScreen(
    onBack: () -> Unit,
    onOpenThread: (channelId: String, threadTs: String) -> Unit,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle(initialValue = null)
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val conversation = snapshot.conversation(viewModel.channelId)
    val title = conversation?.let { "${it.prefix()}${it.title(snapshot.users)}".trim() } ?: "Channel"
    var reacting by remember { mutableStateOf<SlackMessage?>(null) }
    val listState = rememberLazyListState()
    val messages = history?.messages.orEmpty()
    LaunchedEffect(messages.lastOrNull()?.ts) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = BurtonIvory)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = BurtonIvory,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    conversation?.topic?.ifBlank { "${messages.size} messages" } ?: "Loading",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BurtonMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            history == null || (history?.loading == true && messages.isEmpty()) -> {
                MessagesSkeleton()
                Spacer(Modifier.weight(1f))
            }
            history?.error != null && messages.isEmpty() -> {
                Text(history?.error ?: "", color = BurtonIvory)
                TextButton(onClick = { viewModel.loadOlder() }) {
                    Text("Retry", color = BurtonSand)
                }
                Spacer(Modifier.weight(1f))
            }
            messages.isEmpty() -> {
                Text("Nothing in this conversation yet.", color = BurtonMute)
                Spacer(Modifier.weight(1f))
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (history?.hasOlder == true) {
                        item(key = "older") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (history?.loading == true) {
                                    CircularProgressIndicator(color = BurtonSand)
                                } else {
                                    TextButton(onClick = viewModel::loadOlder) {
                                        Text("Load older", color = BurtonSand)
                                    }
                                }
                            }
                        }
                    }
                    itemsIndexed(messages, key = { _, message -> message.ts }) { index, message ->
                        MessageRow(
                            message = message,
                            snapshot = snapshot,
                            compact = compactWith(messages.getOrNull(index - 1), message),
                            onOpenThread = {
                                onOpenThread(viewModel.channelId, message.threadTs.ifBlank { message.ts })
                            }.takeIf { message.isThreadParent },
                            onReact = { reacting = message },
                        )
                    }
                }
            }
        }
        if (notice != null) {
            Text(notice ?: "", color = BurtonIvory, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = viewModel::clearNotice) { Text("Dismiss", color = BurtonSand) }
        }
        ComposeBar(
            value = draft,
            onValueChange = viewModel::onDraft,
            onSend = viewModel::send,
            placeholder = "Message $title",
            enabled = !busy,
        )
    }
    reacting?.let { message ->
        ReactionSheet(
            onDismiss = { reacting = null },
            onPick = { emoji ->
                viewModel.react(message.ts, emoji)
                reacting = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionSheet(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    BurtonModalSheet(onDismiss = onDismiss) {
        Text("React", style = MaterialTheme.typography.headlineMedium, color = BurtonIvory)
        Spacer(Modifier.height(4.dp))
        Text("Tap a name to add or remove it.", style = MaterialTheme.typography.bodyMedium, color = BurtonMute)
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickReactions.forEach { name ->
                Text(
                    ":$name:",
                    color = BurtonVoid,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .background(BurtonSand, RoundedCornerShape(14.dp))
                        .clickable { onPick(name) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}
