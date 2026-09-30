package com.burton.slack.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burton.slack.domain.Mrkdwn
import com.burton.slack.domain.SearchHit
import com.burton.slack.ui.theme.BurtonCharcoal
import com.burton.slack.ui.theme.BurtonIvory
import com.burton.slack.ui.theme.BurtonLine
import com.burton.slack.ui.theme.BurtonMute
import com.burton.slack.ui.theme.BurtonSand

@Composable
fun SearchScreen(
    onOpenHit: (channelId: String, threadTs: String?) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineLarge, color = BurtonIvory)
        Text(
            "Messages in this workspace",
            style = MaterialTheme.typography.bodyMedium,
            color = BurtonMute,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = ui.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search messages") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (ui.query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clear) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = BurtonIvory,
                unfocusedTextColor = BurtonIvory,
                focusedBorderColor = BurtonSand,
                unfocusedBorderColor = BurtonLine,
                cursorColor = BurtonIvory,
                focusedPlaceholderColor = BurtonMute,
                unfocusedPlaceholderColor = BurtonMute,
                focusedLeadingIconColor = BurtonSand,
                unfocusedLeadingIconColor = BurtonMute,
                focusedTrailingIconColor = BurtonIvory,
                unfocusedTrailingIconColor = BurtonMute,
            ),
        )
        Spacer(Modifier.height(16.dp))
        when {
            ui.loading -> Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BurtonSand)
            }
            ui.error != null -> Text(ui.error ?: "", color = BurtonIvory)
            ui.query.isBlank() -> Text(
                "Type a word, a person’s name, or a phrase.",
                color = BurtonMute,
            )
            ui.searched && ui.hits.isEmpty() -> Text(
                "No matches in this workspace.",
                color = BurtonMute,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ui.hits, key = { "${it.channelId}-${it.message.ts}" }) { hit ->
                    SearchHitRow(
                        hit = hit,
                        preview = Mrkdwn.display(hit.message.text, snapshot.users, snapshot.conversations),
                        onClick = {
                            val thread = hit.message.threadTs.takeIf { ts -> ts.isNotBlank() && ts != hit.message.ts }
                            onOpenHit(hit.channelId, thread)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHitRow(
    hit: SearchHit,
    preview: String,
    onClick: () -> Unit,
) {
    val channel = hit.channelName.ifBlank { hit.channelId }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BurtonCharcoal, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            "#$channel",
            style = MaterialTheme.typography.labelSmall,
            color = BurtonSand,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            preview.ifBlank { "Attachment" },
            style = MaterialTheme.typography.bodyLarge,
            color = BurtonIvory,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
