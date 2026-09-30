# Burton Slack

A Slack client for Android with the same look as the other Burton apps. Sign in with **Connect with Slack**, then read channels and DMs, send messages, follow threads, and search.

Signed APKs are published on [GitHub Releases](https://github.com/Burton-Workspaces/burton-slack/releases). Droidify / F-Droid: [burton-sonos-fdroid](https://github.com/Burton-Workspaces/burton-sonos-fdroid) (`https://burton-workspaces.github.io/burton-sonos-fdroid/fdroid/repo`).

## What it does

- **Home** — starred, channels, and direct messages with unread counts
- **Channel** — history, send, reactions, jump into a thread
- **Threads** — replies on one message
- **Search** — workspace message search
- **Settings** — workspace, sign out, app version

The token stays on the phone (DataStore). The app talks to Slack’s Web API over HTTPS; there is no Burton cloud account.

## Requirements

- Android 8.0+ (API 26)
- Internet
- A Slack workspace you can authorize (see [Using the app](docs/using.md))

## Docs

| Doc | Contents |
| --- | --- |
| [Using the app](docs/using.md) | Connect with Slack, screens, scopes |
| [Architecture](docs/architecture.md) | Packages, Slack Web API, caching, polling |
| [Development](docs/development.md) | Build, run, test, project layout |
| [Build automation](docs/build-automation.md) | GitHub Actions, workflow permissions, signing secrets |
| [Releases](docs/releases.md) | SemVer 2.0, local build + publish walkthrough, GitHub Releases |
| [F-Droid / Droidify](docs/fdroid.md) | Same catalog as Burton Sonos, Fingerprint, one-command Pages publish |
| [Contributing](CONTRIBUTING.md) | Conventional Commits (required) |

## Quick start (debug)

```bash
./gradlew :app:installDebug
```

Debug builds use application id `com.burton.slack.debug`. Release builds need a keystore; see [docs/releases.md](docs/releases.md).

```bash
./gradlew testDebugUnitTest
```

## License and scope

This is a household Slack client. It does not replace the official Slack app for admin, huddles, or workspace management.
