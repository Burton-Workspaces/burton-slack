package com.burton.slack.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.burton.slack.domain.Conversation
import com.burton.slack.domain.ConversationKind
import com.burton.slack.domain.Mrkdwn
import com.burton.slack.domain.SlackSnapshot
import com.burton.slack.ui.components.ConversationsSkeleton
import com.burton.slack.ui.components.UserAvatar
import com.burton.slack.ui.settings.SettingsModal
import com.burton.slack.ui.theme.BurtonCharcoal
import com.burton.slack.ui.theme.BurtonIvory
import com.burton.slack.ui.theme.BurtonMute
import com.burton.slack.ui.theme.BurtonSand

@Composable
fun HomeScreen(
    onOpenChannel: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineLarge,
                color = BurtonIvory,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = BurtonIvory)
            }
        }
        Text(
            text = when {
                snapshot.workspace == null && snapshot.scanning -> "Connecting to Slack"
                snapshot.workspace == null -> "Sign in with a workspace token"
                snapshot.conversations.isEmpty() -> "No conversations yet"
                else -> "${snapshot.workspace?.name} · ${snapshot.conversations.size} conversations"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = BurtonMute,
        )
        Spacer(Modifier.height(16.dp))
        when {
            snapshot.workspace == null && (snapshot.scanning || snapshot.error == null) -> ConversationsSkeleton()
            snapshot.workspace == null -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = snapshot.error ?: "Could not load the workspace.",
                        color = BurtonIvory,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = viewModel::refresh) {
                        Text("Retry", color = BurtonSand)
                    }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (snapshot.error != null) {
                        item(key = "error") {
                            Column {
                                Text(snapshot.error ?: "", color = BurtonIvory, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = viewModel::refresh) {
                                    Text("Retry", color = BurtonSand)
                                }
                            }
                        }
                    }
                    section(snapshot.starred, "Starred", snapshot, onOpenChannel)
                    section(snapshot.channels, "Channels", snapshot, onOpenChannel)
                    section(snapshot.directs, "Direct messages", snapshot, onOpenChannel)
                    if (snapshot.conversations.isEmpty()) {
                        item(key = "empty") {
                            Text("Nothing in this workspace yet.", color = BurtonMute)
                        }
                    }
                }
            }
        }
    }
    if (showSettings) {
        SettingsModal(onDismiss = { showSettings = false })
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    items: List<Conversation>,
    title: String,
    snapshot: SlackSnapshot,
    onOpenChannel: (String) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header-$title") {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = BurtonMute,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
    }
    items(items, key = { it.id }) { conversation ->
        ConversationRow(
            conversation = conversation,
            snapshot = snapshot,
            onClick = { onOpenChannel(conversation.id) },
        )
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    snapshot: SlackSnapshot,
    onClick: () -> Unit,
) {
    val title = conversation.title(snapshot.users)
    val preview = Mrkdwn.display(conversation.latestText, snapshot.users, snapshot.conversations)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BurtonCharcoal, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (conversation.kind == ConversationKind.IM) {
            UserAvatar(url = conversation.imageUrl, name = title, size = 40.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${conversation.prefix()}$title".trim(),
                style = MaterialTheme.typography.titleMedium,
                color = BurtonIvory,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (preview.isNotBlank() || conversation.topic.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = preview.ifBlank { conversation.topic },
                    style = MaterialTheme.typography.bodyMedium,
                    color = BurtonMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (conversation.unread > 0) {
            Text(
                text = conversation.unread.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = BurtonSand,
            )
        }
    }
}
