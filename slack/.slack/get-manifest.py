#!/usr/bin/env python3
"""Slack CLI get-manifest hook: print the static app manifest as JSON.

Slack CLI v4+ only runs scripts listed under hooks.* and otherwise tries to
detect Deno/Node. This project is a static manifest, so we ignore --source
and extra CLI flags.
"""
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parent
CANDIDATES = [
    HERE.parent / "manifest.json",
    Path.cwd() / "manifest.json",
    HERE / "manifest.json",
]


def main() -> int:
    for path in CANDIDATES:
        if path.is_file():
            sys.stdout.write(path.read_text(encoding="utf-8"))
            return 0
    sys.stderr.write("get-manifest: could not find manifest.json\n")
    return 1


if __name__ == "__main__":
    sys.exit(main())
