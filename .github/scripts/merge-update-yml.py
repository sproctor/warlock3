#!/usr/bin/env python3
"""Merge electron-builder per-arch update manifests into one per platform.

Usage: merge-update-yml.py <artifacts-dir>

Potassium/electron-builder writes a `latest-<os>.yml` (or `latest.yml` on Windows,
`beta-*`/`alpha-*` on other channels) next to each runner's installers. In a
multi-arch matrix (e.g. macOS arm64 + amd64, Linux amd64 + arm64) those share a
filename but each lists only its own architecture, so the auto-updater can only
serve one arch unless they are merged.

This reads <artifacts-dir>/release-assets-*/<name>.yml, unions the `files:` entries
across every architecture for each manifest name (keeping electron-builder's own
sha512/size/blockMapSize and the version/path/releaseDate from the first), and
writes the merged manifests to <artifacts-dir>/update-yml/ for publishing.
"""
import glob
import os
import sys

import yaml


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("Usage: merge-update-yml.py <artifacts-dir>")
    artifacts_dir = sys.argv[1]
    out_dir = os.path.join(artifacts_dir, "update-yml")
    os.makedirs(out_dir, exist_ok=True)

    # Group every per-arch manifest by filename, in a stable order.
    manifests: dict[str, list[dict]] = {}
    for path in sorted(glob.glob(os.path.join(artifacts_dir, "release-assets-*", "*.yml"))):
        name = os.path.basename(path)
        if name == "electron-builder.yml":  # build config, not an update manifest
            continue
        with open(path) as handle:
            doc = yaml.safe_load(handle)
        if not isinstance(doc, dict) or "files" not in doc:
            continue
        manifests.setdefault(name, []).append(doc)

    if not manifests:
        print("No update manifests found to merge.")
        return

    for name, docs in sorted(manifests.items()):
        merged = dict(docs[0])  # version / path / sha512 / releaseDate from the first
        files = []
        seen_urls = set()
        for doc in docs:
            for entry in doc.get("files", []):
                url = entry.get("url")
                if url in seen_urls:
                    continue
                seen_urls.add(url)
                files.append(entry)
        merged["files"] = files

        out_path = os.path.join(out_dir, name)
        with open(out_path, "w") as handle:
            yaml.safe_dump(merged, handle, default_flow_style=False, sort_keys=False)
        print(f"Merged {len(files)} file entries from {len(docs)} manifest(s) into {out_path}")


if __name__ == "__main__":
    main()
