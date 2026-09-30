#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 [--write [file]]" >&2
  echo >&2
  echo "Exchange a Slack app configuration refresh token (xoxe-) for a new" >&2
  echo "access token (xoxe.xoxp-, 12h) via tooling.tokens.rotate." >&2
  echo >&2
  echo "Do not store these tokens in GitHub Actions secrets. Refresh tokens are" >&2
  echo "single-use; concurrent jobs will lock you out. Use slack auth token and" >&2
  echo "SLACK_SERVICE_TOKEN for CI instead." >&2
  echo >&2
  echo "Required:" >&2
  echo "  SLACK_CONFIG_REFRESH_TOKEN   xoxe- refresh token" >&2
  echo >&2
  echo "Optional:" >&2
  echo "  --write [file]   write KEY=value lines (default: .slack-config-tokens)" >&2
  echo >&2
  echo "Prints eval-able export lines on stdout. Example:" >&2
  echo "  eval \"\$($0 --write)\"" >&2
  exit 1
}

WRITE_FILE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage ;;
    --write)
      if [[ $# -ge 2 && "$2" != -* ]]; then
        WRITE_FILE="$2"
        shift 2
      else
        WRITE_FILE=".slack-config-tokens"
        shift
      fi
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -z "${SLACK_CONFIG_REFRESH_TOKEN:-}" && -f "$ROOT/.slack-config-tokens" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.slack-config-tokens"
  set +a
fi

if [[ -z "${SLACK_CONFIG_REFRESH_TOKEN:-}" ]]; then
  echo "Set SLACK_CONFIG_REFRESH_TOKEN (xoxe-) or put it in .slack-config-tokens." >&2
  exit 1
fi

if ! command -v python3 >/dev/null; then
  echo "python3 is required to parse Slack's JSON response." >&2
  exit 1
fi

if [[ -n "$WRITE_FILE" && "$WRITE_FILE" != /* ]]; then
  WRITE_FILE="$ROOT/$WRITE_FILE"
fi

export SLACK_CONFIG_REFRESH_TOKEN
export WRITE_FILE
python3 <<'PY'
import json, os, shlex, sys, urllib.parse, urllib.request

refresh = os.environ["SLACK_CONFIG_REFRESH_TOKEN"]
write_file = os.environ.get("WRITE_FILE", "")
body = urllib.parse.urlencode({"refresh_token": refresh}).encode()
req = urllib.request.Request(
    "https://slack.com/api/tooling.tokens.rotate",
    data=body,
    method="POST",
    headers={"Content-Type": "application/x-www-form-urlencoded"},
)
with urllib.request.urlopen(req, timeout=30) as resp:
    parsed = json.loads(resp.read().decode())
if not parsed.get("ok"):
    err = parsed.get("error", "unknown_error")
    sys.stderr.write(f"tooling.tokens.rotate failed: {err}\n")
    if err == "invalid_refresh_token":
        sys.stderr.write(
            "Get a new pair from https://api.slack.com/apps → Your App Configuration Tokens.\n"
        )
    sys.exit(1)
token = parsed.get("token") or ""
new_refresh = parsed.get("refresh_token") or ""
if not token or not new_refresh:
    sys.stderr.write("tooling.tokens.rotate returned incomplete data\n")
    sys.exit(1)
print(f"export SLACK_CONFIG_TOKEN={shlex.quote(token)}")
print(f"export SLACK_CONFIG_REFRESH_TOKEN={shlex.quote(new_refresh)}")
exp = parsed.get("exp", "")
if exp:
    sys.stderr.write(f"Rotated app configuration token (exp {exp}).\n")
else:
    sys.stderr.write("Rotated app configuration token.\n")
if write_file:
    with open(write_file, "w", encoding="utf-8") as fh:
        fh.write(f"SLACK_CONFIG_TOKEN={token}\n")
        fh.write(f"SLACK_CONFIG_REFRESH_TOKEN={new_refresh}\n")
    os.chmod(write_file, 0o600)
    sys.stderr.write(f"Wrote {write_file}\n")
PY
