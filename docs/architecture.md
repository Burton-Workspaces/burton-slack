# Architecture

The app is a single Gradle module (`:app`), Kotlin, Jetpack Compose, Hilt, OkHttp, Coil, and DataStore. UI collects `SlackRepository` state.

```
ui/          Compose screens and ViewModels (Hilt)
domain/      Conversation, SlackMessage, Workspace, Mrkdwn
data/
  slack      SlackApi (HTTPS Web API)
  parse      TinyJson + SlackCodec
  repository SlackRepository, LocalPrefs (DataStore)
di/          OkHttp, Coil ImageLoader
```

## Auth

A user OAuth token is stored in DataStore (`burton_slack`). `auth.test` plus `team.info` fill the workspace snapshot. Screens never see the token string after sign-in; the repository holds it in memory and DataStore.

## API

`SlackApi` POSTs `application/x-www-form-urlencoded` to `https://slack.com/api/{method}` with `Authorization: Bearer`. Responses are TinyJson maps. SlackCodec maps those onto domain models.

| Method | Use |
| --- | --- |
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
