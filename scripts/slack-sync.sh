#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 [--validate-only] [--app APP_ID]" >&2
  echo >&2
  echo "Validate slack/manifest.json, then create or update the Slack app." >&2
  echo "Uses the Manifest API so oauth_config.pkce_enabled is kept (Slack CLI" >&2
  echo "validate drops it, which makes burtonslack://oauth fail)." >&2
  echo >&2
  echo "Token (first match): SLACK_CONFIG_TOKEN, .slack-config-tokens, then" >&2
  echo "~/.slack/credentials.json from \`slack login\`." >&2
  echo >&2
  echo "Options:" >&2
  echo "  --validate-only   schema-check only; do not create or update" >&2
  echo "  --app APP_ID      update this app (or SLACK_APP_ID / last created id)" >&2
  echo >&2
  echo "First time:" >&2
  echo "  slack login" >&2
  echo "  $0" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage ;;
    --validate-only|--app)
      break
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! command -v python3 >/dev/null; then
  echo "python3 is required." >&2
  exit 1
fi

if [[ -z "${SLACK_CONFIG_TOKEN:-}" && -n "${SLACK_CONFIG_REFRESH_TOKEN:-}" ]]; then
  eval "$("$ROOT/scripts/slack-config-rotate.sh" --write)"
fi

exec python3 "$ROOT/scripts/slack-manifest-sync.py" "$@"
