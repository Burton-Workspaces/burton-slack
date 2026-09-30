#!/usr/bin/env python3
"""Validate / create / update the Burton Slack app from slack/manifest.json.

Slack CLI `manifest validate` drops oauth_config.pkce_enabled, so custom URI
redirects fail with invalid_redirect_urls. The Manifest API keeps PKCE.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = ROOT / "slack" / "manifest.json"
CLIENT_ID_PATH = ROOT / "slack" / "client-id.txt"
APPS_JSON = ROOT / "slack" / ".slack" / "apps.json"
TOKEN_FILE = ROOT / ".slack-config-tokens"
CREDENTIALS = Path.home() / ".slack" / "credentials.json"


def load_manifest() -> dict:
    return json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))


def find_token(obj: object) -> str:
    if isinstance(obj, dict):
        token = obj.get("token")
        if isinstance(token, str) and token.startswith(("xoxe.xoxp-", "xoxp-")):
            return token
        for value in obj.values():
            found = find_token(value)
            if found:
                return found
    elif isinstance(obj, list):
        for item in obj:
            found = find_token(item)
            if found:
                return found
    return ""


def config_token() -> str:
    env = os.environ.get("SLACK_CONFIG_TOKEN", "").strip()
    if env:
        return env
    if TOKEN_FILE.is_file():
        for line in TOKEN_FILE.read_text(encoding="utf-8").splitlines():
            if line.startswith("SLACK_CONFIG_TOKEN="):
                value = line.split("=", 1)[1].strip().strip("'\"")
                if value:
                    return value
    if CREDENTIALS.is_file():
        found = find_token(json.loads(CREDENTIALS.read_text(encoding="utf-8")))
        if found:
            return found
    return ""


def slack_method(token: str, method: str, fields: dict) -> dict:
    body = urllib.parse.urlencode(fields).encode()
    req = urllib.request.Request(
        f"https://slack.com/api/{method}",
        data=body,
        method="POST",
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            parsed = json.loads(resp.read().decode())
    except urllib.error.HTTPError as error:
        parsed = json.loads(error.read().decode())
    if parsed.get("ok"):
        return parsed
    err = parsed.get("error", "unknown_error")
    sys.stderr.write(f"{method} failed: {err}\n")
    for item in parsed.get("errors") or []:
        message = item.get("message") or item.get("code") or item
        pointer = item.get("pointer", "")
        extra = f" ({pointer})" if pointer else ""
        sys.stderr.write(f"  {message}{extra}\n")
    sys.exit(1)


def write_client_id(client_id: str) -> None:
    if not client_id:
        return
    existing = ""
    if CLIENT_ID_PATH.is_file():
        existing = next(
            (
                line.strip()
                for line in CLIENT_ID_PATH.read_text(encoding="utf-8").splitlines()
                if line.strip() and not line.strip().startswith("#")
            ),
            "",
        )
    if existing == client_id:
        return
    CLIENT_ID_PATH.write_text(
        "# Public Slack app Client ID (not a secret). PKCE public clients expose this in the APK.\n"
        f"{client_id}\n",
        encoding="utf-8",
    )
    sys.stderr.write(f"Wrote {CLIENT_ID_PATH.relative_to(ROOT)}\n")


def write_app_id(app_id: str) -> None:
    APPS_JSON.parent.mkdir(parents=True, exist_ok=True)
    payload = {"apps": {"dev": {"app_id": app_id}}}
    if APPS_JSON.is_file():
        try:
            payload = json.loads(APPS_JSON.read_text(encoding="utf-8"))
            payload.setdefault("apps", {}).setdefault("dev", {})["app_id"] = app_id
        except json.JSONDecodeError:
            pass
    APPS_JSON.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def stored_app_id() -> str:
    if not APPS_JSON.is_file():
        return ""
    try:
        data = json.loads(APPS_JSON.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return ""
    apps = data.get("apps") if isinstance(data, dict) else None
    if isinstance(apps, dict):
        dev = apps.get("dev")
        if isinstance(dev, dict) and isinstance(dev.get("app_id"), str):
            return dev["app_id"]
    return ""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--validate-only", action="store_true")
    parser.add_argument("--app", default=os.environ.get("SLACK_APP_ID", ""))
    args = parser.parse_args()
    if not MANIFEST_PATH.is_file():
        sys.stderr.write(f"Missing {MANIFEST_PATH}\n")
        return 1
    token = config_token()
    if not token:
        sys.stderr.write(
            "No app configuration token. Run `slack login`, or set SLACK_CONFIG_TOKEN,\n"
            "or eval \"$(./scripts/slack-config-rotate.sh --write)\".\n"
        )
        return 1
    manifest = load_manifest()
    manifest_json = json.dumps(manifest, separators=(",", ":"))
    slack_method(token, "apps.manifest.validate", {"manifest": manifest_json})
    sys.stderr.write("Manifest is valid.\n")
    if args.validate_only:
        return 0
    app_id = args.app.strip() or stored_app_id()
    sys.stderr.write("PKCE is one-way: enabling it marks the app a public client.\n")
    if app_id:
        result = slack_method(
            token,
            "apps.manifest.update",
            {"app_id": app_id, "manifest": manifest_json},
        )
        sys.stderr.write(f"Updated Slack app {app_id}.\n")
    else:
        result = slack_method(token, "apps.manifest.create", {"manifest": manifest_json})
        app_id = result.get("app_id", "")
        sys.stderr.write(f"Created Slack app {app_id}.\n")
    write_app_id(app_id)
    credentials = result.get("credentials") if isinstance(result.get("credentials"), dict) else {}
    client_id = credentials.get("client_id") or ""
    write_client_id(client_id)
    if not client_id:
        sys.stderr.write(
            "Copy the public Client ID from api.slack.com into slack/client-id.txt.\n"
        )
    sys.stderr.write(
        "CI: slack auth token → GitHub secret SLACK_SERVICE_TOKEN, variable SLACK_APP_ID.\n"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
