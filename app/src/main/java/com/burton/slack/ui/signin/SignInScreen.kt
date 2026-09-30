package com.burton.slack.ui.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burton.slack.ui.theme.BurtonBlack
import com.burton.slack.ui.theme.BurtonCharcoal
import com.burton.slack.ui.theme.BurtonIvory
import com.burton.slack.ui.theme.BurtonMute
import com.burton.slack.ui.theme.BurtonSand
import com.burton.slack.ui.theme.BurtonVoid

@Composable
fun SignInScreen(
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BurtonBlack)
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Burton Slack", style = MaterialTheme.typography.headlineLarge, color = BurtonIvory)
        Spacer(Modifier.height(12.dp))
        Text(
            "Paste a workspace user token. The token stays on this phone; the app talks to Slack over HTTPS.",
            style = MaterialTheme.typography.bodyLarge,
            color = BurtonMute,
        )
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BurtonCharcoal, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text("User token", style = MaterialTheme.typography.labelSmall, color = BurtonMute)
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = ui.token,
                onValueChange = viewModel::onTokenChange,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = MaterialTheme.typography.titleLarge.copy(color = BurtonIvory),
                cursorBrush = SolidColor(BurtonIvory),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (ui.token.isBlank()) {
                        Text("xoxp-…", color = BurtonMute, style = MaterialTheme.typography.titleLarge)
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Create an app at api.slack.com/apps, add the user scopes in the using guide, install it, and copy the User OAuth Token.",
            style = MaterialTheme.typography.bodyMedium,
            color = BurtonMute,
        )
        if (ui.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(ui.error ?: "", color = BurtonIvory, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = viewModel::connect,
            enabled = !ui.busy,
            colors = ButtonDefaults.buttonColors(containerColor = BurtonIvory, contentColor = BurtonVoid),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (ui.busy) {
                CircularProgressIndicator(color = BurtonSand, modifier = Modifier.height(18.dp))
            } else {
                Text("Connect")
            }
        }
    }
}
