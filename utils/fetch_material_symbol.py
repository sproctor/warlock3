#!/usr/bin/env python3
"""Download Material Symbols icons as Android vector drawables for Warlock.

Use this whenever you need to add an icon from Google's Material Symbols set
(https://fonts.google.com/icons). It fetches the *already-baked* Android
VectorDrawable XML that Google publishes in the material-design-icons repo, so
the artwork is already on the 960 grid and matches the format of the existing
drawables. Do not hand-transform the web SVGs (their viewBox is y-up); always
take the Android drawable from here so the path is byte-for-byte Google's.

The only edit applied is the repo convention: rewrite the fill color from
`@android:color/white` to the literal `#FFFFFF` that the other icons use.

Naming convention (matches the existing drawables):
  - outlined / single-version icons: no suffix         -> circle.xml
  - filled variant (FILL 1):         add "_filled"      -> circle_filled.xml

Examples:
  utils/fetch_material_symbol.py circle               # outlined ring -> circle.xml
  utils/fetch_material_symbol.py circle --filled      # solid disc    -> circle_filled.xml
  utils/fetch_material_symbol.py more_horiz logout    # several at once

Icons are written to compose/src/commonMain/composeResources/drawable/ and
become available as Res.drawable.<name> after the next Gradle build.

Defaults match fonts.google.com defaults: outlined style, weight 400, grade 0,
optical size 24. No third-party dependencies (standard library only).
"""
import argparse
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DRAWABLE_DIR = REPO_ROOT / "compose/src/commonMain/composeResources/drawable"
BASE = (
    "https://raw.githubusercontent.com/google/material-design-icons"
    "/master/symbols/android"
)


def fetch(name: str, style: str, filled: bool) -> "tuple[str, str]":
    """Return (url, xml) for the requested Android vector drawable."""
    family = f"materialsymbols{style}"
    fill = "fill1_" if filled else ""
    url = f"{BASE}/{name}/{family}/{name}_{fill}24px.xml"
    with urllib.request.urlopen(url, timeout=30) as response:
        return url, response.read().decode("utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Download Material Symbols icons as Android vector drawables.",
    )
    parser.add_argument("names", nargs="+", help="icon name(s), e.g. circle more_horiz")
    parser.add_argument(
        "--filled",
        action="store_true",
        help="download the filled (FILL 1) variant and add a _filled suffix",
    )
    parser.add_argument(
        "--style",
        default="outlined",
        choices=["outlined", "rounded", "sharp"],
        help="symbol style family (default: outlined)",
    )
    parser.add_argument(
        "--dir",
        default=str(DRAWABLE_DIR),
        help="output drawable directory (default: the compose commonMain drawables)",
    )
    args = parser.parse_args()

    out_dir = Path(args.dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    failures = 0
    for name in args.names:
        try:
            url, xml = fetch(name, args.style, args.filled)
        except urllib.error.HTTPError as error:
            print(
                f"FAILED {name}: {error} (does the icon exist in style "
                f"'{args.style}'{' filled' if args.filled else ''}?)",
                file=sys.stderr,
            )
            failures += 1
            continue
        except Exception as error:  # noqa: BLE001 - report any fetch problem
            print(f"FAILED {name}: {error}", file=sys.stderr)
            failures += 1
            continue

        # Repo convention: use the literal hex white the other drawables use.
        xml = xml.replace("@android:color/white", "#FFFFFF")
        suffix = "_filled" if args.filled else ""
        out_path = out_dir / f"{name}{suffix}.xml"
        out_path.write_text(xml)
        print(f"wrote {out_path.relative_to(REPO_ROOT)}  (from {url})")

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
