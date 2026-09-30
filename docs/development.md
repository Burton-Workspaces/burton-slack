# Development

## Tooling

- JDK **17**
- Android SDK compile/target **35**, min **26**
- Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01
- Hilt 2.53.1 (KSP)
- OkHttp 4.12.0, Coil 2.7.0, Android Browser (Custom Tabs)

Point Gradle at the SDK with `local.properties` (`sdk.dir=…`). That file is gitignored.

## Commands

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew testDebugUnitTest
```

Release assemble is blocked unless `keystore.properties` exists and `storeFile` points at a real keystore. Copy [`keystore.properties.example`](../keystore.properties.example) and keep `keystore.properties`, `*.jks`, and `*.keystore` out of git (see `.gitignore`). GitHub Actions signing is [build automation](build-automation.md).

Debug application id is `com.burton.slack.debug` so it can sit next to a signed install.

The emulator can run the UI. Connect with Slack against a real workspace is the real test. Paste-token still works for a classic `xoxp-`.

## Slack app (manifest + tokens)

The Android client is not a Slack app by itself. [`slack/manifest.json`](../slack/manifest.json) is the Slack app: user scopes, PKCE, redirect `burtonslack://oauth`. No bot scopes (PKCE + custom URI rejects them). Enabling PKCE is one-way.

```bash
# Install the Slack CLI: https://docs.slack.dev/tools/slack-cli
slack login
./scripts/slack-sync.sh
```

That validates and creates (or updates) the Slack app through the Manifest API, then writes the public Client ID to `slack/client-id.txt`. Slack CLI `manifest validate` is not used: it strips `pkce_enabled`, and Slack then rejects `burtonslack://oauth`.

The Slack CLI reads [`slack/.slack/hooks.json`](../slack/.slack/hooks.json). `get-manifest` must live under the `hooks` object and run with Python (`python3 .slack/get-manifest.py`). A top-level `"get-manifest": "cat …"` is ignored; the CLI then looks for Deno/Node and fails with `runtime_not_found`.

`slack login` stores app configuration tokens in `~/.slack/credentials.json` and rotates them (`tooling.tokens.rotate`; access tokens last 12 hours). Do **not** put those rotating tokens in GitHub Actions secrets — the refresh token is single-use.

Without the CLI, rotate a config refresh token and call the Manifest API:

```bash
eval "$(./scripts/slack-config-rotate.sh --write)"
./scripts/slack-sync.sh --app A0123456789
```

`--validate-only` schema-checks the manifest and stops.

CI uses a long-lived **service token** from `slack auth token` (`SLACK_SERVICE_TOKEN`) plus variable `SLACK_APP_ID`. See [build automation](build-automation.md).

## Layout

```
app/src/main/java/com/burton/slack/
  MainActivity.kt              sign-in gate, OAuth callback, tabs, nav
  data/slack/                  Slack Web API, PKCE, rotating tokens
  data/parse/                  TinyJson, SlackCodec
  data/repository/             SlackRepository, DataStore
  domain/                      models, mrkdwn
  ui/home, channel, thread, search, settings, signin, components, theme
app/src/test/java/…            TinyJson, SlackCodec, Mrkdwn, PKCE, OAuth parse
slack/                         Slack app manifest, public Client ID
```

Parser tests cover Slack JSON and mrkdwn. Run those before changing `SlackCodec`.

Launcher PNGs (mipmaps + F-Droid `fdroid/metadata/com.burton.slack/en-US/icon.png`) come from `brand/ic_launcher.svg`:

```bash
python3 scripts/render-icons.py
```

## Network while debugging

HTTPS only. File previews on `files.slack.com` send the stored token via Coil’s OkHttp interceptor.

## Versioning while developing

Do not hand-edit `CHANGELOG.md` or `version.txt` on feature branches. Those are owned by [release-please](releases.md) from Conventional Commits on `master`.

Commit subjects must follow Conventional Commits. Install the hook once:

```bash
./scripts/install-git-hooks.sh
```

See [CONTRIBUTING.md](../CONTRIBUTING.md).

After a tagged SemVer release, publish the APK into the shared Burton Workspaces catalog:

```bash
./scripts/publish-fdroid-pages.sh
```

That reads `version.txt`. Setup: [fdroid.md](fdroid.md).
