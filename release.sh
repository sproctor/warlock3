#!/usr/bin/bash

# Release pipeline: tag the current commit and push the tag.
# The GitHub Actions release workflow (.github/workflows/release.yaml) builds
# and publishes the multi-platform desktop packages on tag push.
#
# Usage: ./release.sh <version>
# Examples:
#   ./release.sh 3.0.165
#   ./release.sh 3.0.165-beta.1

set -e

if [[ -z "$1" ]]; then
  echo "Usage: $0 <version>" >&2
  echo "Example: $0 3.0.165" >&2
  echo "         $0 3.0.165-beta.1" >&2
  exit 1
fi

VERSION="$1"

echo "Releasing v${VERSION}..."

read -p "Press enter to tag v${VERSION} and push"

git tag "v${VERSION}"
git push origin "v${VERSION}"

echo "Tag pushed. CI will build and publish the release at:"
echo "  https://github.com/sproctor/warlock3/actions"
