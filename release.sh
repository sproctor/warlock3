#!/usr/bin/bash

echo "Releasing..."

while IFS='=' read -r key value
do
  key=$(echo "$key" | tr '.' '_')
  value=$(echo "$value" | tr -d '\n\r')
  eval "${key}"=\${value}
done < gradle.properties
VERSION=$warlock_version

echo "Version: ${VERSION}"

read -p "Press enter to continue"

if [[ $1 == "" ]]; then
  echo "Running tests"
  ./gradlew check
fi

#if [[ $1 == "" || $1 == "--android" ]]; then
#  echo "Android release"
#
#  ./gradlew publishBundle
#fi

if [[ $1 == "" ]]; then
  echo "Building desktop release"

  ./gradlew proguardReleaseJars
fi

if [[ $1 == "" || $1 == "--conveyor" ]]; then
  echo "Deploy with conveyor"

  conveyor --passphrase="$CONVEYOR_PASSPHRASE" make copied-site
fi

echo "Success"
