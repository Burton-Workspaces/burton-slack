# Using Burton Slack

Burton Slack is a workspace client. You sign in with a Slack **user token** from an app you create and install. The token is stored on the phone. Messages go through Slack’s HTTPS Web API.

## Token

1. Open [api.slack.com/apps](https://api.slack.com/apps) and create an app **From scratch** in the workspace you want to read.
2. **OAuth & Permissions** → User Token Scopes. Add:

   | Scope | Why |
   | --- | --- |
   | `channels:history`, `channels:read` | Public channels |
   | `groups:history`, `groups:read` | Private channels |
   | `im:history`, `im:read`, `mpim:history`, `mpim:read` | Direct messages |
   | `chat:write` | Send messages |
   | `users:read` | Names and avatars |
   | `search:read` | Search tab |
   | `team:read` | Workspace name |
   | `reactions:read`, `reactions:write` | Emoji reactions |
   | `stars:read` | Starred section on Home |

3. **Install to Workspace** and copy the **User OAuth Token** (`xoxp-…`).
4. In the app, paste that token and tap **Connect**.

Sign out from Settings. That deletes the token from the phone. A bot token (`xoxb-`) can list some channels the bot is in, but this client is built for a user token.

## Screens

### Connect

Shown when no token is stored. Paste the token. Failed `auth.test` stays on this screen with an error and retry.

### Home

Lists starred conversations, then channels, then direct messages. Unread counts show in sand. Tap a row to open it.

Settings (gear) holds the workspace, sign out, and the app version.

### Channel

Newest messages at the bottom. Tap a message to react. Tap the reply count to open the thread. The compose field at the bottom sends with **Send**.

### Thread

Replies under one parent message. Sending here sets `thread_ts`.

### Search

Workspace `search.messages`. Tap a hit to open that channel (and thread when the hit is a reply).

## Permissions

| Android | Permission | Why |
| --- | --- | --- |
| All | Internet | Slack Web API |

No location or nearby-devices permission. Debug builds use application id `com.burton.slack.debug` and can sit next to a signed install.
