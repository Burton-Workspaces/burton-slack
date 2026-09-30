#!/usr/bin/env python3
"""Rasterize the Burton Slack launcher mark for Android mipmaps and F-Droid."""
from __future__ import annotations

from pathlib import Path

import cairo
import gi

gi.require_version("Rsvg", "2.0")
from gi.repository import Rsvg  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
SVG = ROOT / "brand" / "ic_launcher.svg"
SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def render(svg: Path, dest: Path, size: int) -> None:
    handle = Rsvg.Handle.new_from_file(str(svg))
    surface = cairo.ImageSurface(cairo.FORMAT_ARGB32, size, size)
    ctx = cairo.Context(surface)
    viewport = Rsvg.Rectangle()
    viewport.x = 0
    viewport.y = 0
    viewport.width = size
    viewport.height = size
    handle.render_document(ctx, viewport)
    dest.parent.mkdir(parents=True, exist_ok=True)
    surface.write_to_png(str(dest))


def main() -> None:
    if not SVG.exists():
        raise SystemExit(f"missing {SVG}")
    res = ROOT / "app" / "src" / "main" / "res"
    for folder, size in SIZES.items():
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            render(SVG, res / folder / name, size)
    render(SVG, ROOT / "fdroid" / "metadata" / "com.burton.slack" / "en-US" / "icon.png", 512)
    print("wrote launcher mipmaps and F-Droid icon.png")


if __name__ == "__main__":
    main()
