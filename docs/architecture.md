# Architecture

The app is a single Gradle module (`:app`), Kotlin, Jetpack Compose, Hilt, OkHttp, Coil, and DataStore. UI collects `SlackRepository` state.

```
ui/          Compose screens and ViewModels (Hilt)
domain/      Conversation, SlackMessage, Workspace, Mrkdwn
data/
  slack      SlackApi, PKCE, rotating user tokens
  parse      TinyJson + SlackCodec
  repository SlackRepository, LocalPrefs (DataStore)
di/          OkHttp, Coil ImageLoader
```

## Auth

Connect with Slack runs OAuth 2.0 with PKCE (`burtonslack://oauth`). There is no client secret in the APK. `oauth.v2.access` returns a user access token plus a refresh token; Custom URI installs always rotate. Tokens live in DataStore (`burton_slack`): `user_token`, `refresh_token`, `token_expires_at`. The repository refreshes about five minutes before expiry (`oauth.v2.access` with `grant_type=refresh_token` and `client_id` only). A failed refresh signs out. Paste-token sign-in still stores a classic `xoxp-` with no refresh.

`auth.test` plus `team.info` fill the workspace snapshot. Screens never see the token string after sign-in; the repository holds it in memory and DataStore.

## API

`SlackApi` POSTs `application/x-www-form-urlencoded` to `https://slack.com/api/{method}` with `Authorization: Bearer`. Responses are TinyJson maps. SlackCodec maps those onto domain models.

| Method | Use |
| --- | --- |
| `oauth.v2.access` | PKCE code exchange and token refresh (no Bearer, no client secret) |
| `auth.test` / `team.info` | Workspace + signed-in user |
| `users.list` | Name and avatar cache |
| `conversations.list` | Home rows (channels, IMs, MPIMs) |
| `conversations.history` / `conversations.replies` | Channel and thread |
| `chat.postMessage` | Send |
| `reactions.add` / `reactions.remove` | Emoji on a message |
| `search.messages` | Search tab |
| `conversations.mark` | Mark a channel read when opened |

Pagination follows `response_metadata.next_cursor` with a page cap.

## Polling

`SlackRepository.start()` hydrates token → workspace → users → conversations, then polls conversations about every 4 seconds. An open channel polls history about every 3 seconds. There is no Socket Mode or RTM in this build.

## UI shell

`MainActivity` hosts a `NavHost`. **Home** and **Search** are bottom tabs. Channel and thread are stacked routes and hide the tab bar. Settings is a full-screen modal. Sign-in is a gate when no token is stored.

Theme tokens match Burton Sonos: black surfaces, ivory text, sand accent, danger `#C45C4A`.
