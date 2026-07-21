#!/usr/bin/bash

# Tag the current commit so the GitHub Actions release workflow
# (.github/workflows/release.yaml) picks it up and publishes.
#
# Version is auto-derived from the latest git tag. Beta is the default; use
# -p to cut a production release.
#
#   - default   -> next beta of the in-progress version, or beta.1 on the
#                  next patch if no beta line is open
#                  (v3.0.166 -> v3.0.167-beta.1, or .N+1 if betas exist)
#   - -m        -> bump minor and start beta.1 on the new minor
#                  (with 3.1.0 betas open -> v3.2.0-beta.1)
#   - -p        -> production tag. Finalizes the in-progress beta version if
#                  one exists, otherwise bumps patch.
#                  (with 3.1.0 betas open -> v3.1.0)
#   - -m -p     -> bump minor, reset patch, no beta suffix
#   - -v        -> print the next tag and exit (no tag, no push)
#
# The tag is what sets the version: release.yaml passes it through as
# RELEASE_VERSION, and desktopApp/build.gradle.kts routes a tag containing
# "beta"/"alpha" to the matching update channel. There is no version string
# committed anywhere, so the tag is the single source of truth.
#
# Examples:
#   release.sh        # v3.1.0-beta.22 -> v3.1.0-beta.23
#   release.sh -p     # v3.1.0-beta.22 -> v3.1.0
#   release.sh -m     # v3.1.0-beta.22 -> v3.2.0-beta.1
#   release.sh -v     # print next tag and exit

set -o errexit -o pipefail -o noclobber

PROD=0
MINOR=0
PRINT_ONLY=0
DEBUG=0

RED='\033[0;31m'
NC='\033[0m'

usage() {
  echo "Usage: release.sh [-dhmpv]"
  echo "  -d   enable debug mode"
  echo "  -h   show this help"
  echo "  -m   bump minor (reset patch to 0)"
  echo "  -p   production release (no beta suffix)"
  echo "  -v   print next tag and exit (no tag, no push)"
}

while getopts "dhmpv" arg; do
  case $arg in
    d) DEBUG=1 ;;
    m) MINOR=1 ;;
    p) PROD=1 ;;
    v) PRINT_ONLY=1 ;;
    h) usage; exit 0 ;;
    *) usage; exit 1 ;;
  esac
done

if [[ "$DEBUG" == "1" ]]; then
  set -xv
fi

TAG_GLOB='v[0-9]*.[0-9]*.[0-9]*'

# Bare version strings (3.1.0 or 3.1.0-beta.22) from the release tags.
# The '[-]' bracket form matters: ugrep, which is what `grep` resolves to on
# some setups here, rejects a bare '-' pattern as a missing argument.
all_versions() { git tag --list "$TAG_GLOB" | sed -E 's/^v//'; }

# Highest stable version (no pre-release suffix), as a bare X.Y.Z.
STABLE_VERSION=$(all_versions | grep -vE '[-]' | sort -V | tail -1 || true)

if [[ -z "$STABLE_VERSION" ]]; then
  echo -e "${RED}No prior stable tag found (looked for v*.*.* without a suffix).${NC}"
  echo "Create an initial tag manually, then re-run."
  exit 1
fi

IFS='.' read -r MAJOR MIN PATCH <<<"$STABLE_VERSION"

# Highest X.Y.Z across every tag, ignoring any -beta suffix. This is ahead of
# STABLE_VERSION whenever a beta line is open (as 3.1.0 has been since 3.0.166).
LATEST_ANY_BASE=$(all_versions | sed -E 's/-.*//' | sort -V | tail -1 || true)
HIGHER=$(printf '%s\n%s\n' "$LATEST_ANY_BASE" "$STABLE_VERSION" | sort -V | tail -1)
BETA_LINE_OPEN=0
if [[ "$LATEST_ANY_BASE" != "$STABLE_VERSION" && "$HIGHER" == "$LATEST_ANY_BASE" ]]; then
  BETA_LINE_OPEN=1
fi

# Decide the target VERSION (the MAJOR.MINOR.PATCH portion of the next tag).
if [[ "$MINOR" == "1" ]]; then
  # Bump minor from whichever line is furthest along. Basing this on the latest
  # stable instead would hand back the already-open beta version: with stable
  # 3.0.166 and 3.1.0 betas in flight, "start a new minor" means 3.2.0, not 3.1.0.
  IFS='.' read -r BUMP_MAJOR BUMP_MIN _ <<<"$HIGHER"
  VERSION="${BUMP_MAJOR}.$((BUMP_MIN + 1)).0"
elif [[ "$BETA_LINE_OPEN" == "1" ]]; then
  # Default (beta) and -p both target the in-progress version: default bumps the
  # beta number, -p finalizes that version as stable.
  VERSION="$LATEST_ANY_BASE"
else
  VERSION="${MAJOR}.${MIN}.$((PATCH + 1))"
fi

if [[ "$PROD" == "1" ]]; then
  TAG="v${VERSION}"
else
  NEXT=1
  while git rev-parse "v${VERSION}-beta.${NEXT}" >/dev/null 2>&1; do
    NEXT=$((NEXT + 1))
  done
  TAG="v${VERSION}-beta.${NEXT}"
fi

if [[ "$PRINT_ONLY" == "1" ]]; then
  echo "$TAG"
  exit 0
fi

echo "Latest stable: v${STABLE_VERSION}"
if [[ "$BETA_LINE_OPEN" == "1" ]]; then
  echo "Beta line open: v${LATEST_ANY_BASE}"
fi
echo "Next tag:      ${TAG}"

if [[ -n $(git status --porcelain) ]]; then
  echo -e "${RED}There are uncommitted changes${NC}"
  exit 1
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo -e "${RED}Tag $TAG already exists${NC}"
  exit 1
fi

echo "Tagging $TAG and pushing"
read -p "Press enter to continue, or ctrl-c to abort"

git tag "$TAG"
git push origin "$TAG"

if [[ "$PROD" == "1" ]]; then
  channel="production"
else
  channel="beta"
fi

echo "Pushed $TAG."
echo "  Desktop: build and publish the ${channel} Linux (amd64/arm64), Windows,"
echo "           and macOS (arm64/intel) packages as a GitHub release."
echo "  Android: build an AAB and upload it to the Play internal track"
echo "           (skipped unless the PUBLISH_ANDROID repo variable is true)."
echo "Watch: https://github.com/sproctor/warlock3/actions"
