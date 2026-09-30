# Development

## Tooling

- JDK **17**
- Android SDK compile/target **35**, min **26**
- Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01
- Hilt 2.53.1 (KSP)
- OkHttp 4.12.0, Coil 2.7.0

Point Gradle at the SDK with `local.properties` (`sdk.dir=…`). That file is gitignored.

## Commands

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
./gradlew testDebugUnitTest
```

Release assemble is blocked unless `keystore.properties` exists and `storeFile` points at a real keystore. Copy [`keystore.properties.example`](../keystore.properties.example) and keep `keystore.properties`, `*.jks`, and `*.keystore` out of git (see `.gitignore`). GitHub Actions signing is [build automation](build-automation.md).

Debug application id is `com.burton.slack.debug` so it can sit next to a signed install.

The emulator can run the UI. A real token against a test workspace is the real test.

## Layout

```
app/src/main/java/com/burton/slack/
  MainActivity.kt              sign-in gate, tabs, nav
  data/slack/                  Slack Web API
  data/parse/                  TinyJson, SlackCodec
  data/repository/             SlackRepository, DataStore
  domain/                      models, mrkdwn
  ui/home, channel, thread, search, settings, signin, components, theme
app/src/test/java/…            TinyJson, SlackCodec, Mrkdwn
```

Parser tests cover Slack JSON and mrkdwn. Run those before changing `SlackCodec`.

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
