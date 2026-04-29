#!/usr/bin/bash

# Release pipeline: tag the current commit and push the tag.
# The GitHub Actions release workflow (.github/workflows/release.yaml) builds
# and publishes the multi-platform desktop packages on tag push.

set -e

echo "Releasing..."

while IFS='=' read -r key value
do
  key=$(echo "$key" | tr '.' '_')
  value=$(echo "$value" | tr -d '\n\r')
  eval "${key}"=\${value}
done < gradle.properties
VERSION=$warlock_version

echo "Version: ${VERSION}"

read -p "Press enter to tag v${VERSION} and push"

./gradlew --stop

if [[ $1 == "" ]]; then
  echo "Running tests"
  ./gradlew check
fi

git tag "v${VERSION}"
git push origin "v${VERSION}"

echo "Tag pushed. CI will build and publish the release at:"
echo "  https://github.com/sproctor/warlock3/actions"
