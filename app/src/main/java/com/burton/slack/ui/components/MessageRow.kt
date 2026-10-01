package com.burton.slack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.burton.slack.domain.Mrkdwn
import com.burton.slack.domain.SlackMessage
import com.burton.slack.domain.SlackSnapshot
import com.burton.slack.ui.theme.BurtonCharcoal
import com.burton.slack.ui.theme.BurtonIvory
import com.burton.slack.ui.theme.BurtonMute
import com.burton.slack.ui.theme.BurtonSand
import com.burton.slack.ui.theme.BurtonVoid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageRow(
    message: SlackMessage,
    snapshot: SlackSnapshot,
    compact: Boolean,
    onOpenThread: (() -> Unit)?,
    onReact: () -> Unit,
) {
    val user = snapshot.users[message.userId]
    val name = user?.label ?: message.username.ifBlank { snapshot.userLabel(message.userId) }
    val body = Mrkdwn.display(message.text, snapshot.users, snapshot.conversations)
    if (message.isSystem) {
        Text(
            text = body.ifBlank { systemLabel(message.subtype) },
            style = MaterialTheme.typography.bodyMedium,
            color = BurtonMute,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onReact)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (compact) {
            Spacer(Modifier.width(40.dp))
        } else {
            UserAvatar(url = user?.imageUrl.orEmpty(), name = name)
        }
        Column(modifier = Modifier.weight(1f)) {
            if (!compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        color = BurtonIvory,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(formatTs(message.ts), style = MaterialTheme.typography.labelSmall, color = BurtonMute)
                }
                Spacer(Modifier.height(4.dp))
            }
            if (body.isNotBlank()) {
                Text(body, style = MaterialTheme.typography.bodyLarge, color = BurtonIvory)
            }
            message.files.forEach { file ->
                Spacer(Modifier.height(8.dp))
                if (file.isImage && file.previewUrl.isNotBlank()) {
                    AsyncImage(
                        model = file.previewUrl,
                        contentDescription = file.title.ifBlank { file.name },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 280.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BurtonCharcoal),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        file.title.ifBlank { file.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = BurtonSand,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BurtonCharcoal, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
            if (message.reactions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val me = snapshot.workspace?.userId.orEmpty()
                    message.reactions.forEach { reaction ->
                        val mine = reaction.mine(me)
                        Text(
                            ":${reaction.name}: ${reaction.count}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mine) BurtonVoid else BurtonIvory,
                            modifier = Modifier
                                .background(
                                    if (mine) BurtonSand else BurtonCharcoal,
                                    RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            if (message.isThreadParent && onOpenThread != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${message.replyCount} ${if (message.replyCount == 1) "reply" else "replies"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = BurtonSand,
                    modifier = Modifier.clickable(onClick = onOpenThread),
                )
            }
        }
    }
}

private fun systemLabel(subtype: String) = when (subtype) {
    "channel_join" -> "Joined the channel"
    "channel_leave" -> "Left the channel"
    "channel_topic" -> "Changed the topic"
    "channel_purpose" -> "Changed the purpose"
    else -> subtype.replace('_', ' ')
}

private val clock = SimpleDateFormat("h:mm a", Locale.getDefault())

fun formatTs(ts: String): String {
    val seconds = ts.substringBefore('.').toLongOrNull() ?: return ""
    return clock.format(Date(seconds * 1000))
}

fun compactWith(previous: SlackMessage?, current: SlackMessage): Boolean {
    if (previous == null || current.isSystem || previous.isSystem) return false
    if (previous.userId.isBlank() || previous.userId != current.userId) return false
    val previousSec = previous.ts.substringBefore('.').toLongOrNull() ?: return false
    val currentSec = current.ts.substringBefore('.').toLongOrNull() ?: return false
    return currentSec - previousSec < 300
}
