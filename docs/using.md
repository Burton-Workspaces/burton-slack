# Using Burton Slack

Burton Slack is a workspace client. Sign in with **Connect with Slack**. The token is stored on the phone. Messages go through Slack’s HTTPS Web API.

## Connect

1. Tap **Connect with Slack** and allow the Burton Slack app in the browser.
2. Slack returns you to the app. The user token stays on the phone (DataStore). Rotating tokens refresh automatically; refresh tokens last 30 days.

The Slack app is defined in [`slack/manifest.json`](../slack/manifest.json) (user scopes only, PKCE, redirect `burtonslack://oauth`). Someone has to create it once with `slack login` and `./scripts/slack-sync.sh`, then put the public Client ID in `slack/client-id.txt`. After that, every phone uses the same app. PKCE is one-way: it marks the Slack app a public client.

Sign out from Settings. That deletes the token from the phone.

### Use a token (optional)

On Connect, **Use a token** pastes a classic User OAuth Token (`xoxp-…`) from an app you installed yourself. That path does not refresh. Prefer Connect with Slack when the Client ID is set. A bot token (`xoxb-`) can list some channels the bot is in, but this client is built for a user token.

User scopes the Slack app requests:

| Scope | Why |
| --- | --- |
| `channels:history`, `channels:read` | Public channels |
| `groups:history`, `groups:read` | Private channels |
| `im:history`, `im:read`, `im:write`, `mpim:history`, `mpim:read` | Direct messages |
| `chat:write` | Send messages |
| `users:read` | Names and avatars |
| `search:read` | Search tab |
| `team:read` | Workspace name |
| `reactions:read`, `reactions:write` | Emoji reactions |
| `stars:read` | Starred section on Home |
| `files:read` | Slack-hosted photos and file previews |

## Screens

### Connect

Shown when no token is stored. **Connect with Slack** opens Slack’s authorize page. Failed login stays on this screen with an error and retry. **Use a token** is a debug fallback.

### Home

Lists starred conversations, then channels, then direct messages. Unread counts show in sand. Tap a row to open it.

Settings (gear) holds the workspace, sign out, and the app version.

### Channel

Newest messages at the bottom. Tap a message to react. Tap the reply count to open the thread. The compose field at the bottom sends with **Send**.

### Thread

Replies under one parent message. Sending here sets `thread_ts`.

### Search

People from the workspace directory (username, display name, and real name), then workspace `search.messages`. Tap a person to open a DM. Tap a message to open that channel (and thread when the hit is a reply).

## Permissions

| Android | Permission | Why |
| --- | --- |
| All | Internet | Slack Web API |

No location or nearby-devices permission. Debug builds use application id `com.burton.slack.debug` and can sit next to a signed install.
